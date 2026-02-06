package com.yu212.pwndbg.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.yu212.pwndbg.ui.panels.*

@Service(Service.Level.PROJECT)
@State(name = "PwndbgToolWindowLayout", storages = [Storage("pwndbg-toolwindows.xml")])
class PwndbgToolWindowManager(private val project: Project): PersistentStateComponent<PwndbgToolWindowManager.State>, Disposable {
    data class WindowState(var id: String = "", var tabs: MutableList<String> = mutableListOf())
    data class State(
        var windows: MutableList<WindowState> = mutableListOf(),
        var tabFontSizes: MutableMap<String, Int> = mutableMapOf()
    )

    private var state = State()
    private var initialized = false

    private val toolWindowManager = ToolWindowManager.getInstance(project)
    private val contentFactory = ContentFactory.getInstance()

    var commandPanel: PwndbgCommandPanel? = null
        private set
    var contextPanel: PwndbgContextPanel? = null
        private set
    var breakpointsPanel: PwndbgBreakpointsPanel? = null
        private set
    var addressPanel: PwndbgAddressPanel? = null
        private set
    var mapsPanel: PwndbgMapsPanel? = null
        private set
    var heapPanel: PwndbgHeapPanel? = null
        private set
    var heapInfoPanel: PwndbgHeapInfoPanel? = null
        private set

    private var panelsList: List<PwndbgTabPanel> = emptyList()
    private var panelsById: Map<String, PwndbgTabPanel> = emptyMap()

    private val contentByTabId = LinkedHashMap<String, Content>()
    private val tabIdByContent = LinkedHashMap<Content, String>()
    private val tabToWindowId = LinkedHashMap<String, String>()
    private val windowTabsById = LinkedHashMap<String, MutableList<String>>()

    override fun getState(): State = if (initialized && panelsList.isNotEmpty()) buildStateFromLayout() else state

    override fun loadState(state: State) {
        this.state = state
    }

    fun ensureInitialized() {
        if (initialized) return
        initialized = true
        ApplicationManager.getApplication().invokeLater {
            initializeToolWindows()
        }
    }

    fun showPrimaryWindow() {
        val target = tabToWindowId["command"] ?: windowTabsById.keys.firstOrNull() ?: return
        toolWindowManager.getToolWindow(target)?.show()
    }

    fun moveTabToWindow(tabId: String, targetWindowId: String?) {
        val content = contentByTabId[tabId] ?: return
        val sourceWindowId = tabToWindowId[tabId] ?: return
        val sourceToolWindow = toolWindowManager.getToolWindow(sourceWindowId) ?: return
        val destinationId = targetWindowId ?: createWindowId()
        val destinationToolWindow = getOrCreateToolWindow(destinationId)
        if (destinationToolWindow.contentManager.getIndexOfContent(content) >= 0) return

        sourceToolWindow.contentManager.removeContent(content, false)
        destinationToolWindow.contentManager.addContent(content)
        getTabTitle(tabId)?.let { title ->
            content.displayName = title
            content.tabName = title
        }

        windowTabsById[sourceWindowId]?.remove(tabId)
        windowTabsById.getOrPut(destinationId) { mutableListOf() }.add(tabId)
        tabToWindowId[tabId] = destinationId

        if (windowTabsById[sourceWindowId].isNullOrEmpty()) {
            removeToolWindow(sourceWindowId)
        }
    }

    fun getTabTextFontSize(tabId: String): Int? = state.tabFontSizes[tabId]

    fun isTextFontSizeSupported(tabId: String): Boolean = panelsById[tabId]?.supportsTextFontSize == true

    fun setTabTextFontSize(tabId: String, size: Int?) {
        val panel = panelsById[tabId] ?: return
        if (!panel.supportsTextFontSize) return
        if (size == null) {
            state.tabFontSizes.remove(tabId)
        } else {
            state.tabFontSizes[tabId] = size
        }
        panel.setTextFontSize(size)
    }

    private fun initializeToolWindows() {
        createPanels()
        normalizeState()

        for (panel in panelsList) {
            if (!panel.supportsTextFontSize) continue
            val size = state.tabFontSizes[panel.id]
            panel.setTextFontSize(size)
        }
        for (window in state.windows) {
            windowTabsById[window.id] = window.tabs.toMutableList()
            val toolWindow = getOrCreateToolWindow(window.id)
            for (tabId in window.tabs) {
                addTabToWindow(tabId, toolWindow)
            }
        }
    }

