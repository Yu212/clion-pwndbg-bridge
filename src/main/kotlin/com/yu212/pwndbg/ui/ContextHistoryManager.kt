package com.yu212.pwndbg.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.yu212.pwndbg.PwndbgService
import com.yu212.pwndbg.settings.PwndbgSettingsService
import com.yu212.pwndbg.ui.components.AnsiTextViewer
import com.yu212.pwndbg.ui.heap.HeapChunkModel
import com.yu212.pwndbg.ui.heap.HeapChunkParser
import java.util.*

@Service(Service.Level.PROJECT)
class ContextHistoryManager(private val project: Project) {
    data class HistoryEntry(
        val contextSegments: List<AnsiTextViewer.AnsiSegment>,
        val heapSegments: List<AnsiTextViewer.AnsiSegment>,
        val heapChunks: List<HeapChunkModel>?,
        val arenasSegments: List<AnsiTextViewer.AnsiSegment>,
        val heapInfoSegments: List<AnsiTextViewer.AnsiSegment>,
        val binsSegments: List<AnsiTextViewer.AnsiSegment>
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

    fun atLatest(): Boolean = currentIndex == getLatestIndex()

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
        val earliest = getEarliestIndex() ?: return
        val latest = getLatestIndex() ?: return
        val target = if (direction < 0) {
            if (current == earliest) latest else pins.lower(current) ?: earliest
        } else {
            if (current == latest) earliest else pins.higher(current) ?: latest
        }
        showIndex(target)
    }

    fun refresh(callback: (HistoryEntry) -> Unit) {
        service.executeCommandCapture("context") { contextResult, contextError ->
            val contextSegments = AnsiTextViewer.decodeCommandOutput("context", contextResult, contextError)
            service.executeCommandCapture("vis-heap-chunks") { heapResult, heapError ->
                val heapSegments = AnsiTextViewer.decodeCommandOutput("vis-heap-chunks", stripHeapWarningPrefix(heapResult), heapError)
                val heapChunks = HeapChunkParser.parse(heapSegments)
                service.executeCommandCapture("arenas") { arenasResult, arenasError ->
                    val arenasSegments = AnsiTextViewer.decodeCommandOutput("arenas", arenasResult, arenasError)
                    service.executeCommandCapture("heap") { heapInfoResult, heapInfoError ->
                        val heapInfoSegments = AnsiTextViewer.decodeCommandOutput("heap", heapInfoResult, heapInfoError)
                        service.executeCommandCapture("bins") { binsResult, binsError ->
                            val binsSegments = AnsiTextViewer.decodeCommandOutput("bins", binsResult, binsError)
                            callback(
                                HistoryEntry(
                                    contextSegments,
                                    heapSegments,
                                    heapChunks,
                                    arenasSegments,
                                    heapInfoSegments,
                                    binsSegments
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun stripHeapWarningPrefix(text: String?): String? {
        val heapWarn = "\u001b[33mpwndbg will try to resolve the heap symbols via heuristic now since we cannot resolve the heap via the debug symbols.\nThis might not work in all cases. Use `help set resolve-heap-via-heuristic` for more details.\n\u001b[0m\n"
        return text?.removePrefix(heapWarn)?.trimStart('\n', '\r')
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
            val heapInfoPanel = toolWindowManager.heapInfoPanel
            if (currentIndex == null) {
                contextPanel?.setContextSegments(emptyList())
                heapPanel?.setHeapContent(emptyList(), null)
                heapInfoPanel?.setArenasSegments(emptyList())
                heapInfoPanel?.setHeapSegments(emptyList())
                heapInfoPanel?.setBinsSegments(emptyList())
            } else {
                val entry = history[currentIndex!! - droppedCount]
                contextPanel?.setContextSegments(entry.contextSegments)
                heapPanel?.setHeapContent(entry.heapSegments, entry.heapChunks)
                heapInfoPanel?.setArenasSegments(entry.arenasSegments)
                heapInfoPanel?.setHeapSegments(entry.heapInfoSegments)
                heapInfoPanel?.setBinsSegments(entry.binsSegments)
            }
            contextPanel?.updateHistoryState()
        }
    }
}
