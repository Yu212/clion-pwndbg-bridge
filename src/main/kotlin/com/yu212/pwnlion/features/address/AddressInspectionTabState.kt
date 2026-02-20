package com.yu212.pwnlion.features.address

internal data class AddressInspectionTabState(
    val address: String,
    val xFormat: String,
    val telescopeCount: Int,
    val fixedContextIndex: Int? = null
) {
    val isFixed: Boolean
        get() = fixedContextIndex != null
}
