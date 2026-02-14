package com.yu212.pwndbg.ui.heap

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.util.TextRange
import com.yu212.pwndbg.ui.components.AnsiTextViewer
import java.awt.Color

object HeapChunkParser {
    private fun calcFoldLineRange(startQword: Int, endQword: Int): IntRange? {
        val startLine = startQword / 2 + 1
        val endLine = endQword / 2 - 1
        return if (endLine - startLine >= 3) {
            startLine + 1..<endLine
        } else {
            null
        }
    }

    fun parse(segments: List<AnsiTextViewer.AnsiSegment>): List<HeapChunkModel> {
        val chunks = mutableListOf<HeapChunkModel>()
        var startQword = 0
        var qword = 0
        var lastColor: Color? = null
        var textOffset = 0
        var startAddress = 0UL
        var currentAddress = 0UL
        var clickableRanges = mutableListOf<TextRange>()
        val addressRegex = Regex("\t0x[0-9a-f]{16}")
        for ((chunk, attrs) in segments) {
            val type = ConsoleViewContentType.getConsoleViewType(attrs)
            val color = type.attributes.foregroundColor
            if (type == ConsoleViewContentType.NORMAL_OUTPUT) {
                if (lastColor == null) {
                    currentAddress = chunk.removePrefix("0x").toULong(16)
                    startAddress = currentAddress
                }
            } else if (addressRegex.matches(chunk)) {
                if (lastColor != color && lastColor != null) {
                    chunks.add(
                        HeapChunkModel(
                            startAddress = startAddress,
                            clickableRanges = clickableRanges,
                            foldLineRange = calcFoldLineRange(startQword, qword)
                        )
                    )
                    clickableRanges = mutableListOf()
                    startAddress = currentAddress
                    startQword = qword
                }
                qword++
                clickableRanges.add(TextRange(textOffset + 1, textOffset + chunk.length))
                currentAddress += 8U
                lastColor = color
            } else {
                clickableRanges.add(TextRange(textOffset, textOffset + chunk.length))
            }
            textOffset += chunk.length
        }
        chunks.add(
            HeapChunkModel(
                startAddress = startAddress,
                clickableRanges = clickableRanges,
                foldLineRange = calcFoldLineRange(startQword, qword)
            )
        )
        return chunks
    }
}
