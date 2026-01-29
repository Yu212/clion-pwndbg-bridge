package com.yu212.pwndbg.ui

import com.yu212.pwndbg.PwndbgService
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

class PwndbgAddressPanel(private val project: Project) : Disposable {
    private val xinfoView = AnsiViewer(project)
    private val telescopeView = AnsiViewer(project)
    private val memoryView = AnsiViewer(project)

    private val addressField = JBTextField()
    private val xFormatField = JBTextField("16gx")
    private val runButton = JButton("Inspect")
    private val xTitleLabel = JLabel("x/")
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
        outputPanel.add(sectionPanel("xinfo", xinfoView.component))
        outputPanel.add(sectionPanel("telescope", telescopeView.component))
        outputPanel.add(sectionPanelWithTitle(xTitleLabel, memoryView.component))

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

    private fun printResult(view: AnsiViewer, output: String?, error: String?) {
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

    private fun sectionPanel(title: String, content: JComponent): JComponent {
        val panel = BorderLayoutPanel()
        panel.addToTop(JLabel(title))
        panel.addToCenter(content)
        panel.minimumSize = Dimension(0, 0)
        return panel
    }

    private fun sectionPanelWithTitle(titleLabel: JLabel, content: JComponent): JComponent {
        val panel = BorderLayoutPanel()
        panel.addToTop(titleLabel)
        panel.addToCenter(content)
        panel.minimumSize = Dimension(0, 0)
        return panel
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

    private class AnsiViewer(project: Project) : Disposable {
        private val document = EditorFactory.getInstance().createDocument("")
        private val editor = EditorFactory.getInstance().createViewer(document, project)
        private val ansiDecoder = AnsiEscapeDecoder()

        val component: JComponent
            get() = editor.component

        init {
            val ex = editor as? EditorEx
            if (ex != null) {
                ex.scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
                ex.scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            }
            editor.settings.apply {
                isLineNumbersShown = false
                isLineMarkerAreaShown = false
                isFoldingOutlineShown = false
                isRightMarginShown = false
                isCaretRowShown = false
            }
        }

        fun clear() {
            setText("", isError = false)
        }

        fun setText(text: String, isError: Boolean) {
            val baseType = if (isError) com.intellij.execution.process.ProcessOutputTypes.STDERR else com.intellij.execution.process.ProcessOutputTypes.STDOUT
            val segments = ArrayList<Pair<String, com.intellij.openapi.util.Key<*>>>()
            ansiDecoder.escapeText(text, baseType) { chunk, attrs ->
                if (chunk.isNotEmpty()) {
                    segments.add(chunk to attrs)
                }
            }

            ApplicationManager.getApplication().invokeLater {
                ApplicationManager.getApplication().runWriteAction {
                    document.setText(segments.joinToString(separator = "") { it.first })
                    editor.markupModel.removeAllHighlighters()

                    var offset = 0
                    for ((chunk, attrs) in segments) {
                        val start = offset
                        offset += chunk.length
                        val type = com.intellij.execution.ui.ConsoleViewContentType.getConsoleViewType(attrs)
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
                updatePreferredHeight()
            }
        }

        private fun updatePreferredHeight() {
            val lineCount = document.lineCount.coerceAtLeast(1)
            val lineHeight = editor.lineHeight.coerceAtLeast(1)
            val preferredHeight = (lineCount * lineHeight) + 24
            val width = component.preferredSize.width
            val size = Dimension(width, preferredHeight)
            component.preferredSize = size
            component.minimumSize = size
            component.maximumSize = Dimension(Int.MAX_VALUE, preferredHeight)
        }

        override fun dispose() {
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }
}
