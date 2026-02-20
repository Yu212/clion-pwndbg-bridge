package com.yu212.pwnlion.app

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriver
import com.yu212.pwnlion.debug.CidrSessionBridge
import com.yu212.pwnlion.features.context.ContextHistoryManager
import com.yu212.pwnlion.settings.PwndbgSettingsService
import com.yu212.pwnlion.shared.AnsiSegment
import com.yu212.pwnlion.shared.AnsiSegment.Companion.decodeAnsi
import java.io.BufferedReader
import java.io.InputStreamReader

@Service(Service.Level.PROJECT)
class PwndbgService(private val project: Project): Disposable {
    data class RawOutput(
        val stdout: String?,
        val stderr: String?
    ) {
        val isError: Boolean
            get() = stdout.isNullOrBlank() || !stderr.isNullOrBlank()
    }

    data class CommandRequest(
        val command: String,
        val preprocess: (RawOutput) -> RawOutput = { it }
    )

    data class CommandCaptureResult(
        val command: String,
        val rawOutput: RawOutput,
        val segments: List<AnsiSegment>
    )

    private val log = Logger.getInstance(PwndbgService::class.java)
    private var initialized = false

    @Volatile
    private var currentBridge: CidrSessionBridge? = null

    @Volatile
    private var lastUnsupportedProcessClass: String? = null

    private val toolWindowManager: PwndbgToolWindowManager
        get() = project.getService(PwndbgToolWindowManager::class.java)

    private val settings: PwndbgSettingsService
        get() = ApplicationManager.getApplication().getService(PwndbgSettingsService::class.java)

    fun init() {
        if (initialized) return
        initialized = true

        val connection = project.messageBus.connect(this)
        connection.subscribe(XDebuggerManager.TOPIC, object: XDebuggerManagerListener {
            override fun processStarted(debugProcess: XDebugProcess) {
                val manager = toolWindowManager
                val commandPanel = manager.commandPanel
                startSocat()
                val bridge = createBridge(debugProcess) ?: run {
                    val className = debugProcess.javaClass.name
                    log.info("Pwndbg: Unsupported debug process: $className")
                    lastUnsupportedProcessClass = className
                    commandPanel?.printOutput("[pwndbg] Unsupported debug process: $className\n", isError = true)
                    return
                }
                lastUnsupportedProcessClass = null
                log.info("Pwndbg: debug session started: ${debugProcess.session.sessionName}")
                currentBridge?.dispose()
                currentBridge = bridge
                commandPanel?.clearOutput()
                project.getService(ContextHistoryManager::class.java).clearHistory()
                ApplicationManager.getApplication().invokeLater {
                    manager.showAllWindows()
                }
            }

            override fun processStopped(debugProcess: XDebugProcess) {
                if (!isSupportedProcess(debugProcess)) return
                log.info("Pwndbg: debug session stopped: ${debugProcess.session.sessionName}")
                currentBridge?.dispose()
                currentBridge = null
                toolWindowManager.removeTemporaryTabs()
                stopSocat()
                val breakpointsPanel = toolWindowManager.breakpointsPanel
                breakpointsPanel?.refreshAll()
            }
        })
    }

    private fun isSupportedProcess(debugProcess: XDebugProcess): Boolean {
        if (debugProcess !is CidrDebugProcess) return false
        return debugProcess.driverInTests is DapDriver
    }

    private fun createBridge(debugProcess: XDebugProcess): CidrSessionBridge? {
        if (debugProcess !is CidrDebugProcess) return null
        if (debugProcess.driverInTests !is DapDriver) return null
        return CidrSessionBridge(debugProcess)
    }

    fun executeUserCommand(command: String) {
        val bridge = currentBridge
        if (bridge == null) {
            val commandPanel = toolWindowManager.commandPanel
            val className = lastUnsupportedProcessClass
            if (className != null) {
                commandPanel?.printOutput("[pwndbg] Unsupported debug process: $className\n", isError = true)
            } else {
                commandPanel?.printOutput("[pwndbg] No active debug session.\n", isError = true)
            }
            return
        }
        bridge.runCommand(command)
    }

    fun executeCommandCapture(command: String, onResult: (String?, String?) -> Unit) {
        val bridge = currentBridge
        if (bridge == null) {
            onResult(null, "No active debug session.")
            return
        }
        bridge.runCommandCapture(command, onResult)
    }

    fun executeCommandCaptureDecoded(
        request: CommandRequest,
        onResult: (CommandCaptureResult) -> Unit
    ) {
        executeCommandCapture(request.command) { stdout, stderr ->
            val output = request.preprocess(RawOutput(stdout, stderr))
            onResult(
                CommandCaptureResult(
                    command = request.command,
                    rawOutput = output,
                    segments = decodeCommandOutput(output)
                )
            )
        }
    }

    fun decodeCommandOutput(rawOutput: RawOutput): List<AnsiSegment> {
        return if (rawOutput.stdout.isNullOrBlank() || !rawOutput.stderr.isNullOrBlank()) {
            decodeAnsi(rawOutput.stderr ?: "", true)
        } else {
            decodeAnsi(rawOutput.stdout, false)
        }
    }

    fun executeCommandsSequential(
        vararg requests: CommandRequest,
        onComplete: (List<CommandCaptureResult>) -> Unit
    ) {
        val results = mutableListOf<CommandCaptureResult>()

        fun runAt(index: Int) {
            if (index >= requests.size) {
                onComplete(results)
                return
            }
            val request = requests[index]
            executeCommandCaptureDecoded(request) { result ->
                results.add(result)
                runAt(index + 1)
            }
        }
        runAt(0)
    }

    override fun dispose() {
        currentBridge?.dispose()
        currentBridge = null
        stopSocat()
    }

    @Volatile
    private var socatProcess: Process? = null

    fun startSocat() {
        val commandPanel = toolWindowManager.commandPanel
        if (!settings.isSocatEnabled()) {
            commandPanel?.printOutput("[pwndbg] socat is disabled in settings.\n", isError = false)
            return
        }
        if (socatProcess?.isAlive == true) {
            commandPanel?.printOutput("[pwndbg] socat is already running.\n", isError = false)
            return
        }
        val tcpPort = settings.getSocatPort()
        val ttyPath = settings.getSocatTtyPath()
        val command = listOf(
            "socat",
            "-d",
            "-d",
            "pty,raw,echo=0,link=$ttyPath",
            "tcp-listen:$tcpPort,reuseaddr"
        )
        try {
            val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
            socatProcess = process
            commandPanel?.printOutput("[pwndbg] socat started on tcp:$tcpPort -> $ttyPath\n", isError = false)
            ApplicationManager.getApplication().executeOnPooledThread {
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        commandPanel?.printOutput("[socat] $line\n", isError = false)
                    }
                }
            }
            ApplicationManager.getApplication().executeOnPooledThread {
                val exitCode = process.waitFor()
                commandPanel?.printOutput("[pwndbg] socat exited with code $exitCode\n", isError = false)
            }
        } catch (e: Exception) {
            log.warn("Pwndbg: failed to start socat", e)
            commandPanel?.printOutput("[pwndbg] Failed to start socat: ${e.message}\n", isError = true)
        }
    }

    fun stopSocat() {
        val process = socatProcess ?: return
        if (process.isAlive) {
            process.destroy()
        }
        socatProcess = null
    }
}
