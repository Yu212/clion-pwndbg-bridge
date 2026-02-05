package com.yu212.pwndbg.ui.panels

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.*
import com.yu212.pwndbg.PwndbgService
import com.yu212.pwndbg.ui.PwndbgTabPanel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.*

class PwndbgBreakpointsPanel(private val project: Project) : PwndbgTabPanel {
    override val id: String = "breakpoints"
    override val title: String = "Breakpoints"
    private val model = DefaultListModel<BreakpointEntry>()
    private val list = object : JBList<BreakpointEntry>(model) {
        override fun processMouseEvent(e: MouseEvent) {
            val hit = hitTest(e)
            if (hit != null) {
                if (hit.entry is BreakpointEntry.Header) {
                    e.consume()
                    return
                }
                if (hit.inCheckbox && SwingUtilities.isLeftMouseButton(e)) {
                    if (e.id == MouseEvent.MOUSE_PRESSED) {
                        toggleEntry(hit.entry, hit.index)
                    }
                    e.consume()
                    return
                }
                if (e.id == MouseEvent.MOUSE_CLICKED &&
                    e.clickCount == 2 &&
                    SwingUtilities.isLeftMouseButton(e) &&
                    hit.entry is BreakpointEntry.Clion
                ) {
                    navigateToBreakpoint(hit.entry.breakpoint)
                    e.consume()
                    return
                }
            }
            super.processMouseEvent(e)
        }
    }
    private val rootPanel = JPanel(BorderLayout())
    private val checkboxClickWidth = JBUI.scale(22)

    init {
        list.cellRenderer = BreakpointCellRenderer()
        list.selectionModel = BreakpointSelectionModel(model)
        list.selectionMode = javax.swing.ListSelectionModel.SINGLE_SELECTION
        list.fixedCellHeight = -1
        list.setEmptyText("No breakpoints")

        installKeyboardHandling()
        installBreakpointListener()

        val toolbar = ToolbarDecorator.createDecorator(list)
            .setAddAction(null)
            .setEditAction(null)
            .setMoveUpAction(null)
            .setMoveDownAction(null)
            .setRemoveAction { deleteSelected() }
            .addExtraAction(object : com.intellij.openapi.actionSystem.AnAction(
                "Refresh",
                "Refresh breakpoints",
                AllIcons.Actions.Refresh
            ) {
                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                    refreshAll()
                }
            })
            .createPanel()

