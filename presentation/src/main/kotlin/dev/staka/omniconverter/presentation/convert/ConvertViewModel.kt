package dev.staka.omniconverter.presentation.convert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.staka.omniconverter.application.EnqueueConversions
import dev.staka.omniconverter.application.GetAvailableTargets
import dev.staka.omniconverter.application.ObserveOutputDir
import dev.staka.omniconverter.application.RetainSharedFiles
import dev.staka.omniconverter.application.SetOutputDir
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    private val retainShared: RetainSharedFiles,
    observeOutputDir: ObserveOutputDir,
    private val setOutputDir: SetOutputDir,
) : ViewModel() {
    private val _state = MutableStateFlow(ConvertUiState())
    val state = _state.asStateFlow()

    /** null なら既定の Download/OmniConverter */
    val outputDir = observeOutputDir().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 選び直しても、まだ選べる形式なら選択を保つ */
    fun setFiles(files: List<FileRef>) {
        val targets = getTargets(files)
        _state.update { ConvertUiState(files, targets, it.selected?.takeIf { s -> s in targets }) }
    }

    fun addFiles(files: List<FileRef>) = setFiles((_state.value.files + files).distinctBy { it.uri })

    fun remove(file: FileRef) = setFiles(_state.value.files - file)

    /** 共有の読み取り権限はすぐ切れるので、先に手元へ写してから選択に入れる */
    fun receiveShared(files: List<FileRef>) {
        viewModelScope.launch { setFiles(retainShared(files)) }
    }

    fun select(format: Format) = _state.update { it.copy(selected = format) }

    fun clear() {
        _state.value = ConvertUiState()
    }

    fun chooseOutputDir(dir: FileRef?) {
        viewModelScope.launch { setOutputDir(dir) }
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
