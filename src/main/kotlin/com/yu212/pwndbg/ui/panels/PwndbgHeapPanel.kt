package com.yu212.pwndbg.ui.panels

import com.intellij.openapi.project.Project
import com.yu212.pwndbg.ui.AnsiTextViewer
import com.yu212.pwndbg.ui.PwndbgTabPanel
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

class PwndbgHeapPanel(project: Project): PwndbgTabPanel {
    override val id: String = "heap"
    override val title: String = "Heap"
    override val supportsTextFontSize: Boolean = true

    private val viewer = AnsiTextViewer(
        project,
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
    )
    private val rootPanel = viewer.component
    private var lastText: String? = null

    override val component: JComponent
        get() = rootPanel

    override fun setTextFontSize(size: Int?) {
        viewer.setFontSize(size)
        rootPanel.revalidate()
        rootPanel.repaint()
    }

    override fun dispose() {
        viewer.dispose()
    }

    fun setHeapText(text: String, isError: Boolean) {
        if (text == lastText) return
        lastText = text
        viewer.setText(text, isError, preserveView = true)
    }
}
