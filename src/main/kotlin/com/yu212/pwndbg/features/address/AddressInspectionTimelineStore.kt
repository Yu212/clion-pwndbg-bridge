package com.yu212.pwndbg.features.address

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.yu212.pwndbg.app.PwndbgService
import com.yu212.pwndbg.shared.AnsiSegment

@Service(Service.Level.PROJECT)
internal class AddressInspectionTimelineStore(private val project: Project) {
    data class TimelineState(
        val currentIndex: Int?,
        val earliestIndex: Int?,
        val latestIndex: Int?
    )

    data class RenderResult(
        val historyLabelText: String,
        val xinfoSegments: List<AnsiSegment>,
        val telescopeSegments: List<AnsiSegment>,
        val memorySegments: List<AnsiSegment>
    )

    private data class HistoryKey(val address: String, val contextIndex: Int)

    private data class TimelineEntry(
        var xinfoSegments: List<AnsiSegment>,
        val memorySegments: MutableMap<String, List<AnsiSegment>>,
        var telescopeSegments: List<List<AnsiSegment>>
    )

    private val timelineHistory = LinkedHashMap<HistoryKey, TimelineEntry>()
    private val listeners = LinkedHashMap<String, (TimelineState) -> Unit>()
    private val fixedContextIndexes = LinkedHashMap<String, Int>()

    private var currentContextIndex: Int? = null
    private var earliestContextIndex: Int? = null
    private var latestContextIndex: Int? = null

    private val service: PwndbgService
        get() = project.getService(PwndbgService::class.java)

    fun registerTab(tabId: String, onTimelineChanged: (TimelineState) -> Unit) {
        listeners[tabId] = onTimelineChanged
        onTimelineChanged(
            TimelineState(
                currentIndex = currentContextIndex,
                earliestIndex = earliestContextIndex,
                latestIndex = latestContextIndex
            )
        )
    }

    fun unregisterTab(tabId: String) {
        listeners.remove(tabId)
        fixedContextIndexes.remove(tabId)
    }

    fun updateFixedContextIndex(tabId: String, fixedContextIndex: Int?) {
        if (fixedContextIndex == null) {
            fixedContextIndexes.remove(tabId)
        } else {
            fixedContextIndexes[tabId] = fixedContextIndex
        }
    }

    fun onContextIndexChanged(index: Int?) {
        currentContextIndex = index
        notifyListeners()
    }

    fun onHistoryRangeChanged(earliest: Int?, latest: Int?) {
        val previousEarliest = earliestContextIndex
        earliestContextIndex = earliest
        latestContextIndex = latest

        popDroppedHistory(previousEarliest, earliest)
        notifyListeners()
    }

    fun clear() {
        timelineHistory.clear()
        currentContextIndex = null
        earliestContextIndex = null
        latestContextIndex = null
        notifyListeners()
    }

    fun fetchFullAt(
        tabState: AddressInspectionTabState,
        contextIndex: Int,
        onComplete: () -> Unit
    ) {
        val address = tabState.address
        val xFormat = tabState.xFormat
        val telescopeCount = tabState.telescopeCount
        val key = HistoryKey(address, contextIndex)
        val cached = timelineHistory[key]
        if (cached != null &&
            cached.xinfoSegments.isNotEmpty() &&
            cached.memorySegments.containsKey(xFormat) &&
            cached.telescopeSegments.size >= telescopeCount
        ) {
            onComplete()
            return
        }
        service.executeCommandsSequential(
            PwndbgService.CommandRequest("xinfo $address"),
            PwndbgService.CommandRequest("telescope $address $telescopeCount"),
            PwndbgService.CommandRequest("x/$xFormat $address")
        ) { (xinfo, telescope, memory) ->
            val telescopeByLine = AnsiSegment.splitByLines(telescope.segments)
            val entry = timelineHistory[key]
            if (entry == null) {
                timelineHistory[key] = TimelineEntry(
                    xinfoSegments = xinfo.segments,
                    memorySegments = linkedMapOf(xFormat to memory.segments),
                    telescopeSegments = telescopeByLine
                )
            } else {
                entry.xinfoSegments = xinfo.segments
                entry.memorySegments[xFormat] = memory.segments
                if (telescopeByLine.size >= entry.telescopeSegments.size) {
                    entry.telescopeSegments = telescopeByLine
                }
            }
            onComplete()
        }
    }

