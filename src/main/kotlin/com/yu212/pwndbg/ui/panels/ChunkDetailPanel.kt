package com.yu212.pwndbg.ui.panels

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.components.BorderLayoutPanel
import com.yu212.pwndbg.ui.components.CollapsibleSection
import com.yu212.pwndbg.ui.components.PwndbgTabPanel
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ChunkDetailPanel(project: Project): PwndbgTabPanel {
    override val id: String = "chunk-detail"
    override val title: String = "Chunk Detail"
    override val supportsTextFontSize: Boolean = true

    private val headerLabel = JLabel("Chunk: -")
    private val hiView = CollapsibleSection("hi -v", project)
    private val tryFreeView = CollapsibleSection("try-free", project)
    private val outputPanel = JPanel()
    private val rootPanel = BorderLayoutPanel()

    init {
        val toolbar = JPanel(BorderLayout())
        toolbar.add(headerLabel, BorderLayout.WEST)

        outputPanel.layout = BoxLayout(outputPanel, BoxLayout.Y_AXIS)
        outputPanel.add(hiView.component)
        outputPanel.add(tryFreeView.component)

        rootPanel.addToTop(toolbar)
        rootPanel.addToCenter(JBScrollPane(outputPanel))
    }

    override val component: JComponent
        get() = rootPanel

    override fun setTextFontSize(size: Int?) {
        hiView.setTextFontSize(size)
        tryFreeView.setTextFontSize(size)
        outputPanel.revalidate()
        outputPanel.repaint()
    }

    fun setChunkResult(
        address: ULong,
        hiOutput: String,
        hiIsError: Boolean,
        tryFreeOutput: String,
        tryFreeIsError: Boolean
    ) {
        headerLabel.text = "Chunk: 0x${address.toString(16)}"
        hiView.setText(hiOutput.trimEnd('\n', '\r'), hiIsError)
        tryFreeView.setText(tryFreeOutput.trimEnd('\n', '\r'), tryFreeIsError)
        outputPanel.revalidate()
        outputPanel.repaint()
    }

    override fun dispose() {
        hiView.dispose()
        tryFreeView.dispose()
    }
}
