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
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.yu212.pwndbg.ui.components.PwndbgTabPanel
import com.yu212.pwndbg.ui.panels.*

@Service(Service.Level.PROJECT)
@State(name = "PwndbgToolWindowLayout", storages = [Storage("pwndbg-toolwindows.xml")])
class PwndbgToolWindowManager(private val project: Project): PersistentStateComponent<PwndbgToolWindowManager.State>, Disposable {
    data class WindowState(var id: String = "", var tabs: MutableList<String> = mutableListOf())
    data class State(
        var windows: MutableList<WindowState> = mutableListOf(),
        var tabFontSizes: MutableMap<String, Int> = mutableMapOf()
    )

    private data class TabBinding(
        val tabId: String,
        val temporary: Boolean,
        var panel: PwndbgTabPanel,
        var content: Content? = null,
        var windowId: String? = null
    )

    private var state = State()
    private var initialized = false

    private val toolWindowManager = ToolWindowManager.getInstance(project)
    private val contentFactory = ContentFactory.getInstance()

    var commandPanel: CommandPanel? = null
        private set
    var contextPanel: ContextPanel? = null
        private set
    var breakpointsPanel: BreakpointsPanel? = null
        private set
    var addressPanel: AddressPanel? = null
        private set
    var mapsPanel: MapsPanel? = null
        private set
    var heapPanel: HeapPanel? = null
        private set
    var heapInfoPanel: HeapInfoPanel? = null
        private set

    private val bindingsByTabId = LinkedHashMap<String, TabBinding>()
    private val tabIdByContent = LinkedHashMap<Content, String>()
    private val windowTabsById = LinkedHashMap<String, MutableList<String>>()

    override fun getState(): State = if (initialized && bindingsByTabId.isNotEmpty()) buildStateFromLayout() else state

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

    fun showAllWindows() {
        windowTabsById.keys.mapNotNull { toolWindowManager.getToolWindow(it) }.distinct().forEach { toolWindow ->
            toolWindow.show()
        }
    }

    fun moveTabToWindow(tabId: String, targetWindowId: String?) {
        val binding = bindingsByTabId[tabId] ?: return
        val content = binding.content ?: return
        val sourceWindow = findWindowByTabId(tabId) ?: return
        val destinationId = targetWindowId ?: createWindowId()
        if (sourceWindow.id == destinationId) return
        val destination = toolWindowManager.getToolWindow(destinationId) ?: createToolWindow(destinationId)
        tabIdByContent.remove(content)
        content.manager?.removeContent(content, true)
        removeTabFromWindow(sourceWindow.id, tabId)
        createContentOnWindow(binding, destination)
        destination.show()
    }

    fun getTabTextFontSize(tabId: String): Int? = state.tabFontSizes[tabId]

    fun isTextFontSizeSupported(tabId: String): Boolean {
        val binding = bindingsByTabId[tabId] ?: return false
        return binding.panel.supportsTextFontSize
    }

