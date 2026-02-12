package com.yu212.pwndbg.ui

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.awt.Color
import javax.swing.ScrollPaneConstants

class HeapAnsiTextViewer(
    project: Project,
): AnsiTextViewer(
    project,
    adjustHeight = false,
    verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
) {
    init {
        editor.settings.apply {
            isLineMarkerAreaShown = true
            isFoldingOutlineShown = true
        }
    }

    override fun applySegments(segments: List<Pair<String, Key<*>>>) {
        super.applySegments(segments)
        val lineCount = document.lineCount
        val styles = Array(lineCount) { mutableSetOf<Color>() }
        var lineIndex = 0
        for ((chunk, attrs) in segments) {
            val type = ConsoleViewContentType.getConsoleViewType(attrs)
            val attributes = type.attributes ?: continue
            if (chunk.contains('\n')) {
                lineIndex++
            }
            styles[lineIndex].add(attributes.foregroundColor)
        }
        val foldingModel = editor.foldingModel
        foldingModel.runBatchFoldingOperation {
            foldingModel.allFoldRegions.forEach { foldingModel.removeFoldRegion(it) }
            var runStart = 0
            while (runStart < lineCount) {
                var runEnd = runStart
                while (runEnd < lineCount && styles[runEnd] == styles[runStart]) {
                    runEnd += 1
                }
                val runLength = runEnd - runStart
                if (runLength >= 5) {
                    val startOffset = document.getLineStartOffset(runStart + 1)
                    val endOffset = document.getLineEndOffset(runEnd - 2)
                    val placeholder = "0x%x bytes / %d lines".format((runLength - 2) * 16, runLength - 2)
                    val region = foldingModel.addFoldRegion(startOffset, endOffset, placeholder)
                    if (region != null) {
                        region.isExpanded = false
                    }
                }
                runStart = runEnd
            }
        }
    }
}
