package com.yu212.pwndbg.ui

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.project.Project
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

class CollapsibleSection(
    title: String,
    project: Project,
    startCollapsed: Boolean = false,
    titleComponent: JComponent? = null,
    extraActions: List<AnAction> = emptyList()
) : BorderLayoutPanel(), Disposable {
    constructor(
        titleComponent: JComponent,
        project: Project,
        startCollapsed: Boolean = false,
        extraActions: List<AnAction> = emptyList()
    ) : this("", project, startCollapsed, titleComponent, extraActions)
    private val header = JPanel(BorderLayout(6, 0))
    private val headerLabel = JLabel(title)
    private val viewer = AnsiViewer(project)
    private var collapsed = startCollapsed
    private val toggleAction = object : AnAction("Collapse") {
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
        viewer.setText(text, isError) {
            updateSizeHints()
            revalidate()
            repaint()
        }
    }

    override fun dispose() {
        viewer.dispose()
    }
}

private class AnsiViewer(project: Project) : Disposable {
    private val document = EditorFactory.getInstance().createDocument("")
    private val editor = EditorFactory.getInstance().createViewer(document, project)
    private val ansiDecoder = AnsiEscapeDecoder()
    val component: JComponent = editor.component

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

    fun setText(text: String, isError: Boolean, onUiUpdated: (() -> Unit)? = null) {
        val segments = decodeAnsi(text, isError)
        val app = ApplicationManager.getApplication()
        app.invokeLater {
            app.runWriteAction {
                applySegments(segments)
            }
            updatePreferredHeight()
            onUiUpdated?.invoke()
        }
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
    }

    private fun decodeAnsi(text: String, isError: Boolean): List<Pair<String, com.intellij.openapi.util.Key<*>>> {
        val baseType = if (isError) ProcessOutputTypes.STDERR else ProcessOutputTypes.STDOUT
        val segments = ArrayList<Pair<String, com.intellij.openapi.util.Key<*>>>()
        ansiDecoder.escapeText(text, baseType) { chunk, attrs ->
            if (chunk.isNotEmpty()) {
                segments.add(chunk to attrs)
            }
        }
        return segments
    }

    private fun applySegments(segments: List<Pair<String, com.intellij.openapi.util.Key<*>>>) {
        document.setText(segments.joinToString(separator = "") { it.first })
        editor.markupModel.removeAllHighlighters()

        var offset = 0
        for ((chunk, attrs) in segments) {
            val start = offset
            offset += chunk.length
            val type = ConsoleViewContentType.getConsoleViewType(attrs)
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

    private fun updatePreferredHeight() {
        val lineCount = document.lineCount.coerceAtLeast(1)
        val lineHeight = editor.lineHeight.coerceAtLeast(1)
        val preferredHeight = lineCount * lineHeight + 24
        val width = component.preferredSize.width
        val size = Dimension(width, preferredHeight)
        component.preferredSize = size
        component.minimumSize = size
        component.maximumSize = Dimension(Int.MAX_VALUE, preferredHeight)
    }
}
