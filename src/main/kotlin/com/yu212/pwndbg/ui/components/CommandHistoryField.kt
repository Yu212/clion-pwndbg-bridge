package com.yu212.pwndbg.ui.components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBTextField
import com.yu212.pwndbg.settings.PwndbgSettingsService
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.KeyStroke

class CommandHistoryField(
    initialText: String = "",
): JBTextField(initialText) {
    private data class HistoryEntry(val index: Int, val text: String) {
        override fun toString(): String = text
    }

    private val history = mutableListOf<String>()
    private var historyIndex = 0
    private var pendingText: String? = null

    init {
        installHistoryBindings()
    }

    fun addHistory(entry: String) {
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) return
        if (history.lastOrNull() == trimmed) {
            resetNavigation()
            return
        }
        history.add(trimmed)
        trimToMax()
        resetNavigation()
    }

    fun showHistoryPopup() {
        if (history.isEmpty()) return
        trimToMax()
        val items = history.indices.reversed().map { HistoryEntry(it, history[it]) }
        val popup = JBPopupFactory.getInstance()
                .createPopupChooserBuilder(items)
                .setTitle("History")
                .setItemChosenCallback { selected ->
                    historyIndex = selected.index
                    pendingText = null
                    text = selected.text
                    caretPosition = text.length
                }
                .setRequestFocus(true)
                .createPopup()
        popup.showUnderneathOf(this)
    }

    private fun installHistoryBindings() {
        val inputMap = getInputMap(WHEN_FOCUSED)
        val actionMap = actionMap

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "historyUp")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "historyDown")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), "historyPopup")

        actionMap.put("historyUp", object: AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                moveHistory(-1)
            }
        })
        actionMap.put("historyDown", object: AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                moveHistory(1)
            }
        })
        actionMap.put("historyPopup", object: AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                showHistoryPopup()
            }
        })
    }

    private fun moveHistory(delta: Int) {
        if (history.isEmpty()) return
        if (historyIndex == history.size) {
            pendingText = text
        }
        val newIndex = (historyIndex + delta).coerceIn(0, history.size)
        historyIndex = newIndex
        text = if (historyIndex == history.size) {
            pendingText ?: ""
        } else {
            history[historyIndex]
        }
        caretPosition = text.length
    }

    private fun resetNavigation() {
        historyIndex = history.size
        pendingText = null
    }

    private fun trimToMax() {
        val maxHistory = ApplicationManager.getApplication()
                .getService(PwndbgSettingsService::class.java)
                .getContextHistoryMax()
        while (history.size > maxHistory) {
            history.removeAt(0)
        }
    }
}
