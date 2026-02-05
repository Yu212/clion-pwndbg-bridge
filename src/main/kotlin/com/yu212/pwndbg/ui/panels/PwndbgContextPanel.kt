package com.yu212.pwndbg.ui.panels

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.project.Project
import com.intellij.util.ui.components.BorderLayoutPanel
import com.yu212.pwndbg.PwndbgService
import com.yu212.pwndbg.ui.PwndbgTabPanel
import java.awt.BorderLayout
import java.awt.Font
import java.util.*
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

class PwndbgContextPanel(private val project: Project) : PwndbgTabPanel {
    override val id: String = "context"
    override val title: String = "Context"
    private data class ContextEntry(
        val text: String,
        val isError: Boolean
    )

    private val document = EditorFactory.getInstance().createDocument("")
    private val editor = EditorFactory.getInstance().createViewer(document, project)
    private val ansiDecoder = AnsiEscapeDecoder()
    private var lastText: String? = null
    private val history = ArrayList<ContextEntry>()
    private val maxHistory = 1000
    private var historyIndex = -1

    private val prevButton = JButton("<")
    private val nextButton = JButton(">")
    private val latestButton = JButton(">>")
    private val statusLabel = JLabel("Latest")
    private val refreshAction = object : AnAction("Refresh Context", "Refresh context", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            refreshContext()
        }
    }
    private val pinAction = object : AnAction("Add Pin", "Add pin", AllIcons.General.Pin) {
        override fun actionPerformed(e: AnActionEvent) {
            togglePinAtCurrent()
        }

        override fun update(e: AnActionEvent) {
            val marked = historyIndex >= 0 && pins.contains(historyIndex)
            e.presentation.isEnabled = history.isNotEmpty()
            e.presentation.icon = if (marked) AllIcons.General.PinSelected else AllIcons.General.Pin
            e.presentation.text = if (marked) "Remove Pin" else "Add Pin"
        }
    }
    private val timelineSlider = JSlider()
    private val rootPanel = BorderLayoutPanel()
    private val actionToolbar = createActionToolbar()
    private val pins = TreeSet<Int>()
    private var sliderUpdating = false

    override val component: JComponent
        get() = rootPanel

    fun clearOutput() {
        history.clear()
        pins.clear()
        historyIndex = -1
        updateNavigationState()
        setContextOutput("", isError = false)
    }

    fun setContextOutput(text: String, isError: Boolean) {
        if (text == lastText) return
        lastText = text

        val baseType = if (isError) ProcessOutputTypes.STDERR else ProcessOutputTypes.STDOUT
        val segments = ArrayList<Pair<String, com.intellij.openapi.util.Key<*>>>()
        ansiDecoder.escapeText(text, baseType) { chunk, attrs ->
            if (chunk.isNotEmpty()) {
                segments.add(chunk to attrs)
            }
        }

        ApplicationManager.getApplication().invokeLater {
            val scrollOffset = editor.scrollingModel.verticalScrollOffset
            val caretOffset = editor.caretModel.offset

            ApplicationManager.getApplication().runWriteAction {
                document.setText(segments.joinToString(separator = "") { it.first })
                editor.markupModel.removeAllHighlighters()

                var offset = 0
                for ((chunk, attrs) in segments) {
                    val start = offset
                    offset += chunk.length
                    val type = ConsoleViewContentType.getConsoleViewType(attrs)
                    val attributes = type.attributes ?: continue
                    editor.markupModel.addRangeHighlighter(
                        start,
                        offset,
                        HighlighterLayer.SYNTAX,
                        attributes,
                        HighlighterTargetArea.EXACT_RANGE
                    )
                }
            }

            val clampedCaret = caretOffset.coerceAtMost(document.textLength)
            editor.caretModel.moveToOffset(clampedCaret)
            editor.scrollingModel.scrollVertically(scrollOffset)
        }
    }

    fun pushContextOutput(text: String, isError: Boolean) {
        val entry = ContextEntry(text, isError)
        val lastEntry = history.lastOrNull()
        if (lastEntry == entry) {
            historyIndex = history.lastIndex
            updateNavigationState()
            setContextOutput(text, isError)
            return
        }

        if (history.size >= maxHistory) {
            history.removeAt(0)
            if (pins.isNotEmpty()) {
                val updated = TreeSet<Int>()
                for (idx in pins) {
                    if (idx == 0) continue
                    updated.add(idx - 1)
                }
                pins.clear()
                pins.addAll(updated)
            }
            if (historyIndex > 0) {
                historyIndex -= 1
            }
        }
        history.add(entry)
        historyIndex = history.lastIndex
        updateNavigationState()
        setContextOutput(text, isError)
    }

    fun replaceLatestContextOutput(text: String, isError: Boolean) {
        if (history.isEmpty()) {
            pushContextOutput(text, isError)
            return
        }
        val entry = ContextEntry(text, isError)
        history[history.lastIndex] = entry
        historyIndex = history.lastIndex
        updateNavigationState()
        setContextOutput(text, isError)
    }

    private fun navigateTo(index: Int) {
        if (index < 0 || index >= history.size) return
        historyIndex = index
        val entry = history[historyIndex]
        updateNavigationState()
        setContextOutput(entry.text, entry.isError)
    }

    private fun updateNavigationState() {
        val latestIndex = history.lastIndex
        val hasHistory = history.isNotEmpty()
        prevButton.isEnabled = hasHistory && historyIndex > 0
        nextButton.isEnabled = hasHistory && historyIndex < latestIndex
        latestButton.isEnabled = hasHistory && historyIndex in 0 until latestIndex
        updateSliderState(hasHistory, latestIndex)

        val behind = if (hasHistory && historyIndex >= 0) latestIndex - historyIndex else 0
        statusLabel.text = if (behind == 0) "Latest" else "$behind behind"
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
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
        rootPanel.add(editor.component, BorderLayout.CENTER)

        prevButton.addActionListener { navigateTo(historyIndex - 1) }
        nextButton.addActionListener { navigateTo(historyIndex + 1) }
        latestButton.addActionListener { navigateTo(history.lastIndex) }

        timelineSlider.addChangeListener(object : ChangeListener {
            override fun stateChanged(e: ChangeEvent?) {
                if (sliderUpdating) return
                if (!timelineSlider.isEnabled) return
                val target = timelineSlider.value
                if (target != historyIndex) {
                    navigateTo(target)
                }
            }
        })
        installPinShortcuts()
        updateNavigationState()

        editor.settings.apply {
            isLineNumbersShown = false
            isLineMarkerAreaShown = false
            isFoldingOutlineShown = false
            isRightMarginShown = false
            isCaretRowShown = false
        }

        actionToolbar.component.isOpaque = false
    }

    private fun updateSliderState(hasHistory: Boolean, latestIndex: Int) {
        sliderUpdating = true
        try {
            timelineSlider.isEnabled = hasHistory
            timelineSlider.minimum = 0
            timelineSlider.maximum = if (hasHistory) latestIndex else 0
            timelineSlider.value = if (hasHistory && historyIndex >= 0) historyIndex else 0
            timelineSlider.majorTickSpacing = if (hasHistory) (latestIndex.coerceAtLeast(1)) else 0
            timelineSlider.paintTicks = false
            timelineSlider.paintLabels = true
            timelineSlider.labelTable = buildPinLabels(latestIndex)
        } finally {
            sliderUpdating = false
        }
    }

    private fun buildPinLabels(latestIndex: Int): Hashtable<Int, JLabel> {
        val table = Hashtable<Int, JLabel>()
        val labelFont = statusLabel.font.deriveFont(Font.BOLD)
        val emptyLabel = JLabel(" ").apply {
            font = labelFont
        }
        table[0] = emptyLabel
        if (latestIndex > 0) {
            table[latestIndex] = emptyLabel
        }
        for (idx in pins) {
            val label = JLabel("*")
            label.font = labelFont
            table[idx] = label
        }
        return table
    }

    private fun togglePinAtCurrent() {
        if (historyIndex < 0) return
        if (!pins.add(historyIndex)) {
            pins.remove(historyIndex)
        }
        updateNavigationState()
        actionToolbar.updateActionsAsync()
    }

    private fun installPinShortcuts() {
        val inputMap = rootPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        val actionMap = rootPanel.actionMap

        inputMap.put(KeyStroke.getKeyStroke("ctrl M"), "pwndbg.pin.toggle")
        inputMap.put(KeyStroke.getKeyStroke("alt UP"), "pwndbg.pin.prev")
        inputMap.put(KeyStroke.getKeyStroke("alt DOWN"), "pwndbg.pin.next")
        inputMap.put(KeyStroke.getKeyStroke("LEFT"), "pwndbg.context.prev")
        inputMap.put(KeyStroke.getKeyStroke("RIGHT"), "pwndbg.context.next")

        actionMap.put("pwndbg.pin.toggle", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                togglePinAtCurrent()
            }
        })
        actionMap.put("pwndbg.pin.prev", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                jumpPin(-1)
            }
        })
        actionMap.put("pwndbg.pin.next", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                jumpPin(1)
            }
        })
        actionMap.put("pwndbg.context.prev", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                navigateTo(historyIndex - 1)
            }
        })
        actionMap.put("pwndbg.context.next", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                navigateTo(historyIndex + 1)
            }
        })
    }

    private fun jumpPin(direction: Int) {
        if (pins.isEmpty()) {
            if (history.isNotEmpty()) {
                navigateTo(history.lastIndex)
            }
            return
        }
        val current = historyIndex
        val target = if (direction > 0) {
            pins.higher(current)?.takeIf { it >= 0 } ?: history.lastIndex
        } else {
            pins.lower(current)?.takeIf { it >= 0 } ?: history.lastIndex
        }
        navigateTo(target)
    }

    private fun refreshContext() {
        project.getService(PwndbgService::class.java)
            .executeCommandCapture("context") { result, error ->
                ApplicationManager.getApplication().invokeLater {
                    if (!error.isNullOrBlank()) {
                        setContextOutput("Pwndbg context refresh failed: $error\n", isError = true)
                        return@invokeLater
                    }
                    if (!result.isNullOrBlank()) {
                        replaceLatestContextOutput(result + "\n", isError = false)
                    }
                }
            }
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
}
