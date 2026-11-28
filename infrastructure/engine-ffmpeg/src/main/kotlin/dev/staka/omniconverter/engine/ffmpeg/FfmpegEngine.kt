package dev.staka.omniconverter.engine.ffmpeg

import dev.staka.omniconverter.domain.engine.ConversionEngine
import dev.staka.omniconverter.domain.model.Capability
import dev.staka.omniconverter.domain.model.ConversionResult
import dev.staka.omniconverter.domain.model.ConversionTask
import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.Format
import dev.staka.omniconverter.domain.model.FormatCategory
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.domain.model.Progress
import dev.staka.omniconverter.infrastructure.process.NativeProcessRunner
import dev.staka.omniconverter.infrastructure.process.convert
import java.io.File
import java.nio.file.Path

/** 動画と音声の変換。同梱した ffmpeg を別プロセスで動かす。ADR-0004 */
class FfmpegEngine(
    private val binary: () -> File,
    private val runner: NativeProcessRunner,
) : ConversionEngine {
    override val id = EngineId("ffmpeg")
    override val capabilities: Set<Capability> = FfmpegArgs.capabilities

    override suspend fun convert(task: ConversionTask, onProgress: (Progress) -> Unit): ConversionResult {
        val args = listOf("-hide_banner", "-nostdin", "-y", "-progress", "pipe:1", "-nostats", "-i", task.input.toString()) +
            FfmpegArgs.encode(task.from, task.to) + task.output.toString()
        return run(args, task.output.parent, task.output, onProgress)
    }

    override suspend fun runRaw(args: List<String>, workDir: Path, output: Path, onProgress: (Progress) -> Unit) =
        run(listOf("-hide_banner", "-nostdin") + args, workDir, output, onProgress)

    private suspend fun run(args: List<String>, workDir: Path, output: Path, onProgress: (Progress) -> Unit): ConversionResult {
        val progress = FfmpegProgress()
        return runner.convert(listOf(binary().path) + args, workDir.toFile(), output) { line ->
            progress.feed(line)?.let { onProgress(Progress(it)) }
        }
    }
}

/** 形式ごとのエンコード指定。互換性の高いコーデックを既定にする */
object FfmpegArgs {
    private val video = listOf("mp4", "mkv", "webm", "mov", "avi", "flv", "ts", "3gp", "wmv", "mpeg").map(Formats::of)
    private val audio = listOf("mp3", "aac", "m4a", "flac", "wav", "ogg", "opus", "wma", "aiff").map(Formats::of)
    private val animated = listOf("gif", "webp").map(Formats::of)

    val capabilities: Set<Capability> = buildSet {
        for (from in video) {
            (video - from).forEach { add(Capability(from, it)) }
            audio.forEach { add(Capability(from, it)) }
            animated.forEach { add(Capability(from, it)) }
        }
        for (from in audio) (audio - from).forEach { add(Capability(from, it)) }
    }

    private val h264 = listOf("-c:v", "libx264", "-preset", "medium", "-crf", "23", "-pix_fmt", "yuv420p")
    private val aac = listOf("-c:a", "aac", "-b:a", "192k")

    /** 動画全体から 1 本のパレットを作って量子化する。短い GIF でも色が崩れにくい */
    private const val GIF_WITH_SHARED_PALETTE =
        "fps=12,scale='min(480,iw)':-2:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse"

    fun encode(from: Format, to: Format): List<String> {
        val dropVideo = if (from.category == FormatCategory.VIDEO && to.category == FormatCategory.AUDIO) listOf("-vn") else emptyList()
        return dropVideo + when (to.id) {
            "mp4", "mov" -> h264 + aac + listOf("-movflags", "+faststart")
            "mkv", "flv", "ts" -> h264 + aac
            "3gp" -> listOf("-c:v", "libx264", "-profile:v", "baseline", "-pix_fmt", "yuv420p") + aac
            "webm" -> listOf("-c:v", "libvpx-vp9", "-crf", "32", "-b:v", "0", "-row-mt", "1", "-c:a", "libopus", "-b:a", "128k")
            "avi" -> listOf("-c:v", "mpeg4", "-q:v", "3", "-c:a", "libmp3lame", "-q:a", "2")
            "wmv" -> listOf("-c:v", "wmv2", "-q:v", "3", "-c:a", "wmav2", "-b:a", "192k")
            "mpeg" -> listOf("-c:v", "mpeg2video", "-q:v", "3", "-c:a", "mp2", "-b:a", "192k")
            "mp3" -> listOf("-c:a", "libmp3lame", "-q:a", "2")
            "aac" -> aac + listOf("-f", "adts")
            "m4a" -> aac + listOf("-f", "ipod")
            "flac" -> listOf("-c:a", "flac")
            "wav" -> listOf("-c:a", "pcm_s16le")
            "ogg", "opus" -> listOf("-c:a", "libopus", "-b:a", "128k")
            "wma" -> listOf("-c:a", "wmav2", "-b:a", "192k")
            "aiff" -> listOf("-c:a", "pcm_s16be")
            "gif" -> listOf("-an", "-vf", GIF_WITH_SHARED_PALETTE, "-loop", "0")
            "webp" -> listOf("-an", "-vf", "fps=12,scale='min(480,iw)':-2", "-c:v", "libwebp_anim", "-q:v", "75", "-loop", "0")
            else -> error("ffmpeg は ${to.id} を出力できません")
        }
    }
}

/** ffmpeg の出力から進み具合を読む。長さは入力情報、位置は -progress か統計行から取る */
class FfmpegProgress {
    private var durationUs: Long? = null

    fun feed(line: String): Float? {
        DURATION.find(line)?.let { durationUs = clock(it.groupValues[1]) }
        val position = line.removePrefix("out_time_us=").takeIf { it != line }?.trim()?.toLongOrNull()
            ?: TIME.find(line)?.let { clock(it.groupValues[1]) }
        val total = durationUs ?: return null
        if (position == null || total <= 0) return null
        return (position.toFloat() / total).coerceIn(0f, 1f)
    }

    private fun clock(hms: String): Long {
        val (h, m, s) = hms.split(":")
        return ((h.toLong() * 3600 + m.toLong() * 60) * 1_000_000 + (s.toDouble() * 1_000_000)).toLong()
    }

    private companion object {
        val DURATION = Regex("""Duration: (\d+:\d{2}:\d{2}(?:\.\d+)?)""")
        val TIME = Regex("""time=(\d+:\d{2}:\d{2}(?:\.\d+)?)""")
    }
}
