package com.yu212.pwndbg.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "PwndbgSettings", storages = [Storage("pwndbg-settings.xml")])
class PwndbgSettingsService: PersistentStateComponent<PwndbgSettingsService.State> {
    data class State(
        var contextHistoryMax: Int = DEFAULT_HISTORY_MAX,
        var commandHistoryMax: Int = DEFAULT_HISTORY_MAX,
        var socatEnabled: Boolean = DEFAULT_SOCAT_ENABLED,
        var socatPort: Int = DEFAULT_SOCAT_PORT
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        normalize()
    }

    fun getContextHistoryMax(): Int = state.contextHistoryMax

    fun getCommandHistoryMax(): Int = state.commandHistoryMax

    fun isSocatEnabled(): Boolean = state.socatEnabled

    fun getSocatPort(): Int = state.socatPort

    fun updateSettings(contextHistoryMax: Int, commandHistoryMax: Int, socatEnabled: Boolean, socatPort: Int) {
        state.contextHistoryMax = contextHistoryMax
        state.commandHistoryMax = commandHistoryMax
        state.socatEnabled = socatEnabled
        state.socatPort = socatPort
        normalize()
    }

    private fun normalize() {
        state.contextHistoryMax = state.contextHistoryMax.coerceIn(MIN_HISTORY_MAX, MAX_HISTORY_MAX)
        state.commandHistoryMax = state.commandHistoryMax.coerceIn(MIN_HISTORY_MAX, MAX_HISTORY_MAX)
        state.socatPort = state.socatPort.coerceIn(MIN_SOCAT_PORT, MAX_SOCAT_PORT)
    }

    companion object {
        const val DEFAULT_HISTORY_MAX = 1000
        const val MIN_HISTORY_MAX = 1
        const val MAX_HISTORY_MAX = 100000
        const val DEFAULT_SOCAT_ENABLED = true
        const val DEFAULT_SOCAT_PORT = 0xdead
        const val MIN_SOCAT_PORT = 0
        const val MAX_SOCAT_PORT = 65535
    }
}