    fun setTabTextFontSize(tabId: String, size: Int?) {
        val binding = bindingsByTabId[tabId] ?: return
        if (size == null) {
            state.tabFontSizes.remove(tabId)
        } else {
            state.tabFontSizes[tabId] = size
        }
        val panel = binding.panel
        if (!panel.supportsTextFontSize) return
        panel.setTextFontSize(size)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: PwndbgTabPanel> getOrCreateTemporaryPanel(tabId: String, panelFactory: () -> T): T {
        val existing = bindingsByTabId[tabId]
        if (existing != null) {
            require(existing.temporary) { "Tab '$tabId' is not temporary." }
            return existing.panel as T
        }
        val panel = panelFactory()
        val binding = TabBinding(tabId = tabId, temporary = true, panel = panel)
        bindingsByTabId[tabId] = binding
        Disposer.register(this, panel)
        if (panel.supportsTextFontSize) {
            panel.setTextFontSize(state.tabFontSizes[binding.tabId])
        }
        return panel
    }

    fun showTemporaryTab(
        tabId: String,
        hostTabId: String,
        splitDirection: Int
    ) {
        val binding = bindingsByTabId[tabId] ?: return
        if (!binding.temporary) return

        val attachedWindow = findWindowByTabId(binding.tabId)
        if (attachedWindow != null) {
            binding.content?.let {
                attachedWindow.contentManager.setSelectedContent(it, true, true)
            }
            attachedWindow.show()
            return
        }

        val hostWindow = findWindowByTabId(hostTabId) ?: return
        val content = createContentOnWindow(binding, hostWindow)
        val selectedBeforeSplit = hostWindow.contentManager.selectedContent

        val decorator = InternalDecoratorImpl.findNearestDecorator(hostWindow.component)
        decorator?.splitWithContent(content, splitDirection, -1)

        if (selectedBeforeSplit != null) {
            hostWindow.contentManager.setSelectedContent(selectedBeforeSplit, true, true)
        }
        hostWindow.show()
    }

    fun showTemporaryTabBesideHost(
        tabId: String,
        hostTabId: String,
        focusNewTab: Boolean
    ) {
        val binding = bindingsByTabId[tabId] ?: return
        if (!binding.temporary) return

        val attachedWindow = findWindowByTabId(binding.tabId)
        if (attachedWindow != null) {
            if (focusNewTab) {
                binding.content?.let {
                    attachedWindow.contentManager.setSelectedContent(it, true, true)
                }
            }
            attachedWindow.show()
            return
        }

        val hostWindow = findWindowByTabId(hostTabId) ?: return
        val hostContent = bindingsByTabId[hostTabId]?.content
        val hostIndex = hostContent?.let { hostWindow.contentManager.getIndexOfContent(it) + 1 }
        val content = createContentOnWindow(binding, hostWindow, hostIndex)

        if (focusNewTab) {
            hostWindow.contentManager.setSelectedContent(content, true, true)
        }
        hostWindow.show()
    }

    private fun initializeToolWindows() {
        registerBindings()
        normalizeState()

        for (binding in bindingsByTabId.values) {
            if (binding.temporary) continue
            if (binding.panel.supportsTextFontSize) {
                binding.panel.setTextFontSize(state.tabFontSizes[binding.tabId])
            }
        }
        for (window in state.windows) {
            val toolWindow = createToolWindow(window.id)
            for (tabId in window.tabs) {
                val binding = bindingsByTabId[tabId] ?: continue
                createContentOnWindow(binding, toolWindow)
            }
        }
    }

    private fun registerBindings() {
        ApplicationManager.getApplication().assertIsDispatchThread()

        registerStaticTab(CommandPanel(project).also { commandPanel = it })
        registerStaticTab(ContextPanel(project).also { contextPanel = it })
        registerStaticTab(BreakpointsPanel(project).also { breakpointsPanel = it })
        registerStaticTab(AddressPanel(project).also { addressPanel = it })
        registerStaticTab(MapsPanel(project).also { mapsPanel = it })
        registerStaticTab(HeapPanel(project).also { heapPanel = it })
        registerStaticTab(HeapInfoPanel(project).also { heapInfoPanel = it })
    }

    private fun registerStaticTab(panel: PwndbgTabPanel) {
        val binding = TabBinding(
            tabId = panel.id,
            temporary = false,
            panel = panel
        )
        bindingsByTabId[panel.id] = binding
        Disposer.register(this, panel)
    }

    private fun normalizeState() {
        val staticTabIds = bindingsByTabId.values.filter { !it.temporary }.map { it.tabId }
        val staticSet = staticTabIds.toSet()

        val seen = LinkedHashSet<String>()
        val normalizedWindows = mutableListOf<WindowState>()
        val usedIds = LinkedHashSet<String>()
        var nextIndex = 1

        for (window in state.windows) {
            val cleaned = mutableListOf<String>()
            for (tabId in window.tabs) {
                if (tabId !in staticSet) continue
                if (!seen.add(tabId)) continue
                cleaned.add(tabId)
            }
            if (cleaned.isEmpty()) continue

            var normalizedId = window.id
            while (!usedIds.add(normalizedId)) {
                normalizedId = windowId(nextIndex++)
            }
            normalizedWindows.add(WindowState(normalizedId, cleaned))
        }

        if (normalizedWindows.isEmpty()) {
            normalizedWindows.add(WindowState(windowId(1), staticTabIds.toMutableList()))
        } else {
            val firstTabs = normalizedWindows.first().tabs
            for (tabId in staticTabIds) {
                if (seen.add(tabId)) {
                    firstTabs.add(tabId)
                }
            }
        }

        state.windows = normalizedWindows.toMutableList()
        state.tabFontSizes = state.tabFontSizes.filterKeys { it in staticSet }.toMutableMap()
    }

    private fun createToolWindow(windowId: String): ToolWindow {
        require(toolWindowManager.getToolWindow(windowId) == null) { "Tool window with id '$windowId' already exists." }
        val task = RegisterToolWindowTask(id = windowId, anchor = ToolWindowAnchor.RIGHT, canCloseContent = true)
        val toolWindow = toolWindowManager.registerToolWindow(task)
        toolWindow.stripeTitle = "Pwndbg"
        ToolWindowContentUi.setAllowTabsReordering(toolWindow, true)
        return toolWindow
    }

    private fun buildStateFromLayout(): State {
        val windows = mutableListOf<WindowState>()
        for ((windowId, tabs) in windowTabsById) {
            val persisted = tabs.filter { tabId -> bindingsByTabId[tabId]?.temporary != true }.toMutableList()
            if (persisted.isEmpty()) continue
            windows.add(WindowState(windowId, persisted))
        }
        val staticSet = bindingsByTabId.values.filter { !it.temporary }.map { it.tabId }.toSet()
        return State(
            windows = windows,
            tabFontSizes = state.tabFontSizes.filterKeys { it in staticSet }.toMutableMap()
        )
    }

    fun getTabId(content: Content): String? = tabIdByContent[content]

    fun getWindowIds(): List<String> = windowTabsById.keys.toList()

    fun getWindowLabel(windowId: String): String {
        val titles = windowTabsById[windowId].orEmpty().mapNotNull { tabId ->
            bindingsByTabId[tabId]?.panel?.title
        }
        return if (titles.isEmpty()) "Empty Window" else titles.joinToString(", ")
    }

    private fun findWindowByTabId(tabId: String): ToolWindow? {
        val binding = bindingsByTabId[tabId] ?: return null
        return binding.windowId?.let { toolWindowManager.getToolWindow(it) }
    }

    private fun createContentOnWindow(binding: TabBinding, toolWindow: ToolWindow, index: Int? = null): Content {
        val panel = binding.panel
        val content = contentFactory.createContent(panel.component, panel.title, false)
        content.setDisposer {
            ApplicationManager.getApplication().invokeLater {
                val current = bindingsByTabId[binding.tabId] ?: return@invokeLater
                if (current.content === content) {
                    tabIdByContent.remove(content)
                    current.content = null
                    val oldWindowId = current.windowId
                    if (oldWindowId != null) {
                        removeTabFromWindow(oldWindowId, current.tabId)
                    }
                    current.windowId = null
                }
            }
        }
        content.isCloseable = binding.temporary
        content.displayName = panel.title
        content.tabName = panel.title
        binding.content = content
        tabIdByContent[content] = binding.tabId
        if (index == null) {
            toolWindow.contentManager.addContent(content)
        } else {
            toolWindow.contentManager.addContent(content, index)
        }
        binding.windowId = toolWindow.id
        addTabToWindow(toolWindow.id, binding.tabId)
        return content
    }

    fun removeTemporaryTabs() {
        ApplicationManager.getApplication().invokeLater {
            val tempBindings = bindingsByTabId.values.filter { it.temporary }.toList()
            for (binding in tempBindings) {
                val content = binding.content ?: continue
                content.manager?.removeContent(content, true)
            }
        }
    }

    private fun addTabToWindow(windowId: String, tabId: String) {
        windowTabsById.getOrPut(windowId) { mutableListOf() }.apply {
            add(tabId)
        }
    }

    private fun removeTabFromWindow(windowId: String, tabId: String) {
        windowTabsById[windowId]?.remove(tabId)
        if (windowTabsById[windowId].isNullOrEmpty()) {
            windowTabsById.remove(windowId)
            toolWindowManager.unregisterToolWindow(windowId)
        }
    }

    private fun createWindowId(): String {
        var index = 1
        while (windowTabsById.containsKey(windowId(index))) {
            index += 1
        }
        return windowId(index)
    }

    override fun dispose() {
    }

    private fun windowId(index: Int): String = "pwndbg-$index"
}