        rootPanel.add(toolbar, BorderLayout.CENTER)
        refreshAll()
    }

    override val component: JComponent
        get() = rootPanel

    private fun installBreakpointListener() {
        val manager = XDebuggerManager.getInstance(project).breakpointManager
        val listener = object : XBreakpointListener<XBreakpoint<*>> {
            override fun breakpointAdded(breakpoint: XBreakpoint<*>) = refreshAll()
            override fun breakpointRemoved(breakpoint: XBreakpoint<*>) = refreshAll()
            override fun breakpointChanged(breakpoint: XBreakpoint<*>) = refreshAll()
        }

        for (type in XBreakpointType.EXTENSION_POINT_NAME.extensionList) {
            addBreakpointListener(manager, type, listener)
        }
    }

    private fun <B : XBreakpoint<P>, P : XBreakpointProperties<*>> addBreakpointListener(
        manager: com.intellij.xdebugger.breakpoints.XBreakpointManager,
        type: XBreakpointType<B, P>,
        listener: XBreakpointListener<XBreakpoint<*>>
    ) {
        @Suppress("UNCHECKED_CAST")
        manager.addBreakpointListener(type, listener as XBreakpointListener<B>, this)
    }

    private fun installKeyboardHandling() {
        val inputMap = list.getInputMap(JComponent.WHEN_FOCUSED)
        val ancestorInputMap = list.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        val actionMap = list.actionMap

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "pwndbg.toggle")
        actionMap.put("pwndbg.toggle", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val index = list.selectedIndex
                if (index < 0) return
                val entry = model.getElementAt(index)
                if (entry !is BreakpointEntry.Header) {
                    toggleEntry(entry, index)
                }
            }
        })

        val nextAction = object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                moveSelection(1)
            }
        }
        val prevAction = object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                moveSelection(-1)
            }
        }

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "pwndbg.next")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "pwndbg.prev")
        ancestorInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "pwndbg.next")
        ancestorInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "pwndbg.prev")
        actionMap.put("pwndbg.next", nextAction)
        actionMap.put("pwndbg.prev", prevAction)
        actionMap.put("selectNextRow", nextAction)
        actionMap.put("selectPreviousRow", prevAction)
    }

    private fun moveSelection(direction: Int) {
        if (model.isEmpty) return
        val start = list.selectedIndex
        val index = if (start < 0) {
            if (direction > 0) firstSelectableIndex() else lastSelectableIndex()
        } else {
            nextSelectableIndex(start, direction)
        }
        if (index >= 0) {
            list.selectedIndex = index
            list.ensureIndexIsVisible(index)
        }
    }

    private fun firstSelectableIndex(): Int {
        for (i in 0 until model.size()) {
            if (model.getElementAt(i) !is BreakpointEntry.Header) return i
        }
        return -1
    }

    private fun lastSelectableIndex(): Int {
        for (i in model.size() - 1 downTo 0) {
            if (model.getElementAt(i) !is BreakpointEntry.Header) return i
        }
        return -1
    }

    private fun nextSelectableIndex(from: Int, direction: Int): Int {
        var i = from + direction
        while (i in 0 until model.size()) {
            if (model.getElementAt(i) !is BreakpointEntry.Header) return i
            i += direction
        }
        return -1
    }

    private fun toggleEntry(entry: BreakpointEntry, index: Int) {
        when (entry) {
            is BreakpointEntry.Clion -> {
                entry.breakpoint.isEnabled = !entry.breakpoint.isEnabled
                list.repaint(list.getCellBounds(index, index))
            }
            is BreakpointEntry.Gdb -> {
                val command = if (entry.enabled) "disable ${entry.id}" else "enable ${entry.id}"
                project.getService(PwndbgService::class.java)
                    .executeCommandCapture(command) { _, _ ->
                        ApplicationManager.getApplication().invokeLater {
                            model.set(index, entry.copy(enabled = !entry.enabled))
                        }
                    }
            }
            is BreakpointEntry.Header -> Unit
        }
    }

    private fun deleteSelected() {
        val index = list.selectedIndex
        if (index < 0) return
        when (val entry = model.getElementAt(index)) {
            is BreakpointEntry.Clion -> {
                XDebuggerManager.getInstance(project).breakpointManager.removeBreakpoint(entry.breakpoint)
                refreshAll()
            }
            is BreakpointEntry.Gdb -> {
                project.getService(PwndbgService::class.java)
                    .executeCommandCapture("delete ${entry.id}") { _, _ ->
                        ApplicationManager.getApplication().invokeLater {
                            model.remove(index)
                        }
                    }
            }
            is BreakpointEntry.Header -> Unit
        }
    }

    private fun navigateToBreakpoint(breakpoint: XBreakpoint<*>) {
        val lineBreakpoint = breakpoint as? XLineBreakpoint<*> ?: return
        val file = VirtualFileManager.getInstance().findFileByUrl(lineBreakpoint.fileUrl) ?: return
        OpenFileDescriptor(project, file, lineBreakpoint.line, 0).navigate(true)
    }

    fun refreshAll() {
        ApplicationManager.getApplication().invokeLater {
            val clionBreakpoints = XDebuggerManager.getInstance(project)
                .breakpointManager
                .allBreakpoints
                .toList()

            model.clear()
            model.addElement(BreakpointEntry.Header("CLion breakpoints"))
            for (breakpoint in clionBreakpoints) {
                model.addElement(BreakpointEntry.Clion(breakpoint))
            }

            model.addElement(BreakpointEntry.Header("GDB breakpoints"))
            refreshGdbBreakpoints()
            ensureValidSelection()
        }
    }

    private fun refreshGdbBreakpoints() {
        project.getService(PwndbgService::class.java)
            .executeCommandCapture("info breakpoints") { output, error ->
                val gdbBreakpoints = if (error.isNullOrBlank()) {
                    parseGdbBreakpoints(output.orEmpty())
                } else {
                    emptyList()
                }

                ApplicationManager.getApplication().invokeLater {
                    while (model.size() > 0 && model.lastElement() !is BreakpointEntry.Header) {
                        model.remove(model.size() - 1)
                    }
                    for (bp in gdbBreakpoints) {
                        model.addElement(BreakpointEntry.Gdb(bp.id, bp.summary, bp.enabled))
                    }
                    ensureValidSelection()
                }
            }
    }

    private fun parseGdbBreakpoints(output: String): List<GdbBreakpoint> {
        val results = ArrayList<GdbBreakpoint>()
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("Num") || line.startsWith("Breakpoint")) return@forEach
            if (line.startsWith("No breakpoints")) return@forEach
            val match = GDB_LINE_REGEX.matchEntire(line)
            if (match != null) {
                val id = match.groupValues[1]
                val enabled = match.groupValues[2].lowercase() == "y"
                val summary = match.groupValues[3].ifBlank { line }
                results.add(GdbBreakpoint(id, summary, enabled))
                return@forEach
            }

            val tokens = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.size < 4) return@forEach
            val id = tokens[0]
            val enabled = tokens[3].lowercase() == "y"
            val summary = if (tokens.size > 4) tokens.drop(4).joinToString(" ") else line
            results.add(GdbBreakpoint(id, summary, enabled))
        }
        return results
    }

    override fun dispose() = Unit

    private fun ensureValidSelection() {
        val index = list.selectedIndex
        if (index >= 0 && model.getElementAt(index) !is BreakpointEntry.Header) return
        val next = firstSelectableIndex()
        if (next >= 0) {
            list.selectedIndex = next
        } else {
            list.clearSelection()
        }
    }

    private fun hitTest(event: MouseEvent): HitTest? {
        val index = list.locationToIndex(event.point)
        if (index < 0) return null
        val bounds = list.getCellBounds(index, index) ?: return null
        if (!bounds.contains(event.point)) return null
        val entry = model.getElementAt(index)
        val clickX = event.x - bounds.x
        return HitTest(index, entry, clickX <= checkboxClickWidth)
    }

    private data class HitTest(
        val index: Int,
        val entry: BreakpointEntry,
        val inCheckbox: Boolean
    )

    private data class GdbBreakpoint(val id: String, val summary: String, val enabled: Boolean)

    private sealed class BreakpointEntry {
        data class Header(val title: String) : BreakpointEntry()
        data class Clion(val breakpoint: XBreakpoint<*>) : BreakpointEntry()
        data class Gdb(val id: String, val summary: String, val enabled: Boolean) : BreakpointEntry()
    }

    private class BreakpointSelectionModel(
        private val model: DefaultListModel<BreakpointEntry>
    ) : javax.swing.DefaultListSelectionModel() {
        override fun setSelectionInterval(index0: Int, index1: Int) {
            if (index0 < 0 || index0 >= model.size()) return
            if (model.getElementAt(index0) is BreakpointEntry.Header) return
            super.setSelectionInterval(index0, index1)
        }

        override fun addSelectionInterval(index0: Int, index1: Int) {
            if (index0 < 0 || index0 >= model.size()) return
            if (model.getElementAt(index0) is BreakpointEntry.Header) return
            super.addSelectionInterval(index0, index1)
        }
    }

    private class BreakpointCellRenderer : JPanel(BorderLayout()), ListCellRenderer<BreakpointEntry> {
        private val checkBox = JCheckBox()
        private val label = JLabel()

        init {
            isOpaque = true
            checkBox.isOpaque = false
            checkBox.isFocusable = false
            add(checkBox, BorderLayout.WEST)
            add(label, BorderLayout.CENTER)
            border = JBUI.Borders.empty(2, 6)
        }

        override fun getListCellRendererComponent(
            list: JList<out BreakpointEntry>,
            value: BreakpointEntry,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            when (value) {
                is BreakpointEntry.Header -> {
                    checkBox.isVisible = false
                    label.text = value.title
                    label.font = label.font.deriveFont(Font.BOLD)
                }
                is BreakpointEntry.Clion -> {
                    checkBox.isVisible = true
                    checkBox.isSelected = value.breakpoint.isEnabled
                    label.text = buildClionLabel(value.breakpoint)
                    label.font = list.font
                }
                is BreakpointEntry.Gdb -> {
                    checkBox.isVisible = true
                    checkBox.isSelected = value.enabled
                    label.text = value.summary
                    label.font = list.font
                }
            }

            val background = if (isSelected && value !is BreakpointEntry.Header) {
                list.selectionBackground
            } else {
                list.background
            }
            val foreground = if (isSelected && value !is BreakpointEntry.Header) {
                list.selectionForeground
            } else {
                list.foreground
            }

            this.background = background
            label.foreground = foreground
            return this
        }

        private fun buildClionLabel(breakpoint: XBreakpoint<*>): String {
            val lineBreakpoint = breakpoint as? XLineBreakpoint<*>
            if (lineBreakpoint != null) {
                val file = VirtualFileManager.getInstance().findFileByUrl(lineBreakpoint.fileUrl)
                val fileName = file?.name ?: lineBreakpoint.fileUrl.substringAfterLast('/')
                return "$fileName:${lineBreakpoint.line + 1}"
            }
            return breakpoint.type.title
        }
    }

    companion object {
        private val GDB_LINE_REGEX = Regex("^\\s*(\\d+)\\s+\\S+\\s+\\S+\\s+([yYnN])\\s+\\S+\\s*(.*)$")
    }
}
