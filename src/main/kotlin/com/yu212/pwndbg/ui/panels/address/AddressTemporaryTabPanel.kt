package com.yu212.pwndbg.ui.panels.address

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.components.BorderLayoutPanel
import com.yu212.pwndbg.ui.components.PwndbgTabPanel
import javax.swing.JComponent

internal class AddressTemporaryTabPanel(
    project: Project,
    override val id: String,
    initialState: AddressInspectionTabState
): PwndbgTabPanel {
    override val title: String = initialState.address
    override val supportsTextFontSize: Boolean = true

    private val inspectView = AddressInspectionView(
        project = project,
        tabId = id,
        initialState = initialState,
        onOpenInNewTab = null
    )
    private val rootPanel = BorderLayoutPanel()

    init {
        rootPanel.addToCenter(JBScrollPane(inspectView.component))
    }

    override val component: JComponent
        get() = rootPanel

    override fun setTextFontSize(size: Int?) {
        inspectView.setTextFontSize(size)
    }

    override fun dispose() {
        inspectView.dispose()
    }
}
