package com.yu212.pwndbg.ui.panels

import com.intellij.openapi.project.Project
import com.yu212.pwndbg.ui.HeapAnsiTextViewer
import com.yu212.pwndbg.ui.PwndbgTabPanel
import javax.swing.JComponent

class HeapPanel(project: Project): PwndbgTabPanel {
    override val id: String = "heap"
    override val title: String = "Heap"
    override val supportsTextFontSize: Boolean = true

    private val viewer = HeapAnsiTextViewer(project)
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
        val heapWarn = "\u001b[33mpwndbg will try to resolve the heap symbols via heuristic now since we cannot resolve the heap via the debug symbols.\nThis might not work in all cases. Use `help set resolve-heap-via-heuristic` for more details.\n\u001b[0m\n"
        val strippedText = text.removePrefix(heapWarn)
        if (strippedText == lastText) return
        lastText = strippedText
        viewer.setText(strippedText, isError, preserveView = true)
    }
}