    private fun addTabToWindow(tabId: String, toolWindow: ToolWindow) {
        val content = getOrCreateContent(tabId)
        if (toolWindow.contentManager.getIndexOfContent(content) >= 0) return
        toolWindow.contentManager.addContent(content)
        tabToWindowId[tabId] = toolWindow.id
    }

    private fun getOrCreateContent(tabId: String): Content {
        return contentByTabId.getOrPut(tabId) {
            val panel = requirePanel(tabId)
            val content = contentFactory.createContent(panel.component, panel.title, false)
            content.isCloseable = false
            content.displayName = panel.title
            content.tabName = panel.title
            tabIdByContent[content] = tabId
            content
        }
    }

    private fun normalizeState() {
        val allTabs = getAllTabIds()
        val seen = LinkedHashSet<String>()
        val normalizedWindows = mutableListOf<WindowState>()
        val usedIds = LinkedHashSet<String>()
        var nextIndex = 1
        val validTabs = allTabs.toSet()

        for (window in state.windows) {
            val cleaned = mutableListOf<String>()
            for (tabId in window.tabs) {
                if (getTabTitle(tabId) == null) continue
                if (!seen.add(tabId)) continue
                cleaned.add(tabId)
            }
            if (cleaned.isNotEmpty()) {
                var normalizedId = window.id
                while (!usedIds.add(normalizedId)) {
                    normalizedId = windowId(nextIndex++)
                }
                normalizedWindows.add(WindowState(normalizedId, cleaned))
            }
        }

        if (normalizedWindows.isEmpty()) {
            normalizedWindows.add(WindowState(windowId(1), allTabs.toMutableList()))
        } else {
            val first = normalizedWindows.first().tabs
            for (tabId in allTabs) {
                if (seen.add(tabId)) {
                    first.add(tabId)
                }
            }
        }

        state.windows = normalizedWindows.toMutableList()
        state.tabFontSizes = state.tabFontSizes.filterKeys { it in validTabs }.toMutableMap()
    }

    private fun getOrCreateToolWindow(windowId: String): ToolWindow {
        val existing = toolWindowManager.getToolWindow(windowId)
        if (existing != null) return existing
        val task = RegisterToolWindowTask(id = windowId, anchor = ToolWindowAnchor.RIGHT, canCloseContent = false)
        val toolWindow = toolWindowManager.registerToolWindow(task)
        toolWindow.stripeTitle = "Pwndbg"
        return toolWindow
    }

    private fun createWindowId(): String {
        var index = 1
        while (windowTabsById.containsKey(windowId(index))) {
            index += 1
        }
        return windowId(index)
    }

    private fun removeToolWindow(windowId: String) {
        windowTabsById.remove(windowId)
        toolWindowManager.unregisterToolWindow(windowId)
    }

    private fun buildStateFromLayout(): State {
        val windows = mutableListOf<WindowState>()
        for ((windowId, tabs) in windowTabsById) {
            val tabList = tabs.toMutableList()
            if (tabList.isEmpty()) continue
            windows.add(WindowState(windowId, tabList))
        }
        return State(windows, state.tabFontSizes.toMutableMap())
    }

    fun getTabId(content: Content): String? = tabIdByContent[content]

    fun getWindowIds(): List<String> = windowTabsById.keys.toList()

    fun getWindowLabel(windowId: String): String {
        val tabs = windowTabsById[windowId].orEmpty()
        val titles = tabs.mapNotNull { getTabTitle(it) }
        return if (titles.isEmpty()) "Empty Window" else titles.joinToString(", ")
    }

    private fun createPanels() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        panelsList = listOf(
            PwndbgCommandPanel(project).also { commandPanel = it },
            PwndbgContextPanel(project).also { contextPanel = it },
            PwndbgBreakpointsPanel(project).also { breakpointsPanel = it },
            PwndbgAddressPanel(project).also { addressPanel = it },
            PwndbgMapsPanel(project).also { mapsPanel = it },
            PwndbgHeapPanel(project).also { heapPanel = it },
            PwndbgHeapInfoPanel(project).also { heapInfoPanel = it }
        )
        panelsById = panelsList.associateBy { it.id }
        panelsList.forEach { Disposer.register(this, it) }
    }

    private fun getTabTitle(tabId: String): String? = panelsById[tabId]?.title

    private fun getAllTabIds(): List<String> = panelsList.map { it.id }

    private fun requirePanel(tabId: String): PwndbgTabPanel {
        return panelsById[tabId] ?: error("Unknown tab id: $tabId")
    }

    override fun dispose() {
    }

    private fun windowId(index: Int): String = "pwndbg-$index"
}
