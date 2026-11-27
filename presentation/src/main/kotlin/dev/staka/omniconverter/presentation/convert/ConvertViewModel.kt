package dev.staka.omniconverter.presentation.convert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.staka.omniconverter.application.EnqueueConversions
import dev.staka.omniconverter.application.GetAvailableTargets
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConvertUiState(
    val files: List<FileRef> = emptyList(),
    val targets: List<Format> = emptyList(),
    val selected: Format? = null,
)

class ConvertViewModel(
    private val getTargets: GetAvailableTargets,
    private val enqueue: EnqueueConversions,
) : ViewModel() {
    private val _state = MutableStateFlow(ConvertUiState())
    val state = _state.asStateFlow()

    fun setFiles(files: List<FileRef>) {
        _state.value = ConvertUiState(files, getTargets(files))
    }

    fun select(format: Format) = _state.update { it.copy(selected = format) }

    fun clear() {
        _state.value = ConvertUiState()
    }

    fun convert(onEnqueued: () -> Unit) {
        val s = _state.value
        val target = s.selected ?: return
        viewModelScope.launch {
            enqueue(s.files, target)
            clear()
            onEnqueued()
        }
    }
}
