package com.yu212.pwndbg.ui.panels.address

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.components.BorderLayoutPanel
import com.yu212.pwndbg.ui.PwndbgToolWindowManager
import com.yu212.pwndbg.ui.components.CommandHistoryField
import com.yu212.pwndbg.ui.components.PwndbgTabPanel
import com.yu212.pwndbg.ui.components.ToolbarFactory
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.ActionEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.*

class AddressPanel(private val project: Project): PwndbgTabPanel {
    override val id: String = "address"
    override val title: String = "Address"
    override val supportsTextFontSize: Boolean = true

    private val addressField = CommandHistoryField()
    private val inspectButton = JButton("Inspect")
    private val addressLabel = JLabel("Inspected: -")
    private val rootPanel = BorderLayoutPanel()
    private val openInNewTabAction = object: AnAction("Open in New Tab", null, AllIcons.Actions.OpenNewTab) {
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
        val tabId = nextAddressTemporaryTabId()
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
        val tabId = nextAddressTemporaryTabId()
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

        fun nextAddressTemporaryTabId(): String {
            return "address-view-${TEMPORARY_TAB_SEQUENCE.incrementAndGet()}"
        }
    }
}
