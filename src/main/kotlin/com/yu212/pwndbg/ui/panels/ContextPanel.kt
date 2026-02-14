package com.yu212.pwndbg.ui.panels

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.Project
import com.intellij.util.ui.components.BorderLayoutPanel
import com.yu212.pwndbg.ui.ContextHistoryManager
import com.yu212.pwndbg.ui.components.AnsiTextViewer
import com.yu212.pwndbg.ui.components.PwndbgTabPanel
import java.awt.BorderLayout
import java.awt.Font
import java.util.*
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

class ContextPanel(private val project: Project): PwndbgTabPanel {
    override val id: String = "context"
    override val title: String = "Context"
    override val supportsTextFontSize: Boolean = true

    private val viewer = AnsiTextViewer(
        project,
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
    )
    private val historyManager: ContextHistoryManager
        get() = project.getService(ContextHistoryManager::class.java)

    private val prevButton = JButton("<")
    private val nextButton = JButton(">")
    private val latestButton = JButton(">>")
    private val statusLabel = JLabel("Latest")
    private val refreshAction = object: AnAction("Refresh Context", "Refresh context", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            historyManager.refresh { entry ->
                historyManager.replaceLatestEntry(entry)
            }
        }
    }
    private val pinAction = object: AnAction("Add Pin", "Add pin", AllIcons.General.Pin) {
        override fun actionPerformed(e: AnActionEvent) {
            togglePinAtCurrent()
        }

        override fun update(e: AnActionEvent) {
            val currentIndex = historyManager.getCurrentIndex()
            val marked = currentIndex != null && historyManager.isPinned(currentIndex)
            e.presentation.isEnabled = historyManager.hasHistory()
            e.presentation.icon = if (marked) AllIcons.General.PinSelected else AllIcons.General.Pin
            e.presentation.text = if (marked) "Remove Pin" else "Add Pin"
        }
    }
    private val timelineSlider = JSlider()
    private val rootPanel = BorderLayoutPanel()
    private val actionToolbar = createActionToolbar()
    private var sliderUpdating = false

    override val component: JComponent
        get() = rootPanel

    override fun setTextFontSize(size: Int?) {
        viewer.setFontSize(size)
        rootPanel.revalidate()
        rootPanel.repaint()
    }

    fun setContextSegments(segments: List<AnsiTextViewer.AnsiSegment>) {
        viewer.setSegments(segments, preserveView = true)
    }

    private fun navigateTo(index: Int) {
        historyManager.showIndex(index)
    }

    private fun updateNavigationState() {
        if (!historyManager.hasHistory()) {
            prevButton.isEnabled = false
            nextButton.isEnabled = false
            latestButton.isEnabled = false
            updateSliderState()
            statusLabel.text = "No history"
        } else {
            val currentIndex = historyManager.getCurrentIndex()!!
            val latestIndex = historyManager.getLatestIndex()!!
            val earliestIndex = historyManager.getEarliestIndex()!!

            prevButton.isEnabled = currentIndex > earliestIndex
            nextButton.isEnabled = currentIndex < latestIndex
            latestButton.isEnabled = currentIndex < latestIndex
            updateSliderState()

            val behind = latestIndex - currentIndex
            val ordinal = currentIndex + 1
            statusLabel.text = if (behind == 0) "Latest (#$ordinal)" else "$behind behind (#$ordinal)"
        }
    }

    override fun dispose() {
        viewer.dispose()
    }

    init {
        val toolbar = BorderLayoutPanel()
        val buttons = BorderLayoutPanel()
        buttons.addToLeft(prevButton)
        buttons.addToCenter(nextButton)
        buttons.addToRight(latestButton)
        toolbar.addToLeft(buttons)
        val rightPanel = BorderLayoutPanel()
        rightPanel.addToLeft(statusLabel)
        rightPanel.addToRight(actionToolbar.component)
        toolbar.addToRight(rightPanel)

        val sliderPanel = JPanel(BorderLayout())
        sliderPanel.add(timelineSlider, BorderLayout.CENTER)

        val topPanel = BorderLayoutPanel()
        topPanel.addToCenter(toolbar)
        topPanel.addToBottom(sliderPanel)

        rootPanel.layout = BorderLayout()
        rootPanel.add(topPanel, BorderLayout.NORTH)
        rootPanel.add(viewer.component, BorderLayout.CENTER)

        prevButton.addActionListener {
            val current = historyManager.getCurrentIndex() ?: return@addActionListener
            navigateTo(current - 1)
        }
        nextButton.addActionListener {
            val current = historyManager.getCurrentIndex() ?: return@addActionListener
            navigateTo(current + 1)
        }
        latestButton.addActionListener {
            val latest = historyManager.getLatestIndex() ?: return@addActionListener
            navigateTo(latest)
        }

        timelineSlider.addChangeListener(object: ChangeListener {
            override fun stateChanged(e: ChangeEvent?) {
                if (sliderUpdating) return
                if (!timelineSlider.isEnabled) return
                val target = timelineSlider.value
                val current = historyManager.getCurrentIndex()
                if (current == null || target != current) {
                    navigateTo(target)
                }
            }
        })
        installPinShortcuts()
        updateNavigationState()

        actionToolbar.component.isOpaque = false
    }

    private fun updateSliderState() {
        sliderUpdating = true
        try {
            val currentIndex = historyManager.getCurrentIndex()
            val latestIndex = historyManager.getLatestIndex()
            val earliestIndex = historyManager.getEarliestIndex()

            timelineSlider.isEnabled = historyManager.hasHistory()
            timelineSlider.minimum = earliestIndex ?: 0
            timelineSlider.maximum = latestIndex ?: 0
            timelineSlider.value = currentIndex ?: 0
            timelineSlider.majorTickSpacing = latestIndex?.coerceAtLeast(1) ?: 0
            timelineSlider.paintTicks = false
            timelineSlider.paintLabels = true

            val table = Hashtable<Int, JLabel>()
            val labelFont = statusLabel.font.deriveFont(Font.BOLD)
            val emptyLabel = JLabel(" ").apply {
                font = labelFont
            }
            table[earliestIndex ?: 0] = emptyLabel
            for (idx in historyManager.getPins()) {
                table[idx] = JLabel("*").apply {
                    font = labelFont
                }
            }
            timelineSlider.labelTable = table
        } finally {
            sliderUpdating = false
        }
    }

    private fun togglePinAtCurrent() {
        val currentIndex = historyManager.getCurrentIndex() ?: return
        historyManager.togglePin(currentIndex)
        updateHistoryState()
    }

    private fun installPinShortcuts() {
        val inputMap = rootPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        val actionMap = rootPanel.actionMap

        inputMap.put(KeyStroke.getKeyStroke("ctrl M"), "pwndbg.pin.toggle")
        inputMap.put(KeyStroke.getKeyStroke("alt UP"), "pwndbg.pin.prev")
        inputMap.put(KeyStroke.getKeyStroke("alt DOWN"), "pwndbg.pin.next")
        inputMap.put(KeyStroke.getKeyStroke("LEFT"), "pwndbg.context.prev")
        inputMap.put(KeyStroke.getKeyStroke("RIGHT"), "pwndbg.context.next")

        actionMap.put("pwndbg.pin.toggle", object: AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                togglePinAtCurrent()
            }
        })
        actionMap.put("pwndbg.pin.prev", object: AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                historyManager.jumpPin(-1)
            }
        })
        actionMap.put("pwndbg.pin.next", object: AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                historyManager.jumpPin(1)
            }
        })
        actionMap.put("pwndbg.context.prev", object: AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val current = historyManager.getCurrentIndex() ?: return
                navigateTo(current - 1)
            }
        })
        actionMap.put("pwndbg.context.next", object: AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val current = historyManager.getCurrentIndex() ?: return
                navigateTo(current + 1)
            }
        })
    }

    private fun createActionToolbar(): com.intellij.openapi.actionSystem.ActionToolbar {
        val group = DefaultActionGroup()
        group.add(pinAction)
        group.add(refreshAction)
        val toolbar = ActionManager.getInstance().createActionToolbar("PwndbgContextActions", group, true)
        (toolbar as? ActionToolbarImpl)?.setReservePlaceAutoPopupIcon(false)
        toolbar.targetComponent = rootPanel
        return toolbar
    }

    fun updateHistoryState() {
        updateNavigationState()
        actionToolbar.updateActionsAsync()
    }
}
