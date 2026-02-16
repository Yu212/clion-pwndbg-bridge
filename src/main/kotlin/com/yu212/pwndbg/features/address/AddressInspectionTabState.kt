package com.yu212.pwndbg.features.address

import com.yu212.pwndbg.settings.PwndbgSettingsService

internal data class AddressInspectionTabState(
    val address: String,
    val xFormat: String,
    val telescopeCount: Int,
    val fixedContextIndex: Int? = null
) {
    val isFixed: Boolean
        get() = fixedContextIndex != null
}
