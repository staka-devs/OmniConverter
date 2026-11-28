package dev.staka.omniconverter.infrastructure.file

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.port.FileGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import java.util.UUID

/**
 * SAF で選んだファイルを作業領域へ写し、結果を出力先へ書き出す。
 * 出力先は利用者が選んだフォルダ、選んでいなければ Download/OmniConverter とする。
 * shortcut: 入力を一度コピーするので大きな動画は容量を 2 倍使う。困ったら /proc/self/fd 経由の直接読みに切り替える
 */
class SafFileGateway(
    private val context: Context,
    private val outputDir: suspend () -> FileRef? = { null },
) : FileGateway {
    private val workRoot = File(context.cacheDir, "work")
    private val inbox = File(context.filesDir, "inbox")

    override suspend fun importToWorkspace(source: FileRef, workDir: Path): Path = withContext(Dispatchers.IO) {
        val target = File(workDir.toFile(), safeName(source.name))
        val input = context.contentResolver.openInputStream(Uri.parse(source.uri))
            ?: error("ファイルを開けません: ${source.name}")
        input.use { i -> target.outputStream().use { i.copyTo(it) } }
        target.toPath()
    }

    override suspend fun export(file: Path, name: String): FileRef {
        val dir = outputDir()
        return withContext(Dispatchers.IO) { if (dir != null) exportToTree(file, name, dir) else exportToDownloads(file, name) }
    }

    private fun exportToDownloads(file: Path, name: String): FileRef {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName(name))
            put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(name))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/OmniConverter")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("出力先を作れません")
        try {
            val out = resolver.openOutputStream(uri) ?: error("出力先を開けません")
            out.use { o -> file.toFile().inputStream().use { it.copyTo(o) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return FileRef(uri.toString(), name)
    }

    /** 利用者が選んだフォルダに作る。同じ名前があれば DocumentsProvider が番号を付ける */
    private fun exportToTree(file: Path, name: String, dir: FileRef): FileRef {
        val resolver = context.contentResolver
        val tree = Uri.parse(dir.uri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val uri = DocumentsContract.createDocument(resolver, parent, mimeOf(name), safeName(name))
            ?: error("出力先に作れません: ${dir.name}")
        try {
            val out = resolver.openOutputStream(uri) ?: error("出力先を開けません")
            out.use { o -> file.toFile().inputStream().use { it.copyTo(o) } }
        } catch (e: Exception) {
            DocumentsContract.deleteDocument(resolver, uri)
            throw e
        }
        return FileRef(uri.toString(), name)
    }

    override suspend fun newWorkDir(): Path = withContext(Dispatchers.IO) {
        File(workRoot, UUID.randomUUID().toString()).apply { mkdirs() }.toPath()
    }

    override suspend fun deleteWorkDir(dir: Path) {
        withContext(Dispatchers.IO) { dir.toFile().deleteRecursively() }
    }

    override suspend fun retain(source: FileRef): FileRef = withContext(Dispatchers.IO) {
        val dir = File(inbox, UUID.randomUUID().toString()).apply { mkdirs() }
        val target = File(dir, safeName(source.name))
        val input = context.contentResolver.openInputStream(Uri.parse(source.uri))
            ?: error("ファイルを開けません: ${source.name}")
        input.use { i -> target.outputStream().use { i.copyTo(it) } }
        FileRef(Uri.fromFile(target).toString(), source.name)
    }

    override suspend fun release(source: FileRef) {
        val dir = retainedCopyDir(source) ?: return
        withContext(Dispatchers.IO) { dir.deleteRecursively() }
    }

    /** [retain] が作った写しのディレクトリ。写しの置き場の外を指す URI なら null を返し、消させない */
    private fun retainedCopyDir(source: FileRef): File? {
        val path = Uri.parse(source.uri).takeIf { it.scheme == "file" }?.path ?: return null
        val dir = File(path).canonicalFile.parentFile ?: return null
        return dir.takeIf { it.parentFile == inbox.canonicalFile }
    }

    private fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.').lowercase())
            ?: "application/octet-stream"

    companion object {
        /** 外部から来た表示名で作業領域の外へ書かないよう、区切り文字と先頭のドットを除く */
        fun safeName(name: String): String =
            name.replace(Regex("[/\\\\\u0000]"), "_").trimStart('.').ifBlank { "file" }
    }
}
