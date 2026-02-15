package com.yu212.pwndbg.ui.panels

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.yu212.pwndbg.PwndbgService
import com.yu212.pwndbg.ui.ContextHistoryManager
import com.yu212.pwndbg.ui.PwndbgToolWindowManager
import com.yu212.pwndbg.ui.components.AnsiSegment
import com.yu212.pwndbg.ui.components.PwndbgTabPanel
import com.yu212.pwndbg.ui.heap.HeapAnsiTextViewer
import com.yu212.pwndbg.ui.heap.HeapChunkModel
import javax.swing.JComponent
import javax.swing.SwingConstants

class HeapPanel(
    private val project: Project
): PwndbgTabPanel {
    override val id: String = "heap"
    override val title: String = "Heap"
    override val supportsTextFontSize: Boolean = true

    private val viewer = HeapAnsiTextViewer(
        project = project,
        onChunkCtrlClick = ::inspectChunk
    )
    private val rootPanel = viewer.component

    override val component: JComponent
        get() = rootPanel

    override fun setTextFontSize(size: Int?) {
        viewer.setFontSize(size)
        rootPanel.revalidate()
        rootPanel.repaint()
    }

    override fun dispose() {
        viewer.dispose()
    }

    fun setHeapContent(
        segments: List<AnsiSegment>,
        chunks: List<HeapChunkModel>?
    ) {
        viewer.setHeapContent(segments, chunks, preserveView = true)
    }

    private fun inspectChunk(address: ULong) {
        val history = project.getService(ContextHistoryManager::class.java)
        if (!history.atLatest()) return
        val service = project.getService(PwndbgService::class.java)
        val manager = project.getService(PwndbgToolWindowManager::class.java)
        val addrString = "0x${address.toString(16)}"
        service.executeCommandsSequential(
            PwndbgService.CommandRequest("hi -v $addrString"),
            PwndbgService.CommandRequest("try-free $addrString")
        ) { (hi, tryFree) ->
            ApplicationManager.getApplication().invokeLater {
                val panel = manager.getOrCreateTemporaryPanel("chunk-detail") { ChunkDetailPanel(project) }
                panel.setChunkResult(address, hi.segments, tryFree.segments)
                manager.showTemporaryTab(
                    tabId = panel.id,
                    hostTabId = id,
                    splitDirection = SwingConstants.BOTTOM
                )
            }
        }
    }
}
