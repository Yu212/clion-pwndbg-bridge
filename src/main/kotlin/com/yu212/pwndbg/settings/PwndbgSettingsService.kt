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
        var commandHistoryMax: Int = DEFAULT_HISTORY_MAX
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        normalize()
    }

    fun getContextHistoryMax(): Int = state.contextHistoryMax

    fun getCommandHistoryMax(): Int = state.commandHistoryMax

    fun updateHistoryLimits(contextMax: Int, commandMax: Int) {
        state.contextHistoryMax = contextMax
        state.commandHistoryMax = commandMax
        normalize()
    }

    private fun normalize() {
        state.contextHistoryMax = state.contextHistoryMax.coerceIn(MIN_HISTORY_MAX, MAX_HISTORY_MAX)
        state.commandHistoryMax = state.commandHistoryMax.coerceIn(MIN_HISTORY_MAX, MAX_HISTORY_MAX)
    }

    companion object {
        const val DEFAULT_HISTORY_MAX = 1000
        const val MIN_HISTORY_MAX = 1
        const val MAX_HISTORY_MAX = 100000
    }
}
