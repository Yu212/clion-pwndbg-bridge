package com.yu212.pwndbg.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.components.BorderLayoutPanel
import com.yu212.pwndbg.PwndbgService
import java.awt.BorderLayout
import javax.swing.*

class PwndbgMapsPanel(private val project: Project) : Disposable {
    private val vmmapView = CollapsibleSection("vmmap", project)
    private val checksecView = CollapsibleSection("checksec", project)
    private val gotView = CollapsibleSection("got", project)
    private val pltView = CollapsibleSection("plt", project)
    private val rootPanel = BorderLayoutPanel()
    private val outputPanel = JPanel()

    init {
        val toolbar = JPanel(BorderLayout(8, 0))
        val refreshButton = JButton("Refresh")
        toolbar.add(JLabel("Maps / GOT / PLT"), BorderLayout.WEST)
        toolbar.add(refreshButton, BorderLayout.EAST)

        outputPanel.layout = BoxLayout(outputPanel, BoxLayout.Y_AXIS)
        outputPanel.add(checksecView.component)
        outputPanel.add(vmmapView.component)
        outputPanel.add(gotView.component)
        outputPanel.add(pltView.component)

        rootPanel.addToTop(toolbar)
        rootPanel.addToCenter(JBScrollPane(outputPanel))

        refreshButton.addActionListener { refreshAll() }
    }

    val component: JComponent
        get() = rootPanel

    fun refreshAll() {
        val service = project.getService(PwndbgService::class.java)
        service.executeCommandCapture("checksec") { output, error ->
            printResult(checksecView, output, error)
            service.executeCommandCapture("vmmap") { output2, error2 ->
                printResult(vmmapView, output2, error2)
                service.executeCommandCapture("got -r") { output3, error3 ->
                    printResult(gotView, output3, error3)
                    service.executeCommandCapture("plt") { output4, error4 ->
                        printResult(pltView, output4, error4)
                    }
                }
            }
        }
    }

    private fun printResult(view: CollapsibleSection, output: String?, error: String?) {
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

    override fun dispose() {
        checksecView.dispose()
        vmmapView.dispose()
        gotView.dispose()
        pltView.dispose()
    }

}
