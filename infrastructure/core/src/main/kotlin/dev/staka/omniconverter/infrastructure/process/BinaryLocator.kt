package dev.staka.omniconverter.infrastructure.process

import android.content.Context
import java.io.File

/**
 * jniLibs に lib<name>.so として同梱した実行ファイルの場所を返す。
 * Android 10 以降はアプリが書ける場所のファイルを実行できないため、nativeLibraryDir に展開させる。ADR-0004
 */
class BinaryLocator(context: Context) {
    private val dir = File(context.applicationInfo.nativeLibraryDir)

    val libraryDir: File get() = dir

    fun locate(name: String): File {
        val file = File(dir, "lib$name.so")
        check(file.canExecute()) { "実行ファイルがありません: ${file.path}" }
        return file
    }
}
