package com.yu212.pwndbg.features.address

internal data class AddressInspectionTabState(
    val address: String,
    val xFormat: String = "16gx",
    val telescopeLines: Int = 8,
    val fixedContextIndex: Int? = null
) {
    val isFixed: Boolean
        get() = fixedContextIndex != null
}
