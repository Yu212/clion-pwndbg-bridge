package com.yu212.pwndbg.ui.heap

import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.ui.JBColor
import com.yu212.pwndbg.ui.ContextHistoryManager
import com.yu212.pwndbg.ui.components.AnsiTextViewer
import java.awt.Cursor
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.ScrollPaneConstants

class HeapAnsiTextViewer(
    private val project: Project,
    private val onChunkCtrlClick: (ULong) -> Unit = {},
): AnsiTextViewer(
    project,
    adjustHeight = false,
    verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
) {
    private var chunks: List<HeapChunkModel>? = null
    private val hoverHighlighters = mutableListOf<RangeHighlighter>()
    private var hoveredChunk: HeapChunkModel? = null
    private val handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    private val cursorOwner = HeapAnsiTextViewer::class.java
    private var handCursorApplied = false

    private val mouseMotionListener = object: EditorMouseMotionListener {
        override fun mouseMoved(event: EditorMouseEvent) {
            updateHoverState(event)
        }
    }

    private val mouseListener = object: EditorMouseListener {
        override fun mouseClicked(event: EditorMouseEvent) {
            handleCtrlClick(event)
        }
    }

    private val keyListener = object: KeyAdapter() {
        override fun keyReleased(e: KeyEvent) {
            if (e.keyCode == KeyEvent.VK_CONTROL) {
                clearHover()
            }
        }
    }

    init {
        editor.settings.apply {
            isLineMarkerAreaShown = true
            isFoldingOutlineShown = true
        }
        editor.addEditorMouseMotionListener(mouseMotionListener)
        editor.addEditorMouseListener(mouseListener)
        editor.contentComponent.addKeyListener(keyListener)
    }

    fun setHeapContent(
        segments: List<AnsiSegment>,
        chunks: List<HeapChunkModel>?,
        preserveView: Boolean = false,
        onUiUpdated: (() -> Unit)? = null
    ) {
        this.chunks = chunks
        setSegments(segments, preserveView, onUiUpdated)
    }

    override fun applySegments(segments: List<AnsiSegment>) {
        super.applySegments(segments)
        applyFoldRegions()
        clearHover()
    }

    override fun dispose() {
        editor.removeEditorMouseMotionListener(mouseMotionListener)
        editor.removeEditorMouseListener(mouseListener)
        editor.contentComponent.removeKeyListener(keyListener)
        clearHover()
        super.dispose()
    }

    private fun applyFoldRegions() {
        val foldingModel = editor.foldingModel
        foldingModel.runBatchFoldingOperation {
            foldingModel.allFoldRegions.forEach { foldingModel.removeFoldRegion(it) }
            if (chunks == null) return@runBatchFoldingOperation
            for (chunk in chunks) {
                val lineRange = chunk.foldLineRange ?: continue
                val startOffset = document.getLineStartOffset(lineRange.first)
                val endOffset = document.getLineEndOffset(lineRange.last)
                val length = lineRange.last - lineRange.first + 1
                val placeholder = "... 0x%x bytes / %d lines".format(length * 16, length)
                val region = foldingModel.addFoldRegion(startOffset, endOffset, placeholder)
                if (region != null) {
                    region.isExpanded = false
                }
            }
        }
    }

    private fun updateHoverState(event: EditorMouseEvent) {
        if (event.area != EditorMouseEventArea.EDITING_AREA || !event.mouseEvent.isControlDown) {
            clearHover()
            return
        }
        val history = project.getService(ContextHistoryManager::class.java)
        if (!history.atLatest()) {
            clearHover()
            return
        }
        val offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(event.mouseEvent.point))
        val chunk = findChunkByClickableOffset(offset)
        if (chunk == null) {
            clearHover()
            return
        }
        applyHoverForChunk(chunk)
    }

    private fun handleCtrlClick(event: EditorMouseEvent) {
        if (event.area != EditorMouseEventArea.EDITING_AREA || !event.mouseEvent.isControlDown || event.mouseEvent.button != MouseEvent.BUTTON1) return
        val history = project.getService(ContextHistoryManager::class.java)
        if (!history.atLatest()) return
        val offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(event.mouseEvent.point))
        val chunk = findChunkByClickableOffset(offset) ?: return
        onChunkCtrlClick(chunk.startAddress)
        event.mouseEvent.consume()
    }

    private fun findChunkByClickableOffset(offset: Int): HeapChunkModel? {
        return chunks?.firstOrNull { chunk ->
            chunk.clickableRanges.any { range -> range.containsOffset(offset) }
        }
    }

    private fun applyHoverForChunk(chunk: HeapChunkModel) {
        if (hoveredChunk == chunk) {
            applyHandCursor()
            return
        }
        clearHover()
        hoveredChunk = chunk
        val attributes = TextAttributes().apply {
            foregroundColor = JBColor.BLUE
            effectColor = JBColor.BLUE
            effectType = EffectType.LINE_UNDERSCORE
        }
        for (range in chunk.clickableRanges) {
            addHighlight(range, attributes)
        }
        applyHandCursor()
    }

    private fun addHighlight(range: TextRange, attributes: TextAttributes) {
        val start = range.startOffset
        val end = range.endOffset
        hoverHighlighters.add(
            editor.markupModel.addRangeHighlighter(
                start,
                end,
                HighlighterLayer.SELECTION - 1,
                attributes,
                HighlighterTargetArea.EXACT_RANGE
            )
        )
    }

    private fun clearHover() {
        hoveredChunk = null
        hoverHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
        hoverHighlighters.clear()
        clearCustomCursor()
    }

    private fun applyHandCursor() {
        if (handCursorApplied) return
        (editor as? EditorEx)?.setCustomCursor(cursorOwner, handCursor)
        handCursorApplied = true
    }

    private fun clearCustomCursor() {
        if (!handCursorApplied) return
        (editor as? EditorEx)?.setCustomCursor(cursorOwner, null)
        handCursorApplied = false
    }
}
