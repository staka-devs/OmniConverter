package dev.staka.omniconverter.domain.model

enum class FormatCategory { VIDEO, AUDIO, IMAGE, DOCUMENT, PDF, ARCHIVE }

/** ファイル形式。[id] は代表的な拡張子 */
data class Format(
    val id: String,
    val category: FormatCategory,
    val mimeType: String,
    val extensions: List<String> = listOf(id),
)
