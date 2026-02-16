package com.yu212.pwndbg.shared

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key

data class AnsiSegment(
    val text: String,
    val attributes: Key<*>
) {
    companion object {
        fun decodeAnsi(text: String, isError: Boolean): List<AnsiSegment> {
            val baseType = if (isError) ProcessOutputTypes.STDERR else ProcessOutputTypes.STDOUT
            val decoder = AnsiEscapeDecoder()
            val segments = ArrayList<AnsiSegment>()
            decoder.escapeText(text, baseType) { chunk, attrs ->
                segments.add(AnsiSegment(chunk, attrs))
            }
            return segments
        }

        fun splitByLines(segments: List<AnsiSegment>): List<List<AnsiSegment>> {
            val lines = mutableListOf<MutableList<AnsiSegment>>()
            var current = mutableListOf<AnsiSegment>()
            for (segment in segments) {
                val parts = segment.text.split('\n')
                parts.forEachIndexed { index, part ->
                    if (part.isNotEmpty()) {
                        current.add(AnsiSegment(part, segment.attributes))
                    }
                    if (index < parts.lastIndex) {
                        lines.add(current)
                        current = mutableListOf()
                    }
                }
            }
            lines.add(current)
            return lines
        }

        fun flattenLines(lines: List<List<AnsiSegment>>): List<AnsiSegment> {
            val flattened = mutableListOf<AnsiSegment>()
            lines.forEachIndexed { index, line ->
                flattened.addAll(line)
                if (index < lines.lastIndex) {
                    flattened.add(AnsiSegment("\n", ProcessOutputTypes.STDOUT))
                }
            }
            return flattened
        }
    }
}
