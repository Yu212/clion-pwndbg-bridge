package com.yu212.pwndbg.ui.panels

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.components.BorderLayoutPanel
import com.yu212.pwndbg.PwndbgService
import com.yu212.pwndbg.ui.components.CollapsibleSection
import com.yu212.pwndbg.ui.components.CommandHistoryField
import com.yu212.pwndbg.ui.components.PwndbgTabPanel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

class AddressPanel(private val project: Project): PwndbgTabPanel {
    override val id: String = "address"
    override val title: String = "Address"
    override val supportsTextFontSize: Boolean = true

    private val addressField = CommandHistoryField()
    private val xFormatField = CommandHistoryField("16gx")
    private val runButton = JButton("Inspect")
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
    private val rootPanel = BorderLayoutPanel()
    private val outputPanel = JPanel()
    private var telescopeLines = 8

    init {
        val inputPanel = JPanel(BorderLayout(8, 0))
        val addressLabel = JLabel("Address")
        inputPanel.add(addressLabel, BorderLayout.WEST)
        inputPanel.add(addressField, BorderLayout.CENTER)

        xFormatField.preferredSize = Dimension(100, xFormatField.preferredSize.height)
        xHeader.add(xTitleLabel)
        xHeader.add(xFormatField)
        xHeader.isOpaque = false

        val inputRow = JPanel(BorderLayout(8, 0))
        inputRow.add(inputPanel, BorderLayout.CENTER)
        inputRow.add(runButton, BorderLayout.EAST)

        outputPanel.layout = BoxLayout(outputPanel, BoxLayout.Y_AXIS)
        outputPanel.add(xinfoView.component)
        outputPanel.add(telescopeView.component)
        outputPanel.add(memoryView.component)

        rootPanel.addToCenter(JBScrollPane(outputPanel))
        rootPanel.addToTop(inputRow)

        runButton.addActionListener { inspectAddress() }
        addressField.addActionListener { inspectAddress() }
        xFormatField.addActionListener { updateMemoryOnly() }
        updateTelescopeTitle()
    }

    override val component: JComponent
        get() = rootPanel

    override fun setTextFontSize(size: Int?) {
        xinfoView.setTextFontSize(size)
        telescopeView.setTextFontSize(size)
        memoryView.setTextFontSize(size)
        outputPanel.revalidate()
        outputPanel.repaint()
    }

    private fun inspectAddress() {
        val baseAddress = addressField.text.trim()
        if (baseAddress.isEmpty()) return
        val xFormat = xFormatField.text.trim().ifEmpty { "16gx" }
        addressField.addHistory(baseAddress)
        xFormatField.addHistory(xFormat)
        updateTelescopeTitle()

        val service = project.getService(PwndbgService::class.java)
        service.executeCommandCapture("xinfo $baseAddress") { output, error ->
            printResult(xinfoView, output, error)
            service.executeCommandCapture("telescope $baseAddress $telescopeLines") { output2, error2 ->
                printResult(telescopeView, output2, error2)
                val xCommand = "x/$xFormat $baseAddress"
                service.executeCommandCapture(xCommand) { output3, error3 ->
                    printResult(memoryView, output3, error3)
                }
            }
        }
    }

    private fun printResult(view: CollapsibleSection, output: String?, error: String?) {
        if (!error.isNullOrBlank()) {
            val trimmedError = error.trimEnd('\n', '\r')
            view.setText(trimmedError, isError = true)
            return
        }
        if (!output.isNullOrBlank()) {
            val trimmed = output.trimEnd('\n', '\r')
            view.setText(trimmed, isError = false)
        }
        outputPanel.revalidate()
        outputPanel.repaint()
    }

    private fun updateMemoryOnly() {
        val baseAddress = addressField.text.trim()
        if (baseAddress.isEmpty()) return
        val xFormat = xFormatField.text.trim().ifEmpty { "16gx" }
        xFormatField.addHistory(xFormat)
        val service = project.getService(PwndbgService::class.java)
        val xCommand = "x/$xFormat $baseAddress"
        service.executeCommandCapture(xCommand) { output, error ->
            printResult(memoryView, output, error)
        }
    }

    private fun updateTelescopeLines(delta: Int) {
        val nextValue = (telescopeLines + delta).coerceAtLeast(1)
        if (nextValue == telescopeLines) return
        telescopeLines = nextValue
        updateTelescopeTitle()
        val baseAddress = addressField.text.trim()
        if (baseAddress.isEmpty()) return
        val service = project.getService(PwndbgService::class.java)
        service.executeCommandCapture("telescope $baseAddress $telescopeLines") { output, error ->
            printResult(telescopeView, output, error)
        }
    }

    private fun updateTelescopeTitle() {
        telescopeTitleLabel.text = "telescope $telescopeLines"
    }

    override fun dispose() {
        xinfoView.dispose()
        telescopeView.dispose()
        memoryView.dispose()
    }
}
