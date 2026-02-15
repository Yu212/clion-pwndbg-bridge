package com.yu212.pwndbg.ui.panels.address

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.yu212.pwndbg.PwndbgService
import com.yu212.pwndbg.ui.components.AnsiTextViewer
import com.yu212.pwndbg.ui.components.CollapsibleSection
import com.yu212.pwndbg.ui.components.CommandHistoryField
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class AddressInspectionView(private val project: Project): Disposable {
    private val xFormatField = CommandHistoryField("16gx")
    private val xTitleLabel = JLabel("x/")
    private val xHeader = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
    private val xinfoView = CollapsibleSection("xinfo", project)
    private val telescopeTitleLabel = JLabel()
    private val telescopeDecreaseAction = object: AnAction("Decrease Count", null, AllIcons.General.Remove) {
        override fun actionPerformed(e: AnActionEvent) = updateTelescopeLines(-1)
    }
    private val telescopeIncreaseAction = object: AnAction("Increase Count", null, AllIcons.General.Add) {
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
