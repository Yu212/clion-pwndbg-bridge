package com.yu212.pwndbg.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBIntSpinner
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class PwndbgSettingsConfigurable: Configurable {
    private val settings = ApplicationManager.getApplication().getService(PwndbgSettingsService::class.java)
    private val contextHistorySpinner = JBIntSpinner(
        settings.getContextHistoryMax(),
        PwndbgSettingsService.MIN_HISTORY_MAX,
        PwndbgSettingsService.MAX_HISTORY_MAX,
        1
    )
    private val commandHistorySpinner = JBIntSpinner(
        settings.getCommandHistoryMax(),
        PwndbgSettingsService.MIN_HISTORY_MAX,
        PwndbgSettingsService.MAX_HISTORY_MAX,
        1
    )
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Pwndbg Bridge"

    override fun createComponent(): JComponent {
        if (panel == null) {
            val formPanel = FormBuilder.createFormBuilder()
                    .addLabeledComponent("Context history size", contextHistorySpinner)
                    .addLabeledComponent("Command history size", commandHistorySpinner)
                    .panel
            panel = JPanel(BorderLayout()).apply {
                add(formPanel, BorderLayout.NORTH)
            }
        }
        return panel as JPanel
    }

    override fun isModified(): Boolean {
        return contextHistorySpinner.number != settings.getContextHistoryMax() ||
                commandHistorySpinner.number != settings.getCommandHistoryMax()
    }

    override fun apply() {
        settings.updateHistoryLimits(
            contextHistorySpinner.number,
            commandHistorySpinner.number
        )
    }

    override fun reset() {
        contextHistorySpinner.number = settings.getContextHistoryMax()
        commandHistorySpinner.number = settings.getCommandHistoryMax()
    }
}
