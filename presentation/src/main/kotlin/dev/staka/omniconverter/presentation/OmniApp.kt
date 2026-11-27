package dev.staka.omniconverter.presentation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.staka.omniconverter.application.CancelJob
import dev.staka.omniconverter.application.ClearFinishedJobs
import dev.staka.omniconverter.application.DeletePreset
import dev.staka.omniconverter.application.EnqueueConversions
import dev.staka.omniconverter.application.GetAvailableTargets
import dev.staka.omniconverter.application.ListEngines
import dev.staka.omniconverter.application.ObserveJobs
import dev.staka.omniconverter.application.ObservePresets
import dev.staka.omniconverter.application.SavePreset
import dev.staka.omniconverter.presentation.command.CommandScreen
import dev.staka.omniconverter.presentation.command.CommandViewModel
import dev.staka.omniconverter.presentation.convert.ConvertScreen
import dev.staka.omniconverter.presentation.convert.ConvertViewModel
import dev.staka.omniconverter.presentation.queue.QueueScreen
import dev.staka.omniconverter.presentation.queue.QueueViewModel
import dev.staka.omniconverter.presentation.theme.OmniTheme

/** 画面が使うユースケース。合成ルートが組み立てて渡す。ADR-0010 */
class UseCases(
    val getTargets: GetAvailableTargets,
    val enqueue: EnqueueConversions,
    val observeJobs: ObserveJobs,
    val cancelJob: CancelJob,
    val clearFinished: ClearFinishedJobs,
    val listEngines: ListEngines,
    val observePresets: ObservePresets,
    val savePreset: SavePreset,
    val deletePreset: DeletePreset,
)

private enum class Tab(val label: Int, val icon: Int) {
    CONVERT(R.string.tab_convert, R.drawable.ic_convert),
    QUEUE(R.string.tab_queue, R.drawable.ic_queue),
    COMMAND(R.string.tab_command, R.drawable.ic_terminal),
}

@Composable
fun OmniApp(useCases: UseCases) {
    val convert = viewModel { ConvertViewModel(useCases.getTargets, useCases.enqueue) }
    val queue = viewModel { QueueViewModel(useCases.observeJobs, useCases.cancelJob, useCases.clearFinished) }
    val command = viewModel {
        CommandViewModel(useCases.listEngines, useCases.observePresets, useCases.savePreset, useCases.deletePreset, useCases.enqueue)
    }
    var tab by rememberSaveable { mutableIntStateOf(Tab.CONVERT.ordinal) }
    // 通知が拒否されても変換は進むので、結果は待たない
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    OmniTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t.ordinal,
                            onClick = { tab = t.ordinal },
                            icon = { Icon(painterResource(t.icon), contentDescription = null) },
                            label = { Text(stringResource(t.label)) },
                        )
                    }
                }
            },
        ) { padding ->
            val modifier = Modifier.padding(padding)
            when (Tab.entries[tab]) {
                Tab.CONVERT -> {
                    val state by convert.state.collectAsStateWithLifecycle()
                    ConvertScreen(
                        state = state,
                        onFilesPicked = convert::setFiles,
                        onSelect = convert::select,
                        onClear = convert::clear,
                        onConvert = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            convert.convert { tab = Tab.QUEUE.ordinal }
                        },
                        modifier = modifier,
                    )
                }
                Tab.QUEUE -> {
                    val jobs by queue.jobs.collectAsStateWithLifecycle()
                    QueueScreen(jobs, queue::cancel, queue::clearFinished, modifier)
                }
                Tab.COMMAND -> {
                    val state by command.state.collectAsStateWithLifecycle()
                    val presets by command.presets.collectAsStateWithLifecycle()
                    CommandScreen(
                        state = state,
                        presets = presets,
                        onEngine = command::selectEngine,
                        onText = command::setText,
                        onExtension = command::setExtension,
                        onFilesPicked = command::setFiles,
                        onApplyPreset = command::apply,
                        onSavePreset = command::save,
                        onDeletePreset = command::delete,
                        onRun = { command.run { tab = Tab.QUEUE.ordinal } },
                        modifier = modifier,
                    )
                }
            }
        }
    }
}
