package com.yu212.pwndbg.ui.panels

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.components.BorderLayoutPanel
import com.yu212.pwndbg.PwndbgService
import com.yu212.pwndbg.ui.PwndbgToolWindowManager
import com.yu212.pwndbg.ui.components.AnsiTextViewer
import com.yu212.pwndbg.ui.components.CollapsibleSection
import com.yu212.pwndbg.ui.components.CommandHistoryField
import com.yu212.pwndbg.ui.components.ToolbarFactory
import com.yu212.pwndbg.ui.components.PwndbgTabPanel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.*

private data class AddressInspectionSnapshot(
    val address: String,
    val xFormat: String,
    val telescopeLines: Int,
    val xinfoSegments: List<AnsiTextViewer.AnsiSegment>,
    val telescopeSegments: List<AnsiTextViewer.AnsiSegment>,
    val memorySegments: List<AnsiTextViewer.AnsiSegment>
)

private class AddressInspectionView(private val project: Project): Disposable {
    private val xFormatField = CommandHistoryField("16gx")
    private val xTitleLabel = JLabel("x/")
    private val xHeader = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
    private val xinfoView = CollapsibleSection("xinfo", project)
    private val telescopeTitleLabel = JLabel()
    private val telescopeDecreaseAction = object: AnAction("", null, AllIcons.General.Remove) {
        override fun actionPerformed(e: AnActionEvent) = updateTelescopeLines(-1)
    }
    private val telescopeIncreaseAction = object: AnAction("", null, AllIcons.General.Add) {
        override fun actionPerformed(e: AnActionEvent) = updateTelescopeLines(1)
    }
    private val telescopeView = CollapsibleSection(
        titleComponent = telescopeTitleLabel,
        project = project,
        extraActions = listOf(telescopeDecreaseAction, telescopeIncreaseAction)
    )
    private val memoryView = CollapsibleSection(xHeader, project)
    private val outputPanel = JPanel()

    private var telescopeLines = 8
    private var inspectedAddress: String? = null
    private var currentSnapshot: AddressInspectionSnapshot? = null

    val component: JComponent
        get() = outputPanel

    init {
        xFormatField.preferredSize = Dimension(100, xFormatField.preferredSize.height)
        xHeader.add(xTitleLabel)
        xHeader.add(xFormatField)
        xHeader.isOpaque = false

        outputPanel.layout = BoxLayout(outputPanel, BoxLayout.Y_AXIS)
        outputPanel.add(xinfoView.component)
        outputPanel.add(telescopeView.component)
        outputPanel.add(memoryView.component)

        xFormatField.addActionListener { updateMemoryOnly() }
        updateTelescopeTitle()
    }

    fun inspectAddress(address: String, onComplete: ((AddressInspectionSnapshot) -> Unit)? = null) {
        val baseAddress = address.trim()
        if (baseAddress.isEmpty()) return

        val xFormat = xFormatField.text.trim().ifEmpty { "16gx" }
        xFormatField.addHistory(xFormat)
        val service = project.getService(PwndbgService::class.java)
        service.executeCommandCapture("xinfo $baseAddress") { xinfoOutput, xinfoError ->
            val xinfo = AnsiTextViewer.decodeCommandOutput("xinfo", xinfoOutput, xinfoError)
            service.executeCommandCapture("telescope $baseAddress $telescopeLines") { telescopeOutput, telescopeError ->
                val telescope = AnsiTextViewer.decodeCommandOutput("telescope", telescopeOutput, telescopeError)
                val xCommand = "x/$xFormat $baseAddress"
                service.executeCommandCapture(xCommand) { memoryOutput, memoryError ->
                    val memory = AnsiTextViewer.decodeCommandOutput("x/$xFormat", memoryOutput, memoryError)
                    val snapshot = AddressInspectionSnapshot(
                        address = baseAddress,
                        xFormat = xFormat,
                        telescopeLines = telescopeLines,
                        xinfoSegments = xinfo,
                        telescopeSegments = telescope,
                        memorySegments = memory
                    )
                    setSnapshot(snapshot)
                    onComplete?.invoke(snapshot)
                }
            }
        }
    }

    fun setSnapshot(snapshot: AddressInspectionSnapshot) {
        inspectedAddress = snapshot.address
        telescopeLines = snapshot.telescopeLines
        updateTelescopeTitle()
        xFormatField.text = snapshot.xFormat
        xinfoView.setSegments(snapshot.xinfoSegments)
        telescopeView.setSegments(snapshot.telescopeSegments)
        memoryView.setSegments(snapshot.memorySegments)
        currentSnapshot = snapshot
        refreshOutputPanel()
    }

