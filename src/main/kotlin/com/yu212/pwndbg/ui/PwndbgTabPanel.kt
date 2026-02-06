package com.yu212.pwndbg.ui

import com.intellij.openapi.Disposable
import javax.swing.JComponent

interface PwndbgTabPanel : Disposable {
    val id: String
    val title: String
    val component: JComponent
    val supportsTextFontSize: Boolean
        get() = false

    fun setTextFontSize(size: Int?) {
    }
}
