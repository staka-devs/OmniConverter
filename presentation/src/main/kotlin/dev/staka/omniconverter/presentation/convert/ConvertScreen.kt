package dev.staka.omniconverter.presentation.convert

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Format
import dev.staka.omniconverter.domain.model.FormatCategory
import dev.staka.omniconverter.domain.model.Formats
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
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onOutputDir(FileRef(uri.toString(), context.treeName(uri)))
    }
    val outputRow = @Composable {
        OutputDirRow(outputDir, onPick = { folderPicker.launch(null) }, onReset = { onOutputDir(null) })
    }

    if (state.files.isEmpty()) {
        PickFiles(
            onPickAny = { picker.launch(arrayOf("*/*")) },
            onPickCategory = { picker.launch(it.mimeTypes().toTypedArray()) },
            outputRow = outputRow,
            modifier = modifier,
        )
    } else {
        ChooseTarget(state, onSelect, onClear, onConvert, outputRow, modifier)
    }
}

@Composable
private fun PickFiles(
    onPickAny: () -> Unit,
    onPickCategory: (FormatCategory) -> Unit,
    outputRow: @Composable () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            stringResource(R.string.app_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 20.dp),
        )
        PickAnyCard(onPickAny)
        Spacer(Modifier.height(12.dp))
        FormatCategory.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { category -> CategoryTile(category, { onPickCategory(category) }, Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        outputRow()
    }
}

@Composable
private fun PickAnyCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().height(168.dp),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.pick_files), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun CategoryTile(category: FormatCategory, onClick: () -> Unit, modifier: Modifier) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            CategoryBadge(category)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(category.label()), style = MaterialTheme.typography.titleMedium)
            Text(
                category.sampleFormats(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategoryBadge(category: FormatCategory, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(category.icon()),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

@Composable
private fun ChooseTarget(
    state: ConvertUiState,
    onSelect: (Format) -> Unit,
    onClear: () -> Unit,
    onConvert: () -> Unit,
    outputRow: @Composable () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "files") { SelectedFiles(state.files, onClear) }
            if (state.targets.isEmpty()) {
                item { Text(stringResource(R.string.no_targets), modifier = Modifier.padding(8.dp)) }
            }
            state.targets.groupBy { it.category }.forEach { (category, formats) ->
                item(key = category) { TargetGroup(category, formats, state.selected, onSelect) }
            }
        }
        Surface(tonalElevation = 3.dp, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                outputRow()
                ConvertButton(state.selected, onConvert)
            }
        }
    }
}

@Composable
private fun ConvertButton(selected: Format?, onConvert: () -> Unit) {
    Button(
        onClick = onConvert,
        enabled = selected != null,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp).height(56.dp),
    ) {
        Icon(painterResource(R.drawable.ic_convert), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            selected?.let { stringResource(R.string.convert_to, it.label()) } ?: stringResource(R.string.tab_convert),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SelectedFiles(files: List<FileRef>, onClear: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.file_count, files.size),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClear) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.clear_selection))
                }
            }
            files.take(VISIBLE_FILES).forEach { SelectedFileRow(it) }
            if (files.size > VISIBLE_FILES) {
                Text(
                    stringResource(R.string.more_files, files.size - VISIBLE_FILES),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 32.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SelectedFileRow(file: FileRef) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(Formats.detect(file.name)?.category?.icon() ?: R.drawable.ic_file),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            file.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

@Composable
private fun TargetGroup(category: FormatCategory, formats: List<Format>, selected: Format?, onSelect: (Format) -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(category, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(category.label()), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                formats.forEach { format -> TargetChip(format, format == selected, onSelect) }
            }
        }
    }
}

@Composable
private fun TargetChip(format: Format, selected: Boolean, onSelect: (Format) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onSelect(format) },
        label = { Text(format.label()) },
        leadingIcon = if (selected) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
    )
}

private const val VISIBLE_FILES = 5

fun Format.label() = id.uppercase()

fun FormatCategory.label() = when (this) {
    FormatCategory.VIDEO -> R.string.category_video
    FormatCategory.AUDIO -> R.string.category_audio
    FormatCategory.IMAGE -> R.string.category_image
    FormatCategory.DOCUMENT -> R.string.category_document
    FormatCategory.PDF -> R.string.category_pdf
    FormatCategory.ARCHIVE -> R.string.category_archive
}

private fun FormatCategory.icon() = when (this) {
    FormatCategory.VIDEO -> R.drawable.ic_video
    FormatCategory.AUDIO -> R.drawable.ic_audio
    FormatCategory.IMAGE -> R.drawable.ic_image
    FormatCategory.DOCUMENT -> R.drawable.ic_document
    FormatCategory.PDF -> R.drawable.ic_pdf
    FormatCategory.ARCHIVE -> R.drawable.ic_archive
}

private fun FormatCategory.formats() = Formats.all.filter { it.category == this }

private fun FormatCategory.sampleFormats() = formats().take(4).joinToString(" · ") { it.label() }

/** 種類のタイルから開くピッカーで、その種類のファイルだけを出す */
private fun FormatCategory.mimeTypes() = formats().map { it.mimeType }.distinct()

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
    val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    return contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
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
