package com.yu212.pwndbg.ui.panels

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.components.BorderLayoutPanel
import com.yu212.pwndbg.PwndbgService
import com.yu212.pwndbg.ui.CollapsibleSection
import com.yu212.pwndbg.ui.PwndbgTabPanel
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class PwndbgMapsPanel(private val project: Project) : PwndbgTabPanel {
    override val id: String = "maps"
    override val title: String = "Maps"
    override val supportsTextFontSize: Boolean = true
    private val vmmapView = CollapsibleSection("vmmap", project)
    private val checksecView = CollapsibleSection("checksec", project)
    private val gotView = CollapsibleSection("got", project)
    private val pltView = CollapsibleSection("plt", project)
    private val rootPanel = BorderLayoutPanel()
    private val outputPanel = JPanel()
    private val refreshAction = object : AnAction("Refresh", "Refresh maps", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            refreshAll()
        }
    }
    private val actionToolbar = createActionToolbar()

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

        actionToolbar.component.isOpaque = false
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
        service.executeCommandCapture("checksec") { output, error ->
            printResult(checksecView, output, error)
            service.executeCommandCapture("vmmap") { output2, error2 ->
                printResult(vmmapView, output2, error2)
                service.executeCommandCapture("got -r") { output3, error3 ->
                    printResult(gotView, output3, error3)
                    service.executeCommandCapture("plt") { output4, error4 ->
                        printResult(pltView, output4, error4)
                    }
                }
            }
        }
    }

    private fun printResult(view: CollapsibleSection, output: String?, error: String?) {
        if (!error.isNullOrBlank()) {
            view.setText(error.trimEnd('\n', '\r'), isError = true)
            outputPanel.revalidate()
            outputPanel.repaint()
            return
        }
        if (!output.isNullOrBlank()) {
            view.setText(output.trimEnd('\n', '\r'), isError = false)
            outputPanel.revalidate()
            outputPanel.repaint()
        }
    }

    override fun dispose() {
        checksecView.dispose()
        vmmapView.dispose()
        gotView.dispose()
        pltView.dispose()
    }

    private fun createActionToolbar(): com.intellij.openapi.actionSystem.ActionToolbar {
        val group = DefaultActionGroup()
        group.add(refreshAction)
        val toolbar = ActionManager.getInstance().createActionToolbar("PwndbgMapsActions", group, true)
        (toolbar as? ActionToolbarImpl)?.setReservePlaceAutoPopupIcon(false)
        toolbar.targetComponent = rootPanel
        return toolbar
    }
}
