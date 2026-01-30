package com.yu212.pwndbg.ui

import com.yu212.pwndbg.PwndbgService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class PwndbgAddressPanel(private val project: Project) : Disposable {
    private val addressField = JBTextField()
    private val xFormatField = JBTextField("16gx")
    private val runButton = JButton("Inspect")
    private val xTitleLabel = JLabel("x/")
    private val xinfoView = CollapsibleSection("xinfo", project)
    private val telescopeView = CollapsibleSection("telescope", project)
    private val memoryView = CollapsibleSection(xTitleLabel, project)
    private val rootPanel = BorderLayoutPanel()
    private val outputPanel = JPanel()

    init {
        val inputPanel = JPanel(BorderLayout(8, 0))
        val addressLabel = JLabel("Address")
        val xLabel = JLabel("x/")
        xFormatField.preferredSize = Dimension(100, xFormatField.preferredSize.height)

        val rightPanel = JPanel(BorderLayout(6, 0))
        rightPanel.add(xLabel, BorderLayout.WEST)
        rightPanel.add(xFormatField, BorderLayout.CENTER)

        inputPanel.add(addressLabel, BorderLayout.WEST)
        inputPanel.add(addressField, BorderLayout.CENTER)
        inputPanel.add(rightPanel, BorderLayout.EAST)

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
    }

    val component: JComponent
        get() = rootPanel

    private fun inspectAddress() {
        val baseAddress = addressField.text.trim()
        if (baseAddress.isEmpty()) return
        val xFormat = xFormatField.text.trim().ifEmpty { "16gx" }

        xinfoView.clear()
        telescopeView.clear()
        memoryView.clear()
        updateXTitle(xFormat)

        val service = project.getService(PwndbgService::class.java)

        service.executeCommandCapture("xinfo $baseAddress") { output, error ->
            printResult(xinfoView, output, error)
            service.executeCommandCapture("telescope $baseAddress") { output2, error2 ->
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
        updateXTitle(xFormat)
        memoryView.clear()
        val service = project.getService(PwndbgService::class.java)
        val xCommand = "x/$xFormat $baseAddress"
        service.executeCommandCapture(xCommand) { output, error ->
            printResult(memoryView, output, error)
        }
    }

    private fun updateXTitle(format: String) {
        xTitleLabel.text = "x/$format"
    }

    override fun dispose() {
        xinfoView.dispose()
        telescopeView.dispose()
        memoryView.dispose()
    }

}
