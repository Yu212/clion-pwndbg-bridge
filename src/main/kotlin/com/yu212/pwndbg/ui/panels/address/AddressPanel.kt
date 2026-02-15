package com.yu212.pwndbg.ui.panels.address

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.components.BorderLayoutPanel
import com.yu212.pwndbg.ui.PwndbgToolWindowManager
import com.yu212.pwndbg.ui.components.CommandHistoryField
import com.yu212.pwndbg.ui.components.PwndbgTabPanel
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
    private val rootPanel = BorderLayoutPanel()

    private val stateCards = CardLayout()
    private val stateContainer = JPanel(stateCards)
    private val emptyPanel = JPanel(BorderLayout())
    private val inspectedPanel = JPanel(BorderLayout())

    private var inspectView: AddressInspectionView? = null
    private var currentFontSize: Int? = null

    init {
        val inputPanel = JPanel(BorderLayout(8, 0))
        inputPanel.add(JLabel("Address"), BorderLayout.WEST)
        inputPanel.add(addressField, BorderLayout.CENTER)
        inputPanel.add(inspectButton, BorderLayout.EAST)

        emptyPanel.add(JLabel("No address inspected yet."), BorderLayout.NORTH)

        stateContainer.add(emptyPanel, CARD_EMPTY)
        stateContainer.add(inspectedPanel, CARD_INSPECTED)

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
        currentFontSize = size
        inspectView?.setTextFontSize(size)
    }

    private fun inspectOnAddressTab() {
        val baseAddress = addressField.text.trim()
        if (baseAddress.isEmpty()) {
            showEmptyState()
            return
        }
        addressField.addHistory(baseAddress)

        val existing = inspectView
        if (existing != null) {
            stateCards.show(stateContainer, CARD_INSPECTED)
            existing.inspectAddress(baseAddress)
            return
        }

        val created = AddressInspectionView(
            project = project,
            tabId = id,
            initialState = AddressInspectionTabState(address = baseAddress),
            onOpenInNewTab = ::openCurrentStateInNewTab
        )
        inspectView = created
        currentFontSize?.let(created::setTextFontSize)

        inspectedPanel.removeAll()
        inspectedPanel.add(JBScrollPane(created.component), BorderLayout.CENTER)
        inspectedPanel.revalidate()
        inspectedPanel.repaint()
        stateCards.show(stateContainer, CARD_INSPECTED)
    }

    private fun inspectInputInNewTab() {
        val baseAddress = addressField.text.trim()
        if (baseAddress.isEmpty()) return
        addressField.addHistory(baseAddress)

        val manager = project.getService(PwndbgToolWindowManager::class.java)
        val tabId = nextAddressTemporaryTabId()
        manager.getOrCreateTemporaryPanel(tabId) {
            AddressTemporaryTabPanel(
                project = project,
                id = tabId,
                initialState = AddressInspectionTabState(address = baseAddress)
            )
        }
        manager.showTemporaryTabBesideHost(
            tabId = tabId,
            hostTabId = id,
            focusNewTab = true
        )
    }

    private fun openCurrentStateInNewTab(state: AddressInspectionTabState) {
        val manager = project.getService(PwndbgToolWindowManager::class.java)
        val tabId = nextAddressTemporaryTabId()
        manager.getOrCreateTemporaryPanel(tabId) {
            AddressTemporaryTabPanel(
                project = project,
                id = tabId,
                initialState = state
            )
        }
        manager.showTemporaryTabBesideHost(
            tabId = tabId,
            hostTabId = id,
            focusNewTab = false
        )
        showEmptyState()
    }

    private fun showEmptyState() {
        inspectView?.dispose()
        inspectView = null
        inspectedPanel.removeAll()
        inspectedPanel.revalidate()
        inspectedPanel.repaint()
        stateCards.show(stateContainer, CARD_EMPTY)
    }

    private fun isCtrlModified(event: ActionEvent): Boolean {
        return (event.modifiers and ActionEvent.CTRL_MASK) != 0
    }

    override fun dispose() {
        inspectView?.dispose()
        inspectView = null
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