    fun getSnapshot(): AddressInspectionSnapshot? = currentSnapshot

    fun clearOutput() {
        inspectedAddress = null
        currentSnapshot = null
        xinfoView.setSegments(emptyList())
        telescopeView.setSegments(emptyList())
        memoryView.setSegments(emptyList())
        refreshOutputPanel()
    }

    fun setTextFontSize(size: Int?) {
        xinfoView.setTextFontSize(size)
        telescopeView.setTextFontSize(size)
        memoryView.setTextFontSize(size)
        refreshOutputPanel()
    }

    private fun updateMemoryOnly() {
        val baseAddress = inspectedAddress ?: return
        val xFormat = xFormatField.text.trim().ifEmpty { "16gx" }
        xFormatField.text = xFormat
        xFormatField.addHistory(xFormat)

        val service = project.getService(PwndbgService::class.java)
        val xCommand = "x/$xFormat $baseAddress"
        service.executeCommandCapture(xCommand) { output, error ->
            val memory = AnsiTextViewer.decodeCommandOutput("x/$xFormat", output, error)
            memoryView.setSegments(memory)
            refreshOutputPanel()
            currentSnapshot = currentSnapshot?.copy(
                xFormat = xFormat,
                memorySegments = memory
            )
        }
    }

    private fun updateTelescopeLines(delta: Int) {
        val nextValue = (telescopeLines + delta).coerceAtLeast(1)
        if (nextValue == telescopeLines) return
        telescopeLines = nextValue
        updateTelescopeTitle()
        val baseAddress = inspectedAddress ?: return
        val service = project.getService(PwndbgService::class.java)
        service.executeCommandCapture("telescope $baseAddress $telescopeLines") { output, error ->
            val telescope = AnsiTextViewer.decodeCommandOutput("telescope", output, error)
            telescopeView.setSegments(telescope)
            refreshOutputPanel()
            currentSnapshot = currentSnapshot?.copy(
                telescopeLines = telescopeLines,
                telescopeSegments = telescope
            )
        }
    }

    private fun updateTelescopeTitle() {
        telescopeTitleLabel.text = "telescope $telescopeLines"
    }

    private fun refreshOutputPanel() {
        outputPanel.revalidate()
        outputPanel.repaint()
    }

    override fun dispose() {
        xinfoView.dispose()
        telescopeView.dispose()
        memoryView.dispose()
    }
}

private class AddressTemporaryTabPanel(
    project: Project,
    override val id: String,
    addressTitle: String
): PwndbgTabPanel {
    override val title: String = addressTitle
    override val supportsTextFontSize: Boolean = true

    private val addressLabel = JLabel("Inspected: $addressTitle")
    private val inspectView = AddressInspectionView(project)
    private val rootPanel = BorderLayoutPanel()

    init {
        val header = JPanel(BorderLayout())
        header.add(addressLabel, BorderLayout.WEST)

        rootPanel.addToTop(header)
        rootPanel.addToCenter(JBScrollPane(inspectView.component))
    }

    override val component: JComponent
        get() = rootPanel

    fun setSnapshot(snapshot: AddressInspectionSnapshot) {
        inspectView.setSnapshot(snapshot)
    }

    fun inspectAddress(address: String) {
        inspectView.inspectAddress(address)
    }

    override fun setTextFontSize(size: Int?) {
        inspectView.setTextFontSize(size)
    }

    override fun dispose() {
        inspectView.dispose()
    }
}

class AddressPanel(private val project: Project): PwndbgTabPanel {
    override val id: String = "address"
    override val title: String = "Address"
    override val supportsTextFontSize: Boolean = true

    private val addressField = CommandHistoryField()
    private val inspectButton = JButton("Inspect")
    private val addressLabel = JLabel("Inspected: -")
    private val rootPanel = BorderLayoutPanel()
    private val openInNewTabAction = object: AnAction(
        "Open in New Tab",
        "Move current inspect result to a new tab",
        AllIcons.Actions.OpenNewTab
    ) {
        override fun actionPerformed(e: AnActionEvent) = moveCurrentInspectToNewTab()
    }
    private val openInNewTabToolbar = ToolbarFactory.create(
        place = "PwndbgAddressInspectActions",
        targetComponent = rootPanel,
        actions = listOf(openInNewTabAction)
    )

