package com.yu212.pwndbg.ui.panels

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.components.BorderLayoutPanel
import com.yu212.pwndbg.PwndbgService
import com.yu212.pwndbg.ui.components.CommandHistoryField
import com.yu212.pwndbg.ui.components.PwndbgTabPanel
import java.awt.BorderLayout
import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.*

class CommandPanel(private val project: Project): PwndbgTabPanel {
    override val id: String = "command"
    override val title: String = "Command"
    override val supportsTextFontSize: Boolean = true

    private val consoleView = ConsoleViewImpl(project, true)
    private val ansiDecoder = AnsiEscapeDecoder()
    private val inputField = CommandHistoryField()
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
            val command = text.ifEmpty { lastCommand }
            if (command.isNullOrEmpty()) return@ActionListener
            if (text.isNotEmpty()) {
                lastCommand = text
                inputField.addHistory(text)
            }
            inputField.text = ""
            project.getService(PwndbgService::class.java).executeUserCommand(command)
        }

        sendButton.addActionListener(action)
        inputField.addActionListener(action)
        inputField.focusTraversalKeysEnabled = false
        val inputMap = inputField.getInputMap(JComponent.WHEN_FOCUSED)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "pwndbgComplete")
        inputField.actionMap.put("pwndbgComplete", object: AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                triggerCompletion()
            }
        })
    }

    override val component: JComponent
        get() = rootPanel

    override fun setTextFontSize(size: Int?) {
        val editor = consoleView.editor ?: return
        val scheme = (editor.colorsScheme.clone() as? EditorColorsScheme)
            ?: (EditorColorsManager.getInstance().globalScheme.clone() as? EditorColorsScheme)
            ?: return
        val baseSize = editor.colorsScheme.editorFontSize
        scheme.editorFontSize = size ?: baseSize
        (editor as? EditorEx)?.colorsScheme = scheme
    }

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

    override fun dispose() {
        consoleView.dispose()
    }

    private fun triggerCompletion() {
        val caret = inputField.caretPosition
        val text = inputField.text
        if (caret < 0 || caret > text.length) return
        val prefix = text.substring(0, caret)
        val command = "complete $prefix"
        project.getService(PwndbgService::class.java).executeCommandCapture(command) { result, error ->
            if (!error.isNullOrBlank()) {
                ApplicationManager.getApplication().invokeLater {
                    printOutput("Pwndbg completion failed: $error\n", isError = true)
                }
                return@executeCommandCapture
            }
            val completions = parseCompletions(result)
            if (completions.isEmpty()) return@executeCommandCapture
            ApplicationManager.getApplication().invokeLater {
                if (completions.size == 1) {
                    applyCompletion(completions.first())
                    return@invokeLater
                }
                val popup = JBPopupFactory.getInstance()
                        .createPopupChooserBuilder(completions)
                        .setTitle("Completion")
                        .setItemChosenCallback { selected ->
                            applyCompletion(selected)
                        }
                        .setRequestFocus(true)
                        .createPopup()
                popup.showUnderneathOf(inputField)
            }
        }
    }

    private fun applyCompletion(selected: String) {
        val caretNow = inputField.caretPosition
        val textNow = inputField.text
        if (caretNow < 0 || caretNow > textNow.length) return
        inputField.text = selected + textNow.substring(caretNow)
        inputField.caretPosition = selected.length
    }

    private fun parseCompletions(result: String?): List<String> {
        if (result.isNullOrBlank()) return emptyList()
        return result
                .split("\n")
                .map { it.removeSuffix("\r") }
                .filter { it.isNotBlank() }
                .filterNot { it.contains("List may be truncated, max-completions reached.") }
    }
}
