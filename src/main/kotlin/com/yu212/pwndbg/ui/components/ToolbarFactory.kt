package com.yu212.pwndbg.ui.components

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import javax.swing.JComponent

object ToolbarFactory {
    fun create(
        place: String,
        targetComponent: JComponent,
        actions: List<AnAction>
    ): ActionToolbar {
        val group = DefaultActionGroup()
        actions.forEach(group::add)
        val toolbar = ActionManager.getInstance().createActionToolbar(place, group, true)
        (toolbar as? ActionToolbarImpl)?.setReservePlaceAutoPopupIcon(false)
        toolbar.targetComponent = targetComponent
        toolbar.component.isOpaque = true
        return toolbar
    }
}
