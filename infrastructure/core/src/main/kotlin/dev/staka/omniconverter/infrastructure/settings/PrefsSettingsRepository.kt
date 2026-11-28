package dev.staka.omniconverter.infrastructure.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.port.SettingsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** 設定は少ないので SharedPreferences に置く */
class PrefsSettingsRepository(private val context: Context) : SettingsRepository {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun current(): FileRef? {
        val uri = prefs.getString(KEY_URI, null) ?: return null
        return FileRef(uri, prefs.getString(KEY_NAME, null) ?: uri)
    }

    override fun outputDir(): Flow<FileRef?> = callbackFlow {
        trySend(current())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(current()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override suspend fun setOutputDir(dir: FileRef?) {
        current()?.let(::releaseAccess)
        dir?.let(::takeAccess)
        store(dir)
    }

    private fun takeAccess(dir: FileRef) =
        context.contentResolver.takePersistableUriPermission(Uri.parse(dir.uri), READ_WRITE)

    /** 選ばれなくなったフォルダの永続権限を返す。すでに失っていても構わない */
    private fun releaseAccess(dir: FileRef) {
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(dir.uri), READ_WRITE) }
    }

    private fun store(dir: FileRef?) = prefs.edit().apply {
        if (dir == null) remove(KEY_URI).remove(KEY_NAME) else putString(KEY_URI, dir.uri).putString(KEY_NAME, dir.name)
    }.apply()

    private companion object {
        const val KEY_URI = "output_dir_uri"
        const val KEY_NAME = "output_dir_name"
        const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
