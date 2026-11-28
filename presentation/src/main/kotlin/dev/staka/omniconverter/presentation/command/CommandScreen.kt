package dev.staka.omniconverter.presentation.command

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.staka.omniconverter.application.CommandError
import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Preset
import dev.staka.omniconverter.presentation.R
import dev.staka.omniconverter.presentation.convert.toFileRef

/** 入力欄の見本。説明の代わりに、そのまま動く例を薄く出す */
private val examples = mapOf(
    "ffmpeg" to "-i {input} -vn -c:a libmp3lame -q:a 2 {output}",
    "imagemagick" to "{input} -resize 50% {output}",
    "libheif" to "heif-enc -q 50 {input} -o {output}",
    "libarchive" to "-c -f {output} --format zip @{input}",
    "mupdf" to "convert -o {output} {input}",
    "pandoc" to "-f markdown -t docx -o {output} {input}",
)

@Composable
fun CommandScreen(
    state: CommandUiState,
    presets: List<Preset>,
    onEngine: (EngineId) -> Unit,
    onText: (String) -> Unit,
    onExtension: (String) -> Unit,
    onFilesPicked: (List<FileRef>) -> Unit,
    onApplyPreset: (Preset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (Preset) -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onFilesPicked(uris.map { context.toFileRef(it) })
    }
    var naming by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.engines.forEach { engine ->
                    FilterChip(selected = engine == state.engine, onClick = { onEngine(engine) }, label = { Text(engine.value) })
                }
            }
            OutlinedTextField(
                value = state.text,
                onValueChange = onText,
                label = { Text(stringResource(R.string.command_label)) },
                placeholder = { Text(examples[state.engine?.value].orEmpty(), fontFamily = FontFamily.Monospace) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                isError = state.error != null && state.error != CommandError.InvalidExtension,
                supportingText = state.error?.takeIf { it != CommandError.InvalidExtension }?.let { { Text(stringResource(it.message())) } },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.extension,
                onValueChange = onExtension,
                label = { Text(stringResource(R.string.extension_label)) },
                singleLine = true,
                isError = state.error == CommandError.InvalidExtension,
                supportingText = (state.error == CommandError.InvalidExtension).takeIf { it }?.let { { Text(stringResource(R.string.error_extension)) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.width(200.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                presets.forEach { preset ->
                    InputChip(
                        selected = false,
                        onClick = { onApplyPreset(preset) },
                        label = { Text(preset.name) },
                        trailingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.delete_preset, preset.name),
                                modifier = Modifier.size(18.dp).clickable { onDeletePreset(preset) },
                            )
                        },
                    )
                }
                TextButton(onClick = { naming = true }, enabled = state.text.isNotBlank()) {
                    Text(stringResource(R.string.save_preset))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.height(56.dp)) {
                Text(
                    if (state.files.isEmpty()) stringResource(R.string.pick_files)
                    else stringResource(R.string.file_count, state.files.size),
                )
            }
            Button(
                onClick = onRun,
                enabled = state.engine != null && state.files.isNotEmpty() && state.text.isNotBlank(),
                modifier = Modifier.weight(1f).padding(start = 12.dp).height(56.dp),
            ) {
                Text(stringResource(R.string.run), style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (naming) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.save_preset)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.preset_name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { onSavePreset(name); naming = false }) { Text(stringResource(R.string.save_preset)) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun CommandError.message() = when (this) {
    CommandError.Empty -> R.string.error_empty
    CommandError.UnclosedQuote -> R.string.error_quote
    CommandError.MissingInput -> R.string.error_input
    CommandError.MissingOutput -> R.string.error_output
    CommandError.InvalidExtension -> R.string.error_extension
}
