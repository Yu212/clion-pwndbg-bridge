package com.yu212.pwndbg.ui

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBTextField
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.KeyStroke

class CommandHistoryField(
    initialText: String = "",
    private val maxHistory: Int = 1000
): JBTextField(initialText) {
    private data class HistoryEntry(val index: Int, val text: String) {
        override fun toString(): String = text
    }

    private val history = ArrayList<String>(maxHistory)
    private var historyIndex = 0
    private var pendingText: String? = null

    init {
        historyIndex = history.size
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
        if (history.size > maxHistory) {
            history.removeAt(0)
        }
        resetNavigation()
    }

    fun showHistoryPopup() {
        if (history.isEmpty()) return
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
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                moveHistory(-1)
            }
        })
        actionMap.put("historyDown", object: AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                moveHistory(1)
            }
        })
        actionMap.put("historyPopup", object: AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
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
}
