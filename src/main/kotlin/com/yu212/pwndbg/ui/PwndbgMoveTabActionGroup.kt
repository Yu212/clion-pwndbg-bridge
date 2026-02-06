package com.yu212.pwndbg.ui

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project

class PwndbgMoveTabActionGroup: ActionGroup("Move Tab To", true) {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val event = e ?: return emptyArray()
        val project = event.getData(CommonDataKeys.PROJECT) ?: return emptyArray()
        val toolWindow = event.getData(PlatformDataKeys.TOOL_WINDOW) ?: return emptyArray()
        if (!toolWindow.id.startsWith("pwndbg-")) return emptyArray()

        val contentManager = event.getData(PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER)
            ?: event.getData(PlatformDataKeys.CONTENT_MANAGER)
            ?: return emptyArray()
        val content = contentManager.selectedContent ?: return emptyArray()
        val manager = project.getService(PwndbgToolWindowManager::class.java)
        val tabId = manager.getTabId(content) ?: return emptyArray()

        val actions = mutableListOf<AnAction>()
        for (windowId in manager.getWindowIds()) {
            if (windowId == toolWindow.id) continue
            val label = manager.getWindowLabel(windowId)
            actions.add(MoveToWindowAction(project, tabId, windowId, label))
        }
        actions.add(MoveToNewWindowAction(project, tabId))
        return actions.toTypedArray()
    }

    private class MoveToNewWindowAction(
        private val project: Project,
        private val tabId: String
    ): AnAction("New Window") {
        override fun actionPerformed(e: AnActionEvent) {
            project.getService(PwndbgToolWindowManager::class.java).moveTabToWindow(tabId, null)
        }
    }

    private class MoveToWindowAction(
        private val project: Project,
        private val tabId: String,
        private val targetWindowId: String,
        label: String
    ): AnAction(label) {
        override fun actionPerformed(e: AnActionEvent) {
            project.getService(PwndbgToolWindowManager::class.java).moveTabToWindow(tabId, targetWindowId)
        }
    }
}
