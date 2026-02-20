package com.yu212.pwnlion.features.maps

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.components.BorderLayoutPanel
import com.yu212.pwnlion.app.PwndbgService
import com.yu212.pwnlion.shared.ui.PwndbgTabPanel
import com.yu212.pwnlion.shared.ui.ToolbarFactory
import com.yu212.pwnlion.shared.ui.components.CollapsibleSection
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class MapsPanel(private val project: Project): PwndbgTabPanel {
    override val id: String = "maps"
    override val title: String = "Maps"
    override val supportsTextFontSize: Boolean = true

    private val vmmapView = CollapsibleSection("vmmap", project)
    private val checksecView = CollapsibleSection("checksec", project)
    private val gotView = CollapsibleSection("got", project)
    private val pltView = CollapsibleSection("plt", project)
    private val rootPanel = BorderLayoutPanel()
    private val outputPanel = JPanel()
    private val refreshAction = object: AnAction("Refresh", null, AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            refreshAll()
        }
    }
    private val actionToolbar = ToolbarFactory.create(
        place = "PwndbgMapsActions",
        targetComponent = rootPanel,
        actions = listOf(refreshAction)
    )

    init {
        val toolbar = JPanel(BorderLayout(8, 0))
        toolbar.add(JLabel("Maps / GOT / PLT"), BorderLayout.WEST)
        toolbar.add(actionToolbar.component, BorderLayout.EAST)

        outputPanel.layout = BoxLayout(outputPanel, BoxLayout.Y_AXIS)
        outputPanel.add(checksecView.component)
        outputPanel.add(vmmapView.component)
        outputPanel.add(gotView.component)
        outputPanel.add(pltView.component)

        rootPanel.addToTop(toolbar)
        rootPanel.addToCenter(JBScrollPane(outputPanel))
    }

    override val component: JComponent
        get() = rootPanel

    override fun setTextFontSize(size: Int?) {
        checksecView.setTextFontSize(size)
        vmmapView.setTextFontSize(size)
        gotView.setTextFontSize(size)
        pltView.setTextFontSize(size)
        outputPanel.revalidate()
        outputPanel.repaint()
    }

    fun refreshAll() {
        val service = project.getService(PwndbgService::class.java)
        service.executeCommandsSequential(
            PwndbgService.CommandRequest("checksec"),
            PwndbgService.CommandRequest("vmmap"),
            PwndbgService.CommandRequest("got -r"),
            PwndbgService.CommandRequest("plt")
        ) { (checksec, vmmap, got, plt) ->
            printResult(checksecView, checksec)
            printResult(vmmapView, vmmap)
            printResult(gotView, got)
            printResult(pltView, plt)
        }
    }

    private fun printResult(view: CollapsibleSection, result: PwndbgService.CommandCaptureResult) {
        view.setSegments(result.segments)
        outputPanel.revalidate()
        outputPanel.repaint()
    }

    override fun dispose() {
        checksecView.dispose()
        vmmapView.dispose()
        gotView.dispose()
        pltView.dispose()
    }
}