    fun fetchMemoryAt(tabState: AddressInspectionTabState, contextIndex: Int, onComplete: () -> Unit) {
        val address = tabState.address
        val xFormat = tabState.xFormat
        val key = HistoryKey(address, contextIndex)
        val cached = timelineHistory[key]
        if (cached != null && cached.memorySegments.containsKey(xFormat)) {
            onComplete()
            return
        }
        service.executeCommandCaptureDecoded(
            PwndbgService.CommandRequest("x/$xFormat $address")
        ) { memory ->
            val entry = timelineHistory[key]
            if (entry == null) {
                timelineHistory[key] = TimelineEntry(
                    xinfoSegments = emptyList(),
                    memorySegments = linkedMapOf(xFormat to memory.segments),
                    telescopeSegments = emptyList()
                )
            } else {
                entry.memorySegments[xFormat] = memory.segments
            }
            onComplete()
        }
    }

    fun fetchTelescopeAt(tabState: AddressInspectionTabState, contextIndex: Int, onComplete: () -> Unit) {
        val address = tabState.address
        val telescopeCount = tabState.telescopeCount
        val key = HistoryKey(address, contextIndex)
        val cached = timelineHistory[key]
        if (cached != null && cached.telescopeSegments.size >= telescopeCount) {
            onComplete()
            return
        }
        service.executeCommandCaptureDecoded(
            PwndbgService.CommandRequest("telescope $address $telescopeCount")
        ) { telescope ->
            val telescopeByLine = AnsiSegment.splitByLines(telescope.segments)
            val entry = timelineHistory[key]
            if (entry == null) {
                timelineHistory[key] = TimelineEntry(
                    xinfoSegments = emptyList(),
                    memorySegments = linkedMapOf(),
                    telescopeSegments = telescopeByLine
                )
            } else if (telescopeByLine.size >= entry.telescopeSegments.size) {
                entry.telescopeSegments = telescopeByLine
            }
            onComplete()
        }
    }

    fun getKnownTelescopeCount(tabState: AddressInspectionTabState, contextIndex: Int): Int {
        val key = HistoryKey(tabState.address, contextIndex)
        return timelineHistory[key]?.telescopeSegments?.size ?: 0
    }

    fun render(
        tabState: AddressInspectionTabState,
        contextIndex: Int?,
        latestIndex: Int?
    ): RenderResult {
        val address = tabState.address
        val xFormat = tabState.xFormat
        val telescopeCount = tabState.telescopeCount
        val historyLabel = historyLabel(contextIndex, latestIndex)
        if (contextIndex == null) {
            val segments = infoSegments("No context history available.")
            return RenderResult(historyLabel, segments, segments, segments)
        }

        val entry = timelineHistory[HistoryKey(address, contextIndex)]
        if (entry == null) {
            val segments = infoSegments("No inspection data at this context.")
            return RenderResult(historyLabel, segments, segments, segments)
        }

        val xinfo = entry.xinfoSegments.ifEmpty { infoSegments("No xinfo data at this context.") }
        val memory = entry.memorySegments[xFormat] ?: errorSegments("x/$xFormat is unavailable at this context.")
        val telescope = renderTelescope(entry.telescopeSegments, telescopeCount)
        return RenderResult(
            historyLabelText = historyLabel,
            xinfoSegments = xinfo,
            telescopeSegments = telescope,
            memorySegments = memory
        )
    }

    private fun notifyListeners() {
        val state = TimelineState(
            currentIndex = currentContextIndex,
            earliestIndex = earliestContextIndex,
            latestIndex = latestContextIndex
        )
        ApplicationManager.getApplication().invokeLater {
            listeners.values.forEach { listener ->
                listener(state)
            }
        }
    }

    private fun popDroppedHistory(previousEarliest: Int?, newEarliest: Int?) {
        if (previousEarliest == null || newEarliest == null || newEarliest <= previousEarliest) return
        val protectedIndexes = fixedContextIndexes.values.toSet()
        val dropped = (previousEarliest until newEarliest).toSet()
        timelineHistory.entries.removeIf { (key, _) ->
            key.contextIndex in dropped && key.contextIndex !in protectedIndexes
        }
    }

    private fun renderTelescope(lines: List<List<AnsiSegment>>, requestedLines: Int): List<AnsiSegment> {
        if (lines.isEmpty()) return emptyList()
        val shownCount = requestedLines.coerceAtLeast(1).coerceAtMost(lines.size)
        val shown = AnsiSegment.flattenLines(lines.take(shownCount))
        if (requestedLines <= lines.size) return shown
        return shown + infoSegments("\n[unknown] ${requestedLines - lines.size} line(s) are not available at this context.")
    }

    private fun historyLabel(contextIndex: Int?, latestIndex: Int?): String {
        if (contextIndex == null || latestIndex == null) return "No history"
        val behind = latestIndex - contextIndex
        val ordinal = contextIndex + 1
        return if (behind == 0) "Latest (#$ordinal)" else "$behind behind (#$ordinal)"
    }

    private fun infoSegments(message: String): List<AnsiSegment> = AnsiSegment.decodeAnsi(message, isError = false)

    private fun errorSegments(message: String): List<AnsiSegment> = AnsiSegment.decodeAnsi(message, isError = true)
}
