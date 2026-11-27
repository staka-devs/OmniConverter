package dev.staka.omniconverter.domain.model

import dev.staka.omniconverter.domain.model.FormatCategory.ARCHIVE
import dev.staka.omniconverter.domain.model.FormatCategory.AUDIO
import dev.staka.omniconverter.domain.model.FormatCategory.DOCUMENT
import dev.staka.omniconverter.domain.model.FormatCategory.IMAGE
import dev.staka.omniconverter.domain.model.FormatCategory.PDF
import dev.staka.omniconverter.domain.model.FormatCategory.VIDEO

/** アプリが扱う既知の形式。エンジンはここにある形式で能力を宣言する */
object Formats {
    val all: List<Format> = listOf(
        Format("mp4", VIDEO, "video/mp4", listOf("mp4", "m4v")),
        Format("mkv", VIDEO, "video/x-matroska"),
        Format("webm", VIDEO, "video/webm"),
        Format("mov", VIDEO, "video/quicktime"),
        Format("avi", VIDEO, "video/x-msvideo"),
        Format("flv", VIDEO, "video/x-flv"),
        Format("ts", VIDEO, "video/mp2t", listOf("ts", "m2ts", "mts")),
        Format("3gp", VIDEO, "video/3gpp"),
        Format("wmv", VIDEO, "video/x-ms-wmv"),
        Format("mpeg", VIDEO, "video/mpeg", listOf("mpeg", "mpg")),
        Format("mp3", AUDIO, "audio/mpeg"),
        Format("aac", AUDIO, "audio/aac"),
        Format("m4a", AUDIO, "audio/mp4"),
        Format("flac", AUDIO, "audio/flac"),
        Format("wav", AUDIO, "audio/wav"),
        Format("ogg", AUDIO, "audio/ogg", listOf("ogg", "oga")),
        Format("opus", AUDIO, "audio/opus"),
        Format("wma", AUDIO, "audio/x-ms-wma"),
        Format("aiff", AUDIO, "audio/aiff", listOf("aiff", "aif")),
        Format("jpg", IMAGE, "image/jpeg", listOf("jpg", "jpeg")),
        Format("png", IMAGE, "image/png"),
        Format("webp", IMAGE, "image/webp"),
        Format("gif", IMAGE, "image/gif"),
        Format("bmp", IMAGE, "image/bmp"),
        Format("tiff", IMAGE, "image/tiff", listOf("tiff", "tif")),
        Format("heic", IMAGE, "image/heic", listOf("heic", "heif")),
        Format("avif", IMAGE, "image/avif"),
        Format("jxl", IMAGE, "image/jxl"),
        Format("ico", IMAGE, "image/x-icon"),
        Format("svg", IMAGE, "image/svg+xml"),
        Format("psd", IMAGE, "image/vnd.adobe.photoshop"),
        Format("dng", IMAGE, "image/x-adobe-dng", listOf("dng", "cr2", "cr3", "nef", "arw", "orf", "rw2")),
        Format("tga", IMAGE, "image/x-tga"),
        Format("md", DOCUMENT, "text/markdown", listOf("md", "markdown")),
        Format("html", DOCUMENT, "text/html", listOf("html", "htm", "xhtml")),
        Format("docx", DOCUMENT, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        Format("odt", DOCUMENT, "application/vnd.oasis.opendocument.text"),
        Format("pptx", DOCUMENT, "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        Format("rtf", DOCUMENT, "application/rtf"),
        Format("epub", DOCUMENT, "application/epub+zip"),
        Format("txt", DOCUMENT, "text/plain"),
        Format("tex", DOCUMENT, "application/x-tex"),
        Format("rst", DOCUMENT, "text/x-rst"),
        Format("org", DOCUMENT, "text/org"),
        Format("ipynb", DOCUMENT, "application/x-ipynb+json"),
        Format("pdf", PDF, "application/pdf"),
        Format("xps", PDF, "application/oxps", listOf("xps", "oxps")),
        Format("cbz", PDF, "application/vnd.comicbook+zip"),
        Format("zip", ARCHIVE, "application/zip"),
        Format("tar", ARCHIVE, "application/x-tar"),
        Format("tar.gz", ARCHIVE, "application/gzip", listOf("tar.gz", "tgz")),
        Format("tar.xz", ARCHIVE, "application/x-xz", listOf("tar.xz", "txz")),
        Format("tar.bz2", ARCHIVE, "application/x-bzip2", listOf("tar.bz2", "tbz2")),
        Format("tar.zst", ARCHIVE, "application/zstd", listOf("tar.zst", "tzst")),
        Format("7z", ARCHIVE, "application/x-7z-compressed"),
        Format("rar", ARCHIVE, "application/vnd.rar"),
        Format("iso", ARCHIVE, "application/x-iso9660-image"),
        Format("cpio", ARCHIVE, "application/x-cpio"),
    )

    private val byId = all.associateBy { it.id }

    fun of(id: String): Format = byId[id] ?: error("未知の形式: $id")

    /** ファイル名から形式を判定する。tar.gz のような複合拡張子を優先する */
    fun detect(fileName: String): Format? {
        val name = fileName.lowercase()
        return all.flatMap { f -> f.extensions.map { it to f } }
            .filter { (ext, _) -> name.endsWith(".$ext") }
            .maxByOrNull { (ext, _) -> ext.length }
            ?.second
    }
}
