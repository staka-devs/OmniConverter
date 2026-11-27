package dev.staka.omniconverter.presentation.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.staka.omniconverter.application.CancelJob
import dev.staka.omniconverter.application.ClearFinishedJobs
import dev.staka.omniconverter.application.ObserveJobs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QueueViewModel(
    observeJobs: ObserveJobs,
    private val cancelJob: CancelJob,
    private val clearFinished: ClearFinishedJobs,
) : ViewModel() {
    val jobs = observeJobs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(id: String) {
        viewModelScope.launch { cancelJob(id) }
    }

    fun clearFinished() {
        viewModelScope.launch { clearFinished(jobs.value) }
    }
}
