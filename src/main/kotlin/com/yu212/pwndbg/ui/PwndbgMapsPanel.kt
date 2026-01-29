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
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class PwndbgMapsPanel(private val project: Project) : Disposable {
    private val vmmapView = AnsiViewer(project)
    private val checksecView = AnsiViewer(project)
    private val gotView = AnsiViewer(project)
    private val pltView = AnsiViewer(project)
    private val rootPanel = BorderLayoutPanel()
    private val outputPanel = JPanel()

    init {
        val toolbar = JPanel(BorderLayout(8, 0))
        val refreshButton = JButton("Refresh")
        toolbar.add(JLabel("Maps / GOT / PLT"), BorderLayout.WEST)
        toolbar.add(refreshButton, BorderLayout.EAST)

        outputPanel.layout = BoxLayout(outputPanel, BoxLayout.Y_AXIS)
        outputPanel.add(sectionPanel("checksec", checksecView.component))
        outputPanel.add(sectionPanel("vmmap", vmmapView.component))
        outputPanel.add(sectionPanel("got", gotView.component))
        outputPanel.add(sectionPanel("plt", pltView.component))

        rootPanel.addToTop(toolbar)
        rootPanel.addToCenter(JBScrollPane(outputPanel))

        refreshButton.addActionListener { refreshAll() }
    }

    val component: JComponent
        get() = rootPanel

    fun refreshAll() {
        checksecView.clear()
        vmmapView.clear()
        gotView.clear()
        pltView.clear()

        val service = project.getService(PwndbgService::class.java)
        service.executeCommandCapture("checksec") { output, error ->
            printResult(checksecView, output, error)
            service.executeCommandCapture("vmmap") { output2, error2 ->
                printResult(vmmapView, output2, error2)
                service.executeCommandCapture("got") { output3, error3 ->
                    printResult(gotView, output3, error3)
                    service.executeCommandCapture("plt") { output4, error4 ->
                        printResult(pltView, output4, error4)
                    }
                }
            }
        }
    }

    private fun printResult(view: AnsiViewer, output: String?, error: String?) {
        if (!error.isNullOrBlank()) {
            view.setText(error.trimEnd('\n', '\r'), isError = true)
            outputPanel.revalidate()
            outputPanel.repaint()
            return
        }
        if (!output.isNullOrBlank()) {
            view.setText(output.trimEnd('\n', '\r'), isError = false)
            outputPanel.revalidate()
            outputPanel.repaint()
        }
    }

    private fun sectionPanel(title: String, content: JComponent): JComponent {
        val panel = BorderLayoutPanel()
        panel.addToTop(JLabel(title))
        panel.addToCenter(content)
        panel.minimumSize = Dimension(0, 0)
        return panel
    }

    override fun dispose() {
        checksecView.dispose()
        vmmapView.dispose()
        gotView.dispose()
        pltView.dispose()
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
                ex.scrollPane.verticalScrollBarPolicy = javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
                ex.scrollPane.horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
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
