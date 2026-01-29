package com.yu212.pwndbg.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.ui.CheckBoxList
import com.intellij.ui.CheckBoxListListener
import com.intellij.ui.ToolbarDecorator
import com.intellij.util.Function
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.ListSelectionModel

class PwndbgBreakpointsPanel(private val project: Project) : Disposable {
    private data class BreakpointEntry(
        val breakpoint: XBreakpoint<*>,
        val displayText: String
    )

    private val list: CheckBoxList<BreakpointEntry> = CheckBoxList()
    private val refreshScheduled = AtomicBoolean(false)
    private val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager

    private val breakpointListener = object : XBreakpointListener<XBreakpoint<*>> {
        override fun breakpointAdded(breakpoint: XBreakpoint<*>) {
            scheduleRefresh()
        }

        override fun breakpointRemoved(breakpoint: XBreakpoint<*>) {
            scheduleRefresh()
        }

        override fun breakpointChanged(breakpoint: XBreakpoint<*>) {
            scheduleRefresh()
        }

        override fun breakpointPresentationUpdated(
            breakpoint: XBreakpoint<*>,
            session: com.intellij.xdebugger.XDebugSession?
        ) {
            scheduleRefresh()
        }
    }

    private val rootPanel: JComponent

    init {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.setCheckBoxListListener(object : CheckBoxListListener {
            override fun checkBoxSelectionChanged(index: Int, value: Boolean) {
                val entry = list.getItemAt(index) ?: return
                entry.breakpoint.isEnabled = value
                scheduleRefresh()
            }
        })
        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(event: java.awt.event.MouseEvent) {
                if (event.clickCount == 2) {
                    openSelectedBreakpoint()
                }
            }
        })

        val decorator = ToolbarDecorator.createDecorator(list)
            .disableAddAction()
            .disableUpDownActions()
            .setEditActionName("Open")
            .setRemoveActionName("Delete")
            .setEditAction { openSelectedBreakpoint() }
            .setRemoveAction { removeSelectedBreakpoint() }
            .setEditActionUpdater { list.selectedIndex >= 0 }
            .setRemoveActionUpdater { list.selectedIndex >= 0 }
        rootPanel = decorator.createPanel()

        registerBreakpointListeners()
        refreshBreakpoints()
    }

    val component: JComponent
        get() = rootPanel

    private fun registerBreakpointListeners() {
        project.messageBus.connect(this).subscribe(XBreakpointListener.TOPIC, breakpointListener)
    }

    fun scheduleRefresh() {
        if (!refreshScheduled.compareAndSet(false, true)) return
        ApplicationManager.getApplication().invokeLater {
            refreshScheduled.set(false)
            refreshBreakpoints()
        }
    }

    private fun refreshBreakpoints() {
        val selected = getSelectedEntry()?.breakpoint
        val breakpoints = breakpointManager.allBreakpoints
            .toList()
            .sortedBy { describeBreakpoint(it) }
            .map { BreakpointEntry(it, describeBreakpoint(it)) }

        list.setItems(breakpoints, Function { it.displayText })
        for (entry in breakpoints) {
            list.setItemSelected(entry, entry.breakpoint.isEnabled)
        }

        if (selected != null) {
            var foundIndex = -1
            for (i in 0 until list.model.size) {
                if (list.getItemAt(i)?.breakpoint == selected) {
                    foundIndex = i
                    break
                }
            }
            if (foundIndex >= 0) {
                list.selectedIndex = foundIndex
            }
        }
    }

    private fun describeBreakpoint(breakpoint: XBreakpoint<*>): String {
        val lineBreakpoint = breakpoint as? XLineBreakpoint<*>
        if (lineBreakpoint != null) {
            val path = lineBreakpoint.presentableFilePath
                .takeIf { it.isNotBlank() }
                ?: lineBreakpoint.shortFilePath
            val line = lineBreakpoint.line + 1
            return "$path:$line"
        }

        val type = breakpoint.type
        val display = try {
            @Suppress("UNCHECKED_CAST")
            (type as XBreakpointType<XBreakpoint<*>, com.intellij.xdebugger.breakpoints.XBreakpointProperties<*>>)
                .getDisplayText(breakpoint)
        } catch (_: Exception) {
            type.title
        }
        return display
    }

    private fun openSelectedBreakpoint() {
        val entry = getSelectedEntry() ?: return
        openBreakpoint(entry.breakpoint)
    }

    private fun removeSelectedBreakpoint() {
        val entry = getSelectedEntry() ?: return
        breakpointManager.removeBreakpoint(entry.breakpoint)
    }

    private fun openBreakpoint(breakpoint: XBreakpoint<*>) {
        val position = breakpoint.sourcePosition
        if (position != null) {
            openPosition(position)
            return
        }

        val navigatable = breakpoint.navigatable
        if (navigatable != null && navigatable.canNavigate()) {
            navigatable.navigate(true)
            return
        }

        val lineBreakpoint = breakpoint as? XLineBreakpoint<*>
        if (lineBreakpoint != null) {
            val file = VirtualFileManager.getInstance().findFileByUrl(lineBreakpoint.fileUrl)
            if (file != null) {
                OpenFileDescriptor(project, file, lineBreakpoint.line, 0).navigate(true)
            }
        }
    }

    private fun openPosition(position: XSourcePosition) {
        position.createNavigatable(project).navigate(true)
    }

    private fun getSelectedEntry(): BreakpointEntry? {
        val index = list.selectedIndex
        if (index < 0) return null
        return list.getItemAt(index)
    }

    override fun dispose() {
        // listeners disposed via this Disposable
    }
}
