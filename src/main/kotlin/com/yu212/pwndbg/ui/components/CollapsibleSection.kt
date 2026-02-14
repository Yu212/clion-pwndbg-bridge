package com.yu212.pwndbg.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.Project
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class CollapsibleSection(
    title: String,
    project: Project,
    startCollapsed: Boolean = false,
    titleComponent: JComponent? = null,
    extraActions: List<AnAction> = emptyList()
): BorderLayoutPanel(), Disposable {
    constructor(
        titleComponent: JComponent,
        project: Project,
        startCollapsed: Boolean = false,
        extraActions: List<AnAction> = emptyList()
    ): this("", project, startCollapsed, titleComponent, extraActions)

    private val header = JPanel(BorderLayout(6, 0))
    private val headerLabel = JLabel(title)
    private val viewer = AnsiTextViewer(project, adjustHeight = true)
    private var collapsed = startCollapsed
    private val toggleAction = object: AnAction("Collapse") {
        override fun actionPerformed(e: AnActionEvent) {
            setCollapsed(!collapsed)
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.icon = if (collapsed) AllIcons.General.ArrowRight else AllIcons.General.ArrowDown
        }
    }
    private val toolbar = createToolbar(extraActions)

    val component: JComponent
        get() = this

    init {
        header.isOpaque = false
        header.add(titleComponent ?: headerLabel, BorderLayout.CENTER)
        header.add(toolbar, BorderLayout.EAST)

        addToTop(header)
        addToCenter(viewer.component)
        viewer.component.isVisible = !collapsed
        updateSizeHints()
    }

    private fun createToolbar(extraActions: List<AnAction>): JComponent {
        val group = DefaultActionGroup()
        extraActions.forEach { group.add(it) }
        group.add(toggleAction)
        val toolbar = ActionManager.getInstance().createActionToolbar("PwndbgCollapse", group, true)
        (toolbar as? ActionToolbarImpl)?.setReservePlaceAutoPopupIcon(false)
        toolbar.targetComponent = this
        toolbar.component.isOpaque = false
        return toolbar.component
    }

    private fun setCollapsed(value: Boolean) {
        if (collapsed == value) return
        collapsed = value
        viewer.component.isVisible = !collapsed
        updateSizeHints()
        revalidate()
        repaint()
    }

    private fun updateSizeHints() {
        val headerHeight = header.preferredSize.height.coerceAtLeast(0)
        if (collapsed) {
            minimumSize = Dimension(0, headerHeight)
            preferredSize = Dimension(0, headerHeight)
            maximumSize = Dimension(Int.MAX_VALUE, headerHeight)
            return
        }

        val contentHeight = viewer.component.preferredSize.height.coerceAtLeast(0)
        val totalHeight = (headerHeight + contentHeight).coerceAtLeast(headerHeight)
        minimumSize = Dimension(0, headerHeight)
        preferredSize = Dimension(0, totalHeight)
        maximumSize = Dimension(Int.MAX_VALUE, totalHeight)
    }

    fun setText(text: String, isError: Boolean) {
        setSegments(AnsiTextViewer.decodeAnsi(text, isError))
    }

    fun setSegments(segments: List<AnsiTextViewer.AnsiSegment>) {
        viewer.setSegments(segments) {
            updateSizeHints()
            revalidate()
            repaint()
        }
    }

    fun setTextFontSize(size: Int?) {
        viewer.setFontSize(size)
        updateSizeHints()
        revalidate()
        repaint()
    }

    override fun dispose() {
        viewer.dispose()
    }
}
