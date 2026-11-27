package dev.staka.omniconverter.presentation.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.staka.omniconverter.domain.model.ConversionJob
import dev.staka.omniconverter.domain.model.JobStatus
import dev.staka.omniconverter.presentation.R
import dev.staka.omniconverter.presentation.convert.label

@Composable
fun QueueScreen(
    jobs: List<ConversionJob>,
    onCancel: (String) -> Unit,
    onClearFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_queue), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onClearFinished, enabled = jobs.any { it.isFinished }) {
                Icon(painterResource(R.drawable.ic_clear_all), contentDescription = stringResource(R.string.clear_finished))
            }
        }
        if (jobs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.queue_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(jobs, key = { it.id }) { job -> JobRow(job, onCancel) }
        }
    }
}

private val ConversionJob.isFinished get() = status != JobStatus.QUEUED && status != JobStatus.RUNNING

@Composable
private fun JobRow(job: ConversionJob, onCancel: (String) -> Unit) {
    val target = job.target?.label() ?: job.rawCommand?.outputExtension?.uppercase().orEmpty()
    Column {
        ListItem(
            headlineContent = { Text(job.source.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
            supportingContent = {
                val status = when (job.status) {
                    JobStatus.QUEUED -> stringResource(R.string.status_queued)
                    JobStatus.RUNNING -> job.progress?.let { "${(it * 100).toInt()}%" }.orEmpty()
                    JobStatus.SUCCEEDED -> job.output?.name ?: stringResource(R.string.status_succeeded)
                    JobStatus.FAILED -> job.error?.lines()?.lastOrNull { it.isNotBlank() } ?: stringResource(R.string.status_failed)
                    JobStatus.CANCELLED -> stringResource(R.string.status_cancelled)
                }
                Text(
                    "→ $target  $status",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (job.status == JobStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                when (job.status) {
                    JobStatus.QUEUED, JobStatus.RUNNING -> IconButton(onClick = { onCancel(job.id) }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cancel))
                    }
                    JobStatus.SUCCEEDED -> Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.status_succeeded),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    JobStatus.FAILED -> Icon(
                        painterResource(R.drawable.ic_error),
                        contentDescription = stringResource(R.string.status_failed),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    JobStatus.CANCELLED -> Unit
                }
            },
        )
        if (job.status == JobStatus.RUNNING) {
            val progress = job.progress
            val bar = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = bar) else LinearProgressIndicator(bar)
        }
    }
}
