package com.yu212.pwndbg.ui.panels.address

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.components.BorderLayoutPanel
import com.yu212.pwndbg.ui.components.PwndbgTabPanel
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class AddressTemporaryTabPanel(
    project: Project,
    override val id: String,
    addressTitle: String
): PwndbgTabPanel {
    override val title: String = addressTitle
    override val supportsTextFontSize: Boolean = true

    private val addressLabel = JLabel("Inspected: $addressTitle")
    private val inspectView = AddressInspectionView(project)
    private val rootPanel = BorderLayoutPanel()

    init {
        val header = JPanel(BorderLayout())
        header.add(addressLabel, BorderLayout.WEST)

        rootPanel.addToTop(header)
        rootPanel.addToCenter(JBScrollPane(inspectView.component))
    }

    override val component: JComponent
        get() = rootPanel

    fun setSnapshot(snapshot: AddressInspectionSnapshot) {
        inspectView.setSnapshot(snapshot)
    }

    fun inspectAddress(address: String) {
        inspectView.inspectAddress(address)
    }

    override fun setTextFontSize(size: Int?) {
        inspectView.setTextFontSize(size)
    }

    override fun dispose() {
        inspectView.dispose()
    }
}
