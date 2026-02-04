package com.yu212.pwndbg

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriver
import com.yu212.pwndbg.debug.CidrSessionBridge
import com.yu212.pwndbg.ui.PwndbgBreakpointsPanel
import com.yu212.pwndbg.ui.PwndbgContextPanel
import com.yu212.pwndbg.ui.PwndbgMapsPanel
import com.yu212.pwndbg.ui.PwndbgPanel
import java.io.BufferedReader
import java.io.InputStreamReader

@Service(Service.Level.PROJECT)
class PwndbgService(private val project: Project) : Disposable {
    private val log = Logger.getInstance(PwndbgService::class.java)
    private var initialized = false

    @Volatile
    private var currentBridge: CidrSessionBridge? = null

    @Volatile
    private var panel: PwndbgPanel? = null

    @Volatile
    private var contextPanel: PwndbgContextPanel? = null

    @Volatile
    private var mapsPanel: PwndbgMapsPanel? = null

    @Volatile
    private var breakpointsPanel: PwndbgBreakpointsPanel? = null

    @Volatile
    private var lastUnsupportedProcessClass: String? = null

    companion object {
        private const val DEFAULT_TTY_PATH = "/tmp/ttyPWN"
        private const val DEFAULT_TCP_PORT = 0xdead
    }

    fun init() {
        if (initialized) return
        initialized = true

        val connection = project.messageBus.connect(this)
        connection.subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
            override fun processStarted(debugProcess: XDebugProcess) {
                startSocat()
                val bridge = createBridge(debugProcess) ?: run {
                    val className = debugProcess.javaClass.name
                    log.info("Pwndbg: Unsupported debug process: $className")
                    lastUnsupportedProcessClass = className
                    panel?.printOutput("[pwndbg] Unsupported debug process: $className\n", isError = true)
                    return
                }
                lastUnsupportedProcessClass = null
                log.info("Pwndbg: debug session started: ${debugProcess.session.sessionName}")
                currentBridge?.dispose()
                currentBridge = bridge
                panel?.clearOutput()
                contextPanel?.clearOutput()
                panel?.let { bridge.attachPanel(it) }
                ApplicationManager.getApplication().invokeLater {
                    ToolWindowManager.getInstance(project).getToolWindow("Pwndbg")?.show()
                }
                mapsPanel?.refreshAll()
                breakpointsPanel?.refreshAll()
            }

            override fun processStopped(debugProcess: XDebugProcess) {
                if (!isSupportedProcess(debugProcess)) return
                log.info("Pwndbg: debug session stopped: ${debugProcess.session.sessionName}")
                currentBridge?.dispose()
                currentBridge = null
                stopSocat()
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
        return CidrSessionBridge(
            xDebugSession = debugProcess.session,
            debugProcess = debugProcess,
            panelProvider = { panel },
            contextPanelProvider = { contextPanel }
        )
    }

    fun executeUserCommand(command: String) {
        val bridge = currentBridge
        if (bridge == null) {
            val className = lastUnsupportedProcessClass
            if (className != null) {
                panel?.printOutput("[pwndbg] Unsupported debug process: $className\n", isError = true)
            } else {
                panel?.printOutput("[pwndbg] No active debug session.\n", isError = true)
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

    fun attachPanels(panel: PwndbgPanel, contextPanel: PwndbgContextPanel, mapsPanel: PwndbgMapsPanel) {
        this.panel = panel
        this.contextPanel = contextPanel
        this.mapsPanel = mapsPanel
        currentBridge?.attachPanel(panel)
    }

    fun attachBreakpointsPanel(panel: PwndbgBreakpointsPanel) {
        this.breakpointsPanel = panel
    }

    override fun dispose() {
        currentBridge?.dispose()
        currentBridge = null
        panel = null
        contextPanel = null
        mapsPanel = null
        breakpointsPanel = null
        stopSocat()
    }

    @Volatile
    private var socatProcess: Process? = null

    fun startSocat() {
        if (socatProcess?.isAlive == true) {
            panel?.printOutput("[pwndbg] socat is already running.\n", isError = false)
            return
        }
        val command = listOf(
            "socat",
            "-d",
            "-d",
            "pty,raw,echo=0,link=$DEFAULT_TTY_PATH",
            "tcp-listen:$DEFAULT_TCP_PORT,reuseaddr"
        )
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            socatProcess = process
            panel?.printOutput("[pwndbg] socat started on tcp:$DEFAULT_TCP_PORT -> $DEFAULT_TTY_PATH\n", isError = false)
            ApplicationManager.getApplication().executeOnPooledThread {
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        panel?.printOutput("[socat] $line\n", isError = false)
                    }
                }
            }
            ApplicationManager.getApplication().executeOnPooledThread {
                val exitCode = process.waitFor()
                panel?.printOutput("[pwndbg] socat exited with code $exitCode\n", isError = false)
            }
        } catch (e: Exception) {
            log.warn("Pwndbg: failed to start socat", e)
            panel?.printOutput("[pwndbg] Failed to start socat: ${e.message}\n", isError = true)
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