    private val inspectView = AddressInspectionView(project)
    private val stateCards = CardLayout()
    private val stateContainer = JPanel(stateCards)
    private val emptyStatePanel = JPanel(BorderLayout())
    private val inspectedStatePanel = BorderLayoutPanel()

    init {
        val inputPanel = JPanel(BorderLayout(8, 0))
        inputPanel.add(JLabel("Address"), BorderLayout.WEST)
        inputPanel.add(addressField, BorderLayout.CENTER)
        inputPanel.add(inspectButton, BorderLayout.EAST)

        val inspectActionsRow = JPanel(BorderLayout())
        inspectActionsRow.add(addressLabel, BorderLayout.WEST)
        inspectActionsRow.add(openInNewTabToolbar.component, BorderLayout.EAST)

        emptyStatePanel.add(
            JLabel("No address inspected yet."),
            BorderLayout.NORTH
        )

        inspectedStatePanel.addToTop(inspectActionsRow)
        inspectedStatePanel.addToCenter(JBScrollPane(inspectView.component))

        stateContainer.add(emptyStatePanel, CARD_EMPTY)
        stateContainer.add(inspectedStatePanel, CARD_INSPECTED)

        rootPanel.addToTop(inputPanel)
        rootPanel.addToCenter(stateContainer)

        inspectButton.addActionListener { event ->
            if (isCtrlModified(event)) {
                inspectInputInNewTab()
            } else {
                inspectOnAddressTab()
            }
        }
        addressField.addActionListener { event ->
            if (isCtrlModified(event)) {
                inspectInputInNewTab()
            } else {
                inspectOnAddressTab()
            }
        }
        addressField.inputMap.put(KeyStroke.getKeyStroke("ctrl ENTER"), "pwndbg.inspect.new.tab")
        addressField.actionMap.put("pwndbg.inspect.new.tab", object: AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = inspectInputInNewTab()
        })

        showEmptyState()
    }

    override val component: JComponent
        get() = rootPanel

    override fun setTextFontSize(size: Int?) {
        inspectView.setTextFontSize(size)
    }

    private fun inspectOnAddressTab() {
        val baseAddress = addressField.text.trim()
        if (baseAddress.isEmpty()) return
        addressField.addHistory(baseAddress)

        inspectView.inspectAddress(baseAddress) { snapshot ->
            ApplicationManager.getApplication().invokeLater {
                addressLabel.text = "Inspected: ${snapshot.address}"
                showInspectedState()
            }
        }
    }

    private fun inspectInputInNewTab() {
        val baseAddress = addressField.text.trim()
        if (baseAddress.isEmpty()) return
        addressField.addHistory(baseAddress)

        val manager = project.getService(PwndbgToolWindowManager::class.java)
        val tabId = nextTemporaryAddressTabId()
        val panel = manager.getOrCreateTemporaryPanel(tabId) {
            AddressTemporaryTabPanel(project, tabId, baseAddress)
        }
        panel.inspectAddress(baseAddress)
        manager.showTemporaryTabBesideHost(
            tabId = tabId,
            hostTabId = id,
            focusNewTab = true
        )
    }

    private fun moveCurrentInspectToNewTab() {
        val snapshot = inspectView.getSnapshot() ?: return

        val manager = project.getService(PwndbgToolWindowManager::class.java)
        val tabId = nextTemporaryAddressTabId()
        val panel = manager.getOrCreateTemporaryPanel(tabId) {
            AddressTemporaryTabPanel(project, tabId, snapshot.address)
        }
        panel.setSnapshot(snapshot)
        manager.showTemporaryTabBesideHost(
            tabId = tabId,
            hostTabId = id,
            focusNewTab = false
        )

        inspectView.clearOutput()
        showEmptyState()
    }

    private fun showEmptyState() {
        stateCards.show(stateContainer, CARD_EMPTY)
    }

    private fun showInspectedState() {
        stateCards.show(stateContainer, CARD_INSPECTED)
    }

    private fun isCtrlModified(event: ActionEvent): Boolean {
        return (event.modifiers and ActionEvent.CTRL_MASK) != 0
    }

    override fun dispose() {
        inspectView.dispose()
    }

    private companion object {
        const val CARD_EMPTY = "empty"
        const val CARD_INSPECTED = "inspected"
        val TEMPORARY_TAB_SEQUENCE = AtomicInteger(0)

        fun nextTemporaryAddressTabId(): String {
            return "address-view-${TEMPORARY_TAB_SEQUENCE.incrementAndGet()}"
        }
    }
}
