package com.yu212.pwndbg.ui

import com.yu212.pwndbg.PwndbgService
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class PwndbgPanel(project: Project) : Disposable {
    private val consoleView: ConsoleView = ConsoleViewImpl(project, true)
    private val ansiDecoder = AnsiEscapeDecoder()
    private val inputField = JBTextField()
    private val sendButton = JButton("Send")
    private val rootPanel = BorderLayoutPanel()
    private var lastCommand: String? = null

    init {
        val inputPanel = JPanel(BorderLayout(8, 0))
        val buttonPanel = JPanel(GridLayout(1, 1, 6, 0))
        buttonPanel.add(sendButton)

        inputPanel.add(inputField, BorderLayout.CENTER)
        inputPanel.add(buttonPanel, BorderLayout.EAST)

        rootPanel.addToCenter(consoleView.component)
        rootPanel.addToBottom(inputPanel)

        val action = java.awt.event.ActionListener {
            val text = inputField.text.trim()
            val command = if (text.isEmpty()) lastCommand else text
            if (command.isNullOrEmpty()) return@ActionListener
            if (text.isNotEmpty()) {
                lastCommand = text
            }
            inputField.text = ""
            project.getService(PwndbgService::class.java).executeUserCommand(command)
        }

        sendButton.addActionListener(action)
        inputField.addActionListener(action)

    }

    val component: JComponent
        get() = rootPanel

    fun clearOutput() {
        consoleView.clear()
    }

    fun printCommand(command: String) {
        printOutput("> $command\n", isError = false)
    }

    fun printOutput(text: String, isError: Boolean) {
        val baseType = if (isError) ProcessOutputTypes.STDERR else ProcessOutputTypes.STDOUT
        ansiDecoder.escapeText(text, baseType) { chunk, attrs ->
            val type = ConsoleViewContentType.getConsoleViewType(attrs)
            consoleView.print(chunk, type)
        }
    }

    fun registerDisposable(parent: Disposable) {
        Disposer.register(parent, this)
    }

    override fun dispose() {
        consoleView.dispose()
    }
}
