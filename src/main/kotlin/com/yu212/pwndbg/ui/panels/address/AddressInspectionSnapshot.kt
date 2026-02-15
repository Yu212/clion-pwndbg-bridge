package com.yu212.pwndbg.ui.panels.address

import com.yu212.pwndbg.ui.components.AnsiTextViewer

internal data class AddressInspectionSnapshot(
    val address: String,
    val xFormat: String,
    val telescopeLines: Int,
    val xinfoSegments: List<AnsiTextViewer.AnsiSegment>,
    val telescopeSegments: List<AnsiTextViewer.AnsiSegment>,
    val memorySegments: List<AnsiTextViewer.AnsiSegment>
)
