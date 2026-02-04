package com.yu212.pwndbg.debug

import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.util.TextRange
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriver
import com.yu212.pwndbg.ui.PwndbgContextPanel
import com.yu212.pwndbg.ui.PwndbgPanel
import org.eclipse.lsp4j.debug.EvaluateArguments

class CidrSessionBridge(
    private val xDebugSession: XDebugSession,
    private val debugProcess: CidrDebugProcess,
    private val panelProvider: () -> PwndbgPanel?,
    private val contextPanelProvider: () -> PwndbgContextPanel?
) : Disposable {
    private val log = Logger.getInstance(CidrSessionBridge::class.java)

    private val outputListener = object : ProcessAdapter() {
        override fun onTextAvailable(event: ProcessEvent, outputType: com.intellij.openapi.util.Key<*>) {
            val panel = panelProvider() ?: return
            val isError = outputType === ProcessOutputTypes.STDERR || outputType === ProcessOutputTypes.SYSTEM
            ApplicationManager.getApplication().invokeLater {
                panel.printOutput(event.text, isError)
            }
        }
    }

    private var consoleOffset = 0
    private val consoleListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            val panel = panelProvider() ?: return
            val doc = event.document
            val length = doc.textLength
            if (length <= consoleOffset) return
            val text = doc.getText(TextRange(consoleOffset, length))
            consoleOffset = length
            if (text.isNotEmpty()) {
                ApplicationManager.getApplication().invokeLater {
                    panel.printOutput(text, isError = false)
                }
            }
        }
    }

    private val sessionListener = object : XDebugSessionListener {
        override fun sessionPaused() {
            log.debug("Pwndbg: session paused, refreshing context.")
            runCommandCapture("context") { result, error ->
                if (!error.isNullOrBlank()) {
                    panelProvider()?.let { panel ->
                        ApplicationManager.getApplication().invokeLater {
                            panel.printOutput("Pwndbg command failed: $error\n", isError = true)
                        }
                    }
                    return@runCommandCapture
                }
                if (!result.isNullOrBlank()) {
                    contextPanelProvider()?.let { targetPanel ->
                        ApplicationManager.getApplication().invokeLater {
                            targetPanel.pushContextOutput(result + "\n", isError = false)
                        }
                    }
                }
            }
        }
    }

    init {
        xDebugSession.addSessionListener(sessionListener, this)
        debugProcess.processHandler.addProcessListener(outputListener, this)
        val consoleView = debugProcess.debuggerConsole
        if (consoleView is LanguageConsoleView) {
            val doc = consoleView.editorDocument
            doc.addDocumentListener(consoleListener)
            consoleOffset = doc.textLength
        }
    }

    fun attachPanel(panel: PwndbgPanel) {
        ApplicationManager.getApplication().invokeLater {
            panel.printOutput("[pwndbg] attached to debug session: ${xDebugSession.sessionName}\n", isError = false)
        }
    }

    fun runCommand(command: String) {
        val trimmed = command.trim()

        val panel = panelProvider()
        if (panel != null) {
            ApplicationManager.getApplication().invokeLater {
                panel.printCommand(command)
            }
        }
        if (trimmed == "exit" || trimmed == "quit") {
            ApplicationManager.getApplication().invokeLater {
                panelProvider()?.printOutput("[pwndbg] Exiting debug session...\n", isError = false)
                xDebugSession.stop()
            }
            return
        }
        val driver = debugProcess.driverInTests
        if (driver !is DapDriver) {
            log.warn("Pwndbg: DAP driver is not available for this debug session.")
            panelProvider()?.let { panel ->
                ApplicationManager.getApplication().invokeLater {
                    panel.printOutput("[pwndbg] This plugin requires DAP (Pwndbg GDB (DAP)).\n", isError = true)
                }
            }
            return
        }

        val args = EvaluateArguments().apply {
            expression = command
            context = "repl"
        }
        val future = driver.server.evaluate(args)
        future.whenComplete { response, err ->
            if (err != null) {
                log.warn("Pwndbg DAP evaluate failed", err)
                panelProvider()?.let { panel ->
                    ApplicationManager.getApplication().invokeLater {
                        panel.printOutput("Pwndbg command failed: ${err.message}\n", isError = true)
                    }
                }
                return@whenComplete
            }

            val result = response?.result
            if (!result.isNullOrBlank()) {
                panelProvider()?.let { targetPanel ->
                    ApplicationManager.getApplication().invokeLater {
                        targetPanel.printOutput(result + "\n", isError = false)
                    }
                }
            }
        }
    }

    fun runCommandCapture(command: String, onResult: (String?, String?) -> Unit) {
        val driver = debugProcess.driverInTests
        if (driver !is DapDriver) {
            onResult(null, "DAP driver is not available for this debug session.")
            return
        }

        val args = EvaluateArguments().apply {
            expression = command
            context = "repl"
        }
        val future = driver.server.evaluate(args)
        future.whenComplete { response, err ->
            if (err != null) {
                log.warn("Pwndbg DAP evaluate failed", err)
                onResult(null, err.message ?: "Pwndbg command failed.")
                return@whenComplete
            }
            onResult(response?.result, null)
        }
    }

    override fun dispose() {
        // listeners are disposed with this Disposable.
    }
}
