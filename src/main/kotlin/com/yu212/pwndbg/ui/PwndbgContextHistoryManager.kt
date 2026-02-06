package com.yu212.pwndbg.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.yu212.pwndbg.PwndbgService
import com.yu212.pwndbg.settings.PwndbgSettingsService
import java.util.TreeSet

@Service(Service.Level.PROJECT)
class PwndbgContextHistoryManager(private val project: Project) {
    data class HistoryEntry(
        val contextText: String,
        val contextError: Boolean,
        val heapText: String,
        val heapError: Boolean
    )

    private val history = ArrayList<HistoryEntry>()
    private var droppedCount = 0
    private var currentIndex: Int? = null
    private val pins = TreeSet<Int>()

    private val toolWindowManager: PwndbgToolWindowManager
        get() = project.getService(PwndbgToolWindowManager::class.java)

    private val service: PwndbgService
        get() = project.getService(PwndbgService::class.java)

    fun clearHistory() {
        history.clear()
        droppedCount = 0
        currentIndex = null
        pins.clear()
        updatePanels()
    }

    fun getCurrentIndex(): Int? = currentIndex

    fun getLatestIndex(): Int? = if (history.isEmpty()) null else droppedCount + history.lastIndex

    fun getEarliestIndex(): Int? = if (history.isEmpty()) null else droppedCount

    fun hasHistory(): Boolean = history.isNotEmpty()

    fun isPinned(index: Int): Boolean = pins.contains(index)

    fun togglePin(index: Int) {
        if (!pins.add(index)) {
            pins.remove(index)
        }
        updatePanels()
    }

    fun getPins(): Set<Int> = pins.toSet()

    fun jumpPin(direction: Int) {
        if (pins.isEmpty()) {
            val latest = getLatestIndex() ?: return
            showIndex(latest)
            return
        }
        val current = currentIndex ?: return
        val latest = getLatestIndex() ?: return
        val target = if (direction > 0) {
            pins.higher(current) ?: latest
        } else {
            pins.lower(current) ?: latest
        }
        showIndex(target)
    }

    fun refresh(callback: (HistoryEntry) -> Unit) {
        service.executeCommandCapture("context") { contextResult, contextError ->
            val contextIsError = contextResult.isNullOrBlank() || !contextError.isNullOrBlank()
            val contextText = if (!contextIsError) contextResult else "context command failed: $contextError\n"
            service.executeCommandCapture("vis-heap-chunks") { heapResult, heapError ->
                val heapIsError = heapResult.isNullOrBlank() || !heapError.isNullOrBlank()
                val heapText = if (!heapIsError) heapResult else "vis-heap-chunks command failed: $heapError\n"
                callback(HistoryEntry(contextText, contextIsError, heapText, heapIsError))
            }
        }
    }

    fun showIndex(index: Int) {
        if (history.isEmpty()) {
            currentIndex = null
            updatePanels()
            return
        }
        currentIndex = index.coerceIn(droppedCount, droppedCount + history.lastIndex)
        updatePanels()
    }

    fun replaceLatestEntry(entry: HistoryEntry) {
        if (history.isEmpty()) {
            pushEntry(entry)
            return
        }
        history[history.lastIndex] = entry
        currentIndex = droppedCount + history.lastIndex
        updatePanels()
    }

    fun pushEntry(entry: HistoryEntry) {
        val maxHistory = ApplicationManager.getApplication()
                .getService(PwndbgSettingsService::class.java)
                .getContextHistoryMax()
        while (history.size >= maxHistory) {
            history.removeAt(0)
            droppedCount += 1
        }
        history.add(entry)
        currentIndex = droppedCount + history.lastIndex
        updatePanels()
    }

    private fun updatePanels() {
        ApplicationManager.getApplication().invokeLater {
            val contextPanel = toolWindowManager.contextPanel
            val heapPanel = toolWindowManager.heapPanel
            if (currentIndex == null) {
                contextPanel?.setContextText("", isError = false)
                heapPanel?.setHeapText("", isError = false)
            } else {
                val entry = history[currentIndex!! - droppedCount]
                contextPanel?.setContextText(entry.contextText, entry.contextError)
                heapPanel?.setHeapText(entry.heapText, entry.heapError)
            }
            contextPanel?.updateHistoryState()
        }
    }
}
