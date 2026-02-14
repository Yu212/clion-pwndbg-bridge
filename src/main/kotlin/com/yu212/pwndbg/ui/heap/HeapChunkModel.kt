package com.yu212.pwndbg.ui.heap

import com.intellij.openapi.util.TextRange

data class HeapChunkModel(
    val startAddress: ULong,
    val clickableRanges: List<TextRange>,
    val foldLineRange: IntRange?
)
