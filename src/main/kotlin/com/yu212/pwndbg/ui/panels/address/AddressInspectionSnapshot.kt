package com.yu212.pwndbg.ui.panels.address

import com.yu212.pwndbg.ui.components.AnsiSegment

internal data class AddressInspectionSnapshot(
    val address: String,
    val xFormat: String,
    val telescopeLines: Int,
    val xinfoSegments: List<AnsiSegment>,
    val telescopeSegments: List<AnsiSegment>,
    val memorySegments: List<AnsiSegment>
)
