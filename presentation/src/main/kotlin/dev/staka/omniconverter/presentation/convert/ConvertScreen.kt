@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.staka.omniconverter.presentation.convert

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Format
import dev.staka.omniconverter.domain.model.FormatCategory
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.presentation.R
import dev.staka.omniconverter.presentation.components.EmptyState
import dev.staka.omniconverter.presentation.components.ExpressiveScreen
import dev.staka.omniconverter.presentation.components.ShapedIcon

@Composable
fun ConvertScreen(
    state: ConvertUiState,
    onFilesPicked: (List<FileRef>) -> Unit,
    onSelect: (Format) -> Unit,
    onClear: () -> Unit,
    onConvert: () -> Unit,
    outputDir: FileRef? = null,
    onOutputDir: (FileRef?) -> Unit = {},
    onRemove: (FileRef) -> Unit = {},
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
    val hasFiles = state.files.isNotEmpty()

    ExpressiveScreen(
        title = stringResource(R.string.app_title),
        subtitle = if (hasFiles) stringResource(R.string.file_count, state.files.size) else null,
        actions = {
            if (hasFiles) {
                IconButton(onClick = onClear) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.clear_selection))
                }
            }
        },
        bottomBar = {
            if (hasFiles) {
                ConvertBar(
                    selected = state.selected,
                    outputDir = outputDir,
                    onPickDir = { folderPicker.launch(null) },
                    onResetDir = { onOutputDir(null) },
                    onConvert = onConvert,
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        if (hasFiles) {
            ChooseTarget(state, onSelect, onRemove, onAdd = pick, padding)
        } else {
            EmptyState(
                icon = R.drawable.ic_convert,
                shape = MaterialShapes.Cookie9Sided,
                text = null,
                modifier = Modifier.padding(padding),
            ) {
                Button(
                    onClick = pick,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight),
                    modifier = Modifier.heightIn(min = ButtonDefaults.LargeContainerHeight),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.LargeContainerHeight)),
                    )
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.LargeContainerHeight)))
                    Text(stringResource(R.string.pick_files), style = ButtonDefaults.textStyleFor(ButtonDefaults.LargeContainerHeight))
                }
            }
        }
    }
}

@Composable
private fun ChooseTarget(
    state: ConvertUiState,
    onSelect: (Format) -> Unit,
    onRemove: (FileRef) -> Unit,
    onAdd: () -> Unit,
    padding: PaddingValues,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val visible = if (showAll) state.files else state.files.take(VISIBLE_FILES)
    val hidden = state.files.size - visible.size
    val rows = visible.size + if (hidden > 0) 1 else 0

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(visible, key = { _, file -> file.uri }) { index, file ->
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index, rows),
                leadingContent = { FileBadge(file) },
                trailingContent = {
                    IconButton(onClick = { onRemove(file) }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.remove_file, file.name))
                    }
                },
                modifier = Modifier.animateItem(),
            ) {
                Text(file.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            }
        }
        if (hidden > 0) {
            item(key = "more") {
                SegmentedListItem(
                    onClick = { showAll = true },
                    shapes = ListItemDefaults.segmentedShapes(rows - 1, rows),
                ) {
                    Text(stringResource(R.string.more_files, hidden), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item(key = "add") {
            FilledTonalButton(onClick = onAdd, shapes = ButtonDefaults.shapes(), modifier = Modifier.padding(top = 12.dp)) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.add_files))
            }
        }
        item(key = "heading") {
            Text(
                stringResource(R.string.target_heading),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
        }
        if (state.targets.isEmpty()) {
            item(key = "none") { Text(stringResource(R.string.no_targets), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        state.targets.groupBy { it.category }.forEach { (category, formats) ->
            item(key = category) { TargetGroup(category, formats, state.selected, onSelect) }
        }
    }
}

@Composable
private fun FileBadge(file: FileRef) {
    val category = Formats.detect(file.name)?.category
    ShapedIcon(
        icon = category?.icon() ?: R.drawable.ic_file,
        shape = category?.shape() ?: MaterialShapes.Square,
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
private fun TargetGroup(category: FormatCategory, formats: List<Format>, selected: Format?, onSelect: (Format) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(
                icon = category.icon(),
                shape = category.shape(),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                size = 32.dp,
            )
            Spacer(Modifier.width(12.dp))
            Text(stringResource(category.label()), style = MaterialTheme.typography.titleMedium)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            formats.forEach { format ->
                ToggleButton(
                    checked = format == selected,
                    onCheckedChange = { onSelect(format) },
                ) {
                    Text(format.label())
                }
            }
        }
    }
}

/** 出力先と変換ボタン。何をどこへ出すかを、押す直前に一目で確かめられる */
@Composable
private fun ConvertBar(
    selected: Format?,
    outputDir: FileRef?,
    onPickDir: () -> Unit,
    onResetDir: () -> Unit,
    onConvert: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onPickDir, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    Icon(
                        painterResource(R.drawable.ic_folder),
                        contentDescription = stringResource(R.string.output_dir),
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        outputDir?.name ?: stringResource(R.string.default_output_dir),
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
                if (outputDir != null) {
                    IconButton(onClick = onResetDir) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.reset_output_dir))
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Button(
                onClick = onConvert,
                enabled = selected != null,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) {
                Icon(
                    painterResource(R.drawable.ic_convert),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)),
                )
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
                Text(
                    selected?.let { stringResource(R.string.convert_to, it.label()) } ?: stringResource(R.string.tab_convert),
                    style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                )
            }
        }
    }
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

fun FormatCategory.icon() = when (this) {
    FormatCategory.VIDEO -> R.drawable.ic_video
    FormatCategory.AUDIO -> R.drawable.ic_audio
    FormatCategory.IMAGE -> R.drawable.ic_image
    FormatCategory.DOCUMENT -> R.drawable.ic_document
    FormatCategory.PDF -> R.drawable.ic_pdf
    FormatCategory.ARCHIVE -> R.drawable.ic_archive
}

/** 種類ごとに形を変え、色に頼らず見分けられるようにする */
fun FormatCategory.shape() = when (this) {
    FormatCategory.VIDEO -> MaterialShapes.Cookie4Sided
    FormatCategory.AUDIO -> MaterialShapes.Circle
    FormatCategory.IMAGE -> MaterialShapes.Cookie6Sided
    FormatCategory.DOCUMENT -> MaterialShapes.Square
    FormatCategory.PDF -> MaterialShapes.Slanted
    FormatCategory.ARCHIVE -> MaterialShapes.Pill
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
