package dev.staka.omniconverter.presentation.convert

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Format
import dev.staka.omniconverter.domain.model.FormatCategory
import dev.staka.omniconverter.presentation.R

@Composable
fun ConvertScreen(
    state: ConvertUiState,
    onFilesPicked: (List<FileRef>) -> Unit,
    onSelect: (Format) -> Unit,
    onClear: () -> Unit,
    onConvert: () -> Unit,
    outputDir: FileRef? = null,
    onOutputDir: (FileRef?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onFilesPicked(uris.map { context.toFileRef(it) })
    }
    val pick = { picker.launch(arrayOf("*/*")) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onOutputDir(FileRef(uri.toString(), context.treeName(uri)))
    }

    if (state.files.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = pick, modifier = Modifier.height(96.dp).width(280.dp)) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.pick_files), style = MaterialTheme.typography.titleLarge)
            }
        }
        return
    }

    Column(modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.file_count, state.files.size),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClear) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.clear_selection))
                    }
                }
            }
            items(state.files, key = { it.uri }) { file ->
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            if (state.targets.isEmpty()) {
                item { Text(stringResource(R.string.no_targets), modifier = Modifier.padding(top = 24.dp)) }
            }
            state.targets.groupBy { it.category }.forEach { (category, formats) ->
                item(key = category) {
                    Text(
                        stringResource(category.label()),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        formats.forEach { format ->
                            FilterChip(
                                selected = format == state.selected,
                                onClick = { onSelect(format) },
                                label = { Text(format.label()) },
                            )
                        }
                    }
                }
            }
        }
        OutputDirRow(outputDir, onPick = { folderPicker.launch(null) }, onReset = { onOutputDir(null) })
        Button(
            onClick = onConvert,
            enabled = state.selected != null,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp).height(56.dp),
        ) {
            Text(
                state.selected?.let { stringResource(R.string.convert_to, it.label()) } ?: stringResource(R.string.tab_convert),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

fun Format.label() = id.uppercase()

fun FormatCategory.label() = when (this) {
    FormatCategory.VIDEO -> R.string.category_video
    FormatCategory.AUDIO -> R.string.category_audio
    FormatCategory.IMAGE -> R.string.category_image
    FormatCategory.DOCUMENT -> R.string.category_document
    FormatCategory.PDF -> R.string.category_pdf
    FormatCategory.ARCHIVE -> R.string.category_archive
}

/** バックグラウンドの変換で後から読めるよう、永続的な読み取り権限を取る */
internal fun Context.toFileRef(uri: Uri): FileRef {
    runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
    return FileRef(uri.toString(), name ?: uri.lastPathSegment ?: "file")
}

/** 選んだフォルダの表示名。取れなければ URI の末尾を使う */
private fun Context.treeName(tree: Uri): String {
    val doc = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree))
    return contentResolver.query(doc, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        ?: tree.lastPathSegment.orEmpty()
}

/** 他のアプリから共有されたファイル。読み取り権限は一時的なので、受け取ったらすぐ写す */
fun Context.sharedFiles(intent: Intent?): List<FileRef> {
    intent ?: return emptyList()
    val uris = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }
    return uris.map { uri ->
        val name = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        FileRef(uri.toString(), name ?: uri.lastPathSegment ?: "file")
    }
}

/** 出力先。変換ボタンのすぐ上に置き、何をどこへ出すかを一目で分かるようにする */
@Composable
private fun OutputDirRow(outputDir: FileRef?, onPick: () -> Unit, onReset: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPick, modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_folder), contentDescription = stringResource(R.string.output_dir))
            Spacer(Modifier.width(8.dp))
            Text(
                outputDir?.name ?: stringResource(R.string.default_output_dir),
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (outputDir != null) {
            IconButton(onClick = onReset) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.reset_output_dir))
            }
        }
    }
}
