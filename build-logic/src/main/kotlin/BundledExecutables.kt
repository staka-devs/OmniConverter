import com.android.build.api.dsl.Packaging

/**
 * 同梱した実行ファイルを、インストール時に nativeLibraryDir へ展開させる。
 * Android 10 以降はアプリが書ける場所のファイルを実行できず、展開先だけが実行を許される。ADR-0004
 */
fun Packaging.extractBundledExecutables() {
    jniLibs.useLegacyPackaging = true
}
