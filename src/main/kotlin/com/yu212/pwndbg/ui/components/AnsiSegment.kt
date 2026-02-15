package com.yu212.pwndbg.ui.components

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
    }
}
