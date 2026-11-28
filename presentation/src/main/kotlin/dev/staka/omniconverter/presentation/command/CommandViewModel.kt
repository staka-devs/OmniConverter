package dev.staka.omniconverter.presentation.command

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.staka.omniconverter.application.CommandError
import dev.staka.omniconverter.application.CommandLine
import dev.staka.omniconverter.application.DeletePreset
import dev.staka.omniconverter.application.EnqueueConversions
import dev.staka.omniconverter.application.ListEngines
import dev.staka.omniconverter.application.ObservePresets
import dev.staka.omniconverter.application.Result
import dev.staka.omniconverter.application.SavePreset
import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Preset
import dev.staka.omniconverter.domain.model.RawCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommandUiState(
    val engines: List<EngineId> = emptyList(),
    val engine: EngineId? = null,
    val text: String = "",
    val extension: String = "",
    val files: List<FileRef> = emptyList(),
    val error: CommandError? = null,
)

class CommandViewModel(
    listEngines: ListEngines,
    observePresets: ObservePresets,
    private val savePreset: SavePreset,
    private val deletePreset: DeletePreset,
    private val enqueue: EnqueueConversions,
) : ViewModel() {
    private val _state = MutableStateFlow(CommandUiState(engines = listEngines()).let { it.copy(engine = it.engines.firstOrNull()) })
    val state = _state.asStateFlow()
    val presets = observePresets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectEngine(engine: EngineId) = _state.update { it.copy(engine = engine, error = null) }
    fun setText(text: String) = _state.update { it.copy(text = text, error = null) }
    fun setExtension(extension: String) = _state.update { it.copy(extension = extension, error = null) }
    fun setFiles(files: List<FileRef>) = _state.update { it.copy(files = files) }

    fun apply(preset: Preset) = _state.update {
        it.copy(
            engine = preset.command.engine,
            text = CommandLine.join(preset.command.args),
            extension = preset.command.outputExtension,
            error = null,
        )
    }

    fun save(name: String) {
        val command = build() ?: return
        viewModelScope.launch { savePreset(name, command) }
    }

    fun delete(preset: Preset) {
        viewModelScope.launch { deletePreset(preset.id) }
    }

    fun run(onEnqueued: () -> Unit) {
        val files = _state.value.files.takeIf { it.isNotEmpty() } ?: return
        val command = build() ?: return
        viewModelScope.launch {
            enqueue(files, command)
            _state.update { it.copy(files = emptyList()) }
            onEnqueued()
        }
    }

    /** 入力を検証し、だめならエラーを画面に出す */
    private fun build(): RawCommand? {
        val s = _state.value
        val engine = s.engine ?: return null
        return when (val result = CommandLine.build(engine, s.text, s.extension)) {
            is Result.Ok -> result.value
            is Result.Err -> {
                _state.update { it.copy(error = result.error) }
                null
            }
        }
    }
}
