package com.yu212.pwndbg.ui

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel

class PwndbgContextPanel(project: com.intellij.openapi.project.Project) : Disposable {
    private data class ContextEntry(
        val text: String,
        val isError: Boolean
    )

    private val document = EditorFactory.getInstance().createDocument("")
    private val editor = EditorFactory.getInstance().createViewer(document, project)
    private val ansiDecoder = AnsiEscapeDecoder()
    private var lastText: String? = null
    private val history = ArrayList<ContextEntry>()
    private val maxHistory = 100
    private var historyIndex = -1

    private val prevButton = JButton("<")
    private val nextButton = JButton(">")
    private val latestButton = JButton(">>")
    private val statusLabel = JLabel("Latest")
    private val rootPanel = BorderLayoutPanel()

    val component: JComponent
        get() = rootPanel

    fun clearOutput() {
        history.clear()
        historyIndex = -1
        updateNavigationState()
        setContextOutput("", isError = false)
    }

    fun setContextOutput(text: String, isError: Boolean) {
        if (text == lastText) return
        lastText = text

        val baseType = if (isError) ProcessOutputTypes.STDERR else ProcessOutputTypes.STDOUT
        val segments = ArrayList<Pair<String, com.intellij.openapi.util.Key<*>>>()
        ansiDecoder.escapeText(text, baseType) { chunk, attrs ->
            if (chunk.isNotEmpty()) {
                segments.add(chunk to attrs)
            }
        }

        ApplicationManager.getApplication().invokeLater {
            val scrollOffset = editor.scrollingModel.verticalScrollOffset
            val caretOffset = editor.caretModel.offset

            ApplicationManager.getApplication().runWriteAction {
                document.setText(segments.joinToString(separator = "") { it.first })
                editor.markupModel.removeAllHighlighters()

                var offset = 0
                for ((chunk, attrs) in segments) {
                    val start = offset
                    offset += chunk.length
                    val type = ConsoleViewContentType.getConsoleViewType(attrs)
                        ?: if (isError) ConsoleViewContentType.ERROR_OUTPUT else ConsoleViewContentType.NORMAL_OUTPUT
                    val attributes = type.attributes ?: continue
                    editor.markupModel.addRangeHighlighter(
                        start,
                        offset,
                        HighlighterLayer.SYNTAX,
                        attributes,
                        HighlighterTargetArea.EXACT_RANGE
                    )
                }
            }

            val clampedCaret = caretOffset.coerceAtMost(document.textLength)
            editor.caretModel.moveToOffset(clampedCaret)
            editor.scrollingModel.scrollVertically(scrollOffset)
        }
    }

    fun pushContextOutput(text: String, isError: Boolean) {
        val entry = ContextEntry(text, isError)
        val lastEntry = history.lastOrNull()
        if (lastEntry == entry) {
            historyIndex = history.lastIndex
            updateNavigationState()
            setContextOutput(text, isError)
            return
        }

        if (history.size >= maxHistory) {
            history.removeAt(0)
            if (historyIndex > 0) {
                historyIndex -= 1
            }
        }
        history.add(entry)
        historyIndex = history.lastIndex
        updateNavigationState()
        setContextOutput(text, isError)
    }

    private fun navigateTo(index: Int) {
        if (index < 0 || index >= history.size) return
        historyIndex = index
        val entry = history[historyIndex]
        updateNavigationState()
        setContextOutput(entry.text, entry.isError)
    }

    private fun updateNavigationState() {
        val latestIndex = history.lastIndex
        val hasHistory = history.isNotEmpty()
        prevButton.isEnabled = hasHistory && historyIndex > 0
        nextButton.isEnabled = hasHistory && historyIndex < latestIndex
        latestButton.isEnabled = hasHistory && historyIndex in 0 until latestIndex

        val behind = if (hasHistory && historyIndex >= 0) latestIndex - historyIndex else 0
        statusLabel.text = if (behind == 0) "Latest" else "$behind behind"
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
    }

    init {
        val toolbar = BorderLayoutPanel()
        val buttons = BorderLayoutPanel()
        buttons.addToLeft(prevButton)
        buttons.addToCenter(nextButton)
        buttons.addToRight(latestButton)
        toolbar.addToLeft(buttons)
        toolbar.addToRight(statusLabel)

        rootPanel.layout = BorderLayout()
        rootPanel.add(toolbar, BorderLayout.NORTH)
        rootPanel.add(editor.component, BorderLayout.CENTER)

        prevButton.addActionListener { navigateTo(historyIndex - 1) }
        nextButton.addActionListener { navigateTo(historyIndex + 1) }
        latestButton.addActionListener { navigateTo(history.lastIndex) }
        updateNavigationState()

        editor.settings.apply {
            isLineNumbersShown = false
            isLineMarkerAreaShown = false
            isFoldingOutlineShown = false
            isRightMarginShown = false
            isCaretRowShown = false
        }
    }
}
