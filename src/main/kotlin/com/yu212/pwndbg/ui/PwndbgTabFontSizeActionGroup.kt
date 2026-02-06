package com.yu212.pwndbg.ui

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project

class PwndbgTabFontSizeActionGroup : ActionGroup("Text Font Size", true) {
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
        if (!manager.isTextFontSizeSupported(tabId)) return emptyArray()

        val sizes = listOf(10, 11, 12, 13, 14, 16, 18, 20, 24)
        val actions = mutableListOf<AnAction>()
        actions.add(FontSizeToggleAction(project, tabId, null, "Default"))
        actions.add(Separator.getInstance())
        for (size in sizes) {
            actions.add(FontSizeToggleAction(project, tabId, size, "${size}px"))
        }
        return actions.toTypedArray()
    }

    private class FontSizeToggleAction(
        private val project: Project,
        private val tabId: String,
        private val fontSize: Int?,
        label: String
    ) : ToggleAction(label) {
        override fun isSelected(e: AnActionEvent): Boolean {
            val manager = project.getService(PwndbgToolWindowManager::class.java)
            return manager.getTabTextFontSize(tabId) == fontSize
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (!state) return
            project.getService(PwndbgToolWindowManager::class.java)
                .setTabTextFontSize(tabId, fontSize)
        }
    }
}
