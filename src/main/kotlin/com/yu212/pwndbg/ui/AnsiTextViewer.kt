package com.yu212.pwndbg.ui

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.project.Project
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

class AnsiTextViewer(
    project: Project,
    private val adjustHeight: Boolean = false,
    private val verticalScrollBarPolicy: Int = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
): Disposable {
    private val document = EditorFactory.getInstance().createDocument("")
    val editor: Editor = EditorFactory.getInstance().createViewer(document, project)
    val component: JComponent = editor.component
    private val ansiDecoder = AnsiEscapeDecoder()
    private val baseScheme = editor.colorsScheme
    private var fontSizeOverride: Int? = null

    init {
        val ex = editor as? EditorEx
        if (ex != null) {
            ex.scrollPane.verticalScrollBarPolicy = verticalScrollBarPolicy
            ex.scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }
        editor.settings.apply {
            isLineNumbersShown = false
            isLineMarkerAreaShown = false
            isFoldingOutlineShown = false
            isRightMarginShown = false
            isCaretRowShown = false
            isUseSoftWraps = false
        }
    }

    fun setText(text: String, isError: Boolean, preserveView: Boolean = false, onUiUpdated: (() -> Unit)? = null) {
        val segments = decodeAnsi(text, isError)
        val app = ApplicationManager.getApplication()
        app.invokeLater {
            val scrollOffset = if (preserveView) editor.scrollingModel.verticalScrollOffset else 0
            val caretOffset = if (preserveView) editor.caretModel.offset else 0
            app.runWriteAction {
                applySegments(segments)
            }
            if (preserveView) {
                val clamped = caretOffset.coerceAtMost(document.textLength)
                editor.caretModel.moveToOffset(clamped)
                editor.scrollingModel.scrollVertically(scrollOffset)
            }
            updatePreferredHeight()
            onUiUpdated?.invoke()
        }
    }

    fun setFontSize(size: Int?) {
        fontSizeOverride = size
        val scheme = (baseScheme.clone() as? EditorColorsScheme)
            ?: (EditorColorsManager.getInstance().globalScheme.clone() as? EditorColorsScheme)
            ?: return
        val baseSize = baseScheme.editorFontSize
        scheme.editorFontSize = size ?: baseSize
        (editor as? EditorEx)?.colorsScheme = scheme
        updatePreferredHeight()
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
    }

    private fun decodeAnsi(text: String, isError: Boolean): List<Pair<String, com.intellij.openapi.util.Key<*>>> {
        val baseType = if (isError) ProcessOutputTypes.STDERR else ProcessOutputTypes.STDOUT
        val segments = ArrayList<Pair<String, com.intellij.openapi.util.Key<*>>>()
        ansiDecoder.escapeText(text, baseType) { chunk, attrs ->
            if (chunk.isNotEmpty()) {
                segments.add(chunk to attrs)
            }
        }
        return segments
    }

    private fun applySegments(segments: List<Pair<String, com.intellij.openapi.util.Key<*>>>) {
        document.setText(segments.joinToString(separator = "") { it.first })
        editor.markupModel.removeAllHighlighters()

        var offset = 0
        for ((chunk, attrs) in segments) {
            val start = offset
            offset += chunk.length
            val type = ConsoleViewContentType.getConsoleViewType(attrs)
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

    private fun updatePreferredHeight() {
        if (!adjustHeight) return
        val preferredHeight = document.lineCount * editor.lineHeight + 24
        val width = component.preferredSize.width
        val size = Dimension(width, preferredHeight)
        component.preferredSize = size
        component.minimumSize = size
        component.maximumSize = Dimension(Int.MAX_VALUE, preferredHeight)
    }
}
