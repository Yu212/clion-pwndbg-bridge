package com.yu212.pwndbg.ui

import com.yu212.pwndbg.PwndbgService
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.content.ContentFactory

class PwndbgToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val ui = RunnerLayoutUi.Factory.getInstance(project)
            .create("Pwndbg", "Pwndbg", "Pwndbg", toolWindow.disposable)

        val mainPanel = PwndbgPanel(project)
        val contextPanel = PwndbgContextPanel(project)
        val breakpointsPanel = PwndbgBreakpointsPanel(project)
        val addressPanel = PwndbgAddressPanel(project)
        val mapsPanel = PwndbgMapsPanel(project)

        val mainContent = ui.createContent("Pwndbg", mainPanel.component, "Command", null, mainPanel.component)
        val contextContent = ui.createContent("PwndbgContext", contextPanel.component, "Context", null, contextPanel.component)
        val breakpointsContent = ui.createContent("PwndbgBreakpoints", breakpointsPanel.component, "Breakpoints", null, breakpointsPanel.component)
        val addressContent = ui.createContent("PwndbgAddress", addressPanel.component, "Address", null, addressPanel.component)
        val mapsContent = ui.createContent("PwndbgMaps", mapsPanel.component, "Maps", null, mapsPanel.component)
        mainContent.isCloseable = false
        contextContent.isCloseable = false
        breakpointsContent.isCloseable = false
        addressContent.isCloseable = false
        mapsContent.isCloseable = false
        ui.addContent(mainContent)
        ui.addContent(contextContent)
        ui.addContent(breakpointsContent)
        ui.addContent(addressContent)
        ui.addContent(mapsContent)
        ui.selectAndFocus(mainContent, true, true)

        val containerContent = ContentFactory.getInstance()
            .createContent(ui.component, "", false)
        containerContent.isCloseable = false
        toolWindow.contentManager.addContent(containerContent)

        toolWindow.disposable.let {
            mainPanel.registerDisposable(it)
            Disposer.register(it, contextPanel)
            Disposer.register(it, breakpointsPanel)
            Disposer.register(it, addressPanel)
            Disposer.register(it, mapsPanel)
        }

        project.getService(PwndbgService::class.java)
            .attachPanels(mainPanel, contextPanel, mapsPanel)
    }
}
