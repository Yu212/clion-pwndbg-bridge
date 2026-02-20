package com.yu212.pwnlion.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import javax.swing.JComponent

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
    private val socatPortSpinner = JBIntSpinner(
        settings.getSocatPort(),
        PwndbgSettingsService.MIN_SOCAT_PORT,
        PwndbgSettingsService.MAX_SOCAT_PORT,
        1
    )
    private val socatTtyPathField = JBTextField(settings.getSocatTtyPath(), 12)
    private val addressDefaultXFormatField = JBTextField(settings.getAddressDefaultXFormat(), 12)
    private val addressDefaultTelescopeCountSpinner = JBIntSpinner(
        settings.getAddressDefaultTelescopeCount(),
        PwndbgSettingsService.MIN_ADDRESS_TELESCOPE_COUNT,
        PwndbgSettingsService.MAX_ADDRESS_TELESCOPE_COUNT,
        1
    )
    private lateinit var enableSocatCell: Cell<JBCheckBox>
    private var panel: JComponent? = null

    override fun getDisplayName(): String = "PwnLion"

    override fun createComponent(): JComponent {
        if (panel == null) {
            panel = panel {
                row("Context history size") {
                    cell(contextHistorySpinner)
                }
                row("Command history size") {
                    cell(commandHistorySpinner)
                }
                row {
                    enableSocatCell = checkBox("Enable socat bridge")
                }
                indent {
                    row("Port") {
                        cell(socatPortSpinner)
                    }.enabledIf(enableSocatCell.selected)
                    row("TTY path") {
                        cell(socatTtyPathField)
                    }.enabledIf(enableSocatCell.selected)
                }
                row("Default x format") {
                    cell(addressDefaultXFormatField)
                }
                row("Default telescope count") {
                    cell(addressDefaultTelescopeCountSpinner)
                }
            }
            reset()
        }
        return panel as JComponent
    }

    override fun isModified(): Boolean {
        val socatEnabled = if (::enableSocatCell.isInitialized) enableSocatCell.component.isSelected else settings.isSocatEnabled()
        return contextHistorySpinner.number != settings.getContextHistoryMax() ||
                commandHistorySpinner.number != settings.getCommandHistoryMax() ||
                socatEnabled != settings.isSocatEnabled() ||
                socatPortSpinner.number != settings.getSocatPort() ||
                socatTtyPathField.text != settings.getSocatTtyPath() ||
                addressDefaultXFormatField.text != settings.getAddressDefaultXFormat() ||
                addressDefaultTelescopeCountSpinner.number != settings.getAddressDefaultTelescopeCount()
    }

    override fun apply() {
        settings.updateSettings(
            contextHistorySpinner.number,
            commandHistorySpinner.number,
            enableSocatCell.component.isSelected,
            socatPortSpinner.number,
            socatTtyPathField.text,
            addressDefaultXFormatField.text,
            addressDefaultTelescopeCountSpinner.number
        )
    }

    override fun reset() {
        contextHistorySpinner.number = settings.getContextHistoryMax()
        commandHistorySpinner.number = settings.getCommandHistoryMax()
        if (::enableSocatCell.isInitialized) {
            enableSocatCell.component.isSelected = settings.isSocatEnabled()
        }
        socatPortSpinner.number = settings.getSocatPort()
        socatTtyPathField.text = settings.getSocatTtyPath()
        addressDefaultXFormatField.text = settings.getAddressDefaultXFormat()
        addressDefaultTelescopeCountSpinner.number = settings.getAddressDefaultTelescopeCount()
    }
}
