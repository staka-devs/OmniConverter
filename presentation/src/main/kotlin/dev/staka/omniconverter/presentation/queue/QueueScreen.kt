@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.staka.omniconverter.presentation.queue

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.staka.omniconverter.domain.model.ConversionJob
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.JobStatus
import dev.staka.omniconverter.presentation.R
import dev.staka.omniconverter.presentation.components.EmptyState
import dev.staka.omniconverter.presentation.components.ExpressiveScreen
import dev.staka.omniconverter.presentation.components.ShapedIcon
import dev.staka.omniconverter.presentation.convert.label

@Composable
fun QueueScreen(
    jobs: List<ConversionJob>,
    onCancel: (String) -> Unit,
    onClearFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var log by rememberSaveable { mutableStateOf<String?>(null) }
    val (active, finished) = jobs.partition { !it.isFinished }

    ExpressiveScreen(
        title = stringResource(R.string.tab_queue),
        actions = {
            IconButton(onClick = onClearFinished, enabled = finished.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_clear_all), contentDescription = stringResource(R.string.clear_finished))
            }
        },
        modifier = modifier,
    ) { padding ->
        if (jobs.isEmpty()) {
            EmptyState(R.drawable.ic_queue, MaterialShapes.Sunny, stringResource(R.string.queue_empty), Modifier.padding(padding))
            return@ExpressiveScreen
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            section(R.string.section_active, active) { job, shapes -> JobItem(job, shapes, onCancel, onOpen = {}) }
            section(R.string.section_finished, finished) { job, shapes ->
                JobItem(job, shapes, onCancel, onOpen = {
                    when (job.status) {
                        JobStatus.SUCCEEDED -> job.output?.let { context.open(it) }
                        JobStatus.FAILED -> log = job.error
                        else -> Unit
                    }
                })
            }
        }
    }

    log?.let { text ->
        AlertDialog(
            onDismissRequest = { log = null },
            title = { Text(stringResource(R.string.status_failed)) },
            text = {
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { log = null }) { Text(stringResource(R.string.close)) } },
        )
    }
}

private fun LazyListScope.section(title: Int, jobs: List<ConversionJob>, row: @Composable (ConversionJob, ListItemShapes) -> Unit) {
    if (jobs.isEmpty()) return
    item(key = title) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
        )
    }
    itemsIndexed(jobs, key = { _, job -> job.id }) { index, job ->
        Box(Modifier.animateItem()) { row(job, ListItemDefaults.segmentedShapes(index, jobs.size)) }
    }
}

private val ConversionJob.isFinished get() = status != JobStatus.QUEUED && status != JobStatus.RUNNING

@Composable
private fun JobItem(job: ConversionJob, shapes: ListItemShapes, onCancel: (String) -> Unit, onOpen: () -> Unit) {
    SegmentedListItem(
        onClick = onOpen,
        shapes = shapes,
        leadingContent = { StatusMark(job) },
        supportingContent = { JobDetail(job) },
        trailingContent = {
            if (!job.isFinished) {
                IconButton(onClick = { onCancel(job.id) }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cancel))
                }
            }
        },
    ) {
        Text(job.source.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
    }
}

@Composable
private fun JobDetail(job: ConversionJob) {
    val target = job.target?.label() ?: job.rawCommand?.outputExtension?.uppercase().orEmpty()
    val status = when (job.status) {
        JobStatus.QUEUED -> stringResource(R.string.status_queued)
        JobStatus.RUNNING -> job.progress?.let { "${(it * 100).toInt()}%" }.orEmpty()
        JobStatus.SUCCEEDED -> job.output?.name ?: stringResource(R.string.status_succeeded)
        JobStatus.FAILED -> job.error?.lines()?.lastOrNull { it.isNotBlank() } ?: stringResource(R.string.status_failed)
        JobStatus.CANCELLED -> stringResource(R.string.status_cancelled)
    }
    Column {
        Text(
            "$target · $status",
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (job.status == JobStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (job.status == JobStatus.RUNNING) {
            val progress = job.progress
            val bar = Modifier.fillMaxWidth().padding(top = 8.dp)
            if (progress != null) LinearWavyProgressIndicator(progress = { progress }, modifier = bar) else LinearWavyProgressIndicator(bar)
        }
    }
}

@Composable
private fun StatusMark(job: ConversionJob) {
    val colors = MaterialTheme.colorScheme
    when (job.status) {
        JobStatus.RUNNING -> {
            val progress = job.progress
            if (progress != null) CircularWavyProgressIndicator(progress = { progress }, modifier = Modifier.size(40.dp))
            else LoadingIndicator(Modifier.size(40.dp))
        }
        JobStatus.QUEUED -> ShapedIcon(R.drawable.ic_queue, MaterialShapes.Circle, colors.surfaceContainerHighest, colors.onSurfaceVariant)
        JobStatus.SUCCEEDED -> ShapedIcon(R.drawable.ic_check, MaterialShapes.Cookie9Sided, colors.primaryContainer, colors.onPrimaryContainer)
        JobStatus.FAILED -> ShapedIcon(R.drawable.ic_error, MaterialShapes.Burst, colors.errorContainer, colors.onErrorContainer)
        JobStatus.CANCELLED -> ShapedIcon(R.drawable.ic_close, MaterialShapes.Circle, colors.surfaceContainerHighest, colors.onSurfaceVariant)
    }
}

/** 変換結果を、その形式を開けるアプリで開く */
private fun Context.open(file: FileRef) {
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.name.substringAfterLast('.').lowercase()) ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(file.uri), mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
    }
}
