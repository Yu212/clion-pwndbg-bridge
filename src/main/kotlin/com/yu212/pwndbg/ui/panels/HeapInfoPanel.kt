package com.yu212.pwndbg.ui.panels

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.components.BorderLayoutPanel
import com.yu212.pwndbg.ui.CollapsibleSection
import com.yu212.pwndbg.ui.PwndbgTabPanel
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class HeapInfoPanel(project: Project): PwndbgTabPanel {
    override val id: String = "heap-info"
    override val title: String = "Heap Info"
    override val supportsTextFontSize: Boolean = true

    private val arenasView = CollapsibleSection("arenas", project)
    private val heapView = CollapsibleSection("heap", project)
    private val binsView = CollapsibleSection("bins", project)
    private val rootPanel = BorderLayoutPanel()
    private val outputPanel = JPanel()

    init {
        val toolbar = JPanel(BorderLayout(8, 0))
        toolbar.add(JLabel("Arenas / Heap / Bins"), BorderLayout.WEST)

        outputPanel.layout = BoxLayout(outputPanel, BoxLayout.Y_AXIS)
        outputPanel.add(arenasView.component)
        outputPanel.add(heapView.component)
        outputPanel.add(binsView.component)

        rootPanel.addToTop(toolbar)
        rootPanel.addToCenter(JBScrollPane(outputPanel))
    }

    override val component: JComponent
        get() = rootPanel

    override fun setTextFontSize(size: Int?) {
        arenasView.setTextFontSize(size)
        heapView.setTextFontSize(size)
        binsView.setTextFontSize(size)
        outputPanel.revalidate()
        outputPanel.repaint()
    }

    fun setArenasText(text: String, isError: Boolean) {
        arenasView.setText(text, isError)
        outputPanel.revalidate()
        outputPanel.repaint()
    }

    fun setHeapText(text: String, isError: Boolean) {
        heapView.setText(text, isError)
        outputPanel.revalidate()
        outputPanel.repaint()
    }

    fun setBinsText(text: String, isError: Boolean) {
        binsView.setText(text, isError)
        outputPanel.revalidate()
        outputPanel.repaint()
    }

    override fun dispose() {
        arenasView.dispose()
        heapView.dispose()
        binsView.dispose()
    }
}
