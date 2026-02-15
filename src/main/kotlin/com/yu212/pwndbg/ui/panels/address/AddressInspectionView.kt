package com.yu212.pwndbg.ui.panels.address

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.ui.components.BorderLayoutPanel
import com.yu212.pwndbg.ui.components.CollapsibleSection
import com.yu212.pwndbg.ui.components.CommandHistoryField
import com.yu212.pwndbg.ui.components.ToolbarFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class AddressInspectionView(
    private val project: Project,
    private val tabId: String,
    initialState: AddressInspectionTabState,
    private val onOpenInNewTab: ((AddressInspectionTabState) -> Unit)? = null
): Disposable {
    private val timelineStore: AddressInspectionTimelineStore
        get() = project.getService(AddressInspectionTimelineStore::class.java)

    private var state: AddressInspectionTabState = initialState

    private val xFormatField = CommandHistoryField(state.xFormat)
    private val xTitleLabel = JLabel("x/")
    private val xHeader = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
    private val inspectedAddressLabel = JLabel()
    private val historyLabel = JLabel("No history")
    private val fixedAction = object: ToggleAction("Fixed", null, AllIcons.General.Pin) {
        override fun isSelected(e: AnActionEvent): Boolean = state.isFixed

        override fun setSelected(e: AnActionEvent, selected: Boolean) {
            if (state.isFixed == selected) return
            state = state.copy(fixedContextIndex = if (selected) currentContextIndex else null)
            timelineStore.updateFixedContextIndex(tabId, state.fixedContextIndex)
            renderFromHistory()
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.text = if (state.isFixed) "Unfix" else "Fix"
            e.presentation.icon = if (state.isFixed) AllIcons.General.PinSelected else AllIcons.General.Pin
        }
    }
    private val newTabAction = object: AnAction("Open in New Tab", null, AllIcons.Actions.OpenNewTab) {
        override fun actionPerformed(e: AnActionEvent) {
            onOpenInNewTab?.invoke(state)
        }
    }

    private val topToolbar = ToolbarFactory.create(
        place = "PwndbgAddressInspectionActions",
        targetComponent = xHeader,
        actions = buildList {
            if (onOpenInNewTab != null) add(newTabAction)
            add(fixedAction)
        }
    )

    private val xinfoView = CollapsibleSection("xinfo", project)
    private val telescopeTitleLabel = JLabel()
    private val telescopeDecreaseAction = object: AnAction("Decrease Count", null, AllIcons.General.Remove) {
        override fun actionPerformed(e: AnActionEvent) = updateTelescopeLines(-1)
    }
    private val telescopeIncreaseAction = object: AnAction("Increase Count", null, AllIcons.General.Add) {
        override fun actionPerformed(e: AnActionEvent) = updateTelescopeLines(1)
    }
    private val telescopeView = CollapsibleSection(
        titleComponent = telescopeTitleLabel,
        project = project,
        extraActions = listOf(telescopeDecreaseAction, telescopeIncreaseAction)
    )
    private val memoryView = CollapsibleSection(xHeader, project)
    private val outputPanel = BorderLayoutPanel()
    private val bodyPanel = JPanel()
    private val topHeader = BorderLayoutPanel()

    private var currentContextIndex: Int? = null
    private var latestContextIndex: Int? = null

    val component: JComponent
        get() = outputPanel

    init {
        xFormatField.preferredSize = Dimension(100, xFormatField.preferredSize.height)
        xHeader.add(xTitleLabel)
        xHeader.add(xFormatField)
        xHeader.isOpaque = false

        val rightHeader = JPanel(BorderLayout(6, 0))
        rightHeader.isOpaque = false
        rightHeader.add(historyLabel, BorderLayout.WEST)
        rightHeader.add(topToolbar.component, BorderLayout.EAST)

        topHeader.addToLeft(inspectedAddressLabel)
        topHeader.addToRight(rightHeader)
        topHeader.isOpaque = false

        bodyPanel.layout = javax.swing.BoxLayout(bodyPanel, javax.swing.BoxLayout.Y_AXIS)
        bodyPanel.add(xinfoView.component)
        bodyPanel.add(telescopeView.component)
        bodyPanel.add(memoryView.component)

        outputPanel.addToTop(topHeader)
        outputPanel.addToCenter(bodyPanel)

        xFormatField.addActionListener { onXFormatSubmitted() }
        updateTelescopeTitle()

        timelineStore.registerTab(tabId) { timeline ->
            handleTimelineState(timeline)
        }
        timelineStore.updateFixedContextIndex(tabId, state.fixedContextIndex)
    }

    fun inspectAddress(address: String) {
        val baseAddress = address.trim()
        if (baseAddress.isEmpty()) return

        applyXFormatInput()
        state = state.copy(address = baseAddress)

        val latest = latestContextIndex
        if (latest == null) {
            renderFromHistory()
            return
        }
        timelineStore.fetchFullAt(state, latest) {
            renderFromHistory()
        }
    }

    fun setTextFontSize(size: Int?) {
        xinfoView.setTextFontSize(size)
        telescopeView.setTextFontSize(size)
        memoryView.setTextFontSize(size)
        refreshOutputPanel()
    }

    private fun onXFormatSubmitted() {
        applyXFormatInput()
        val contextIndex = effectiveContextIndex()
        val latest = latestContextIndex
        if (contextIndex != null && latest != null && contextIndex == latest) {
            timelineStore.fetchMemoryAt(state, contextIndex) {
                renderFromHistory()
            }
            return
        }
        renderFromHistory()
    }

    private fun updateTelescopeLines(delta: Int) {
        val nextValue = (state.telescopeLines + delta).coerceAtLeast(1)
        val oldValue = state.telescopeLines
        if (nextValue == oldValue) return

        state = state.copy(telescopeLines = nextValue)
        updateTelescopeTitle()

        val contextIndex = effectiveContextIndex()
        val latest = latestContextIndex
        if (contextIndex != null && latest != null && contextIndex == latest && nextValue > oldValue) {
            val known = timelineStore.getKnownTelescopeLineCount(state, contextIndex)
            if (nextValue > known) {
                timelineStore.fetchTelescopeAt(state, contextIndex) {
                    renderFromHistory()
                }
                return
            }
        }
        renderFromHistory()
    }

    private fun handleTimelineState(timeline: AddressInspectionTimelineStore.TimelineState) {
        currentContextIndex = timeline.currentIndex
        latestContextIndex = timeline.latestIndex

        if (!state.isFixed && timeline.latestIndex != null) {
            timelineStore.fetchFullAt(state, timeline.latestIndex) {
                renderFromHistory()
            }
            return
        }
        renderFromHistory()
    }

    private fun renderFromHistory() {
        ApplicationManager.getApplication().invokeLater {
            inspectedAddressLabel.text = "Inspected: ${state.address}"
            val result = timelineStore.render(
                tabState = state,
                contextIndex = effectiveContextIndex(),
                latestIndex = latestContextIndex
            )
            historyLabel.text = result.historyLabelText
            xinfoView.setSegments(result.xinfoSegments)
            telescopeView.setSegments(result.telescopeSegments)
            memoryView.setSegments(result.memorySegments)
            updateTelescopeTitle()
            refreshOutputPanel()
            topToolbar.updateActionsAsync()
        }
    }

    private fun effectiveContextIndex(): Int? {
        return if (state.isFixed) state.fixedContextIndex else currentContextIndex
    }

    private fun updateTelescopeTitle() {
        telescopeTitleLabel.text = "telescope ${state.telescopeLines}"
    }

    private fun refreshOutputPanel() {
        outputPanel.revalidate()
        outputPanel.repaint()
    }

    private fun applyXFormatInput() {
        val xFormat = xFormatField.text.trim().ifEmpty { "16gx" }
        xFormatField.addHistory(xFormat)
        state = state.copy(xFormat = xFormat)
    }

    override fun dispose() {
        timelineStore.unregisterTab(tabId)
        xinfoView.dispose()
        telescopeView.dispose()
        memoryView.dispose()
    }
}
