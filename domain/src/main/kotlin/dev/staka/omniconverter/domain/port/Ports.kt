package dev.staka.omniconverter.domain.port

import dev.staka.omniconverter.domain.engine.ConversionEngine
import dev.staka.omniconverter.domain.model.ConversionJob
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Preset
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path

interface EngineRegistry {
    val engines: List<ConversionEngine>
}

interface ConversionJobRepository {
    fun observeAll(): Flow<List<ConversionJob>>
    suspend fun get(id: String): ConversionJob?
    suspend fun upsert(jobs: List<ConversionJob>)
    suspend fun delete(id: String)
}

/** 利用者のファイルと、エンジンが読み書きする作業領域との橋渡し */
interface FileGateway {
    /** [source] を作業領域へ取り込み、そのパスを返す */
    suspend fun importToWorkspace(source: FileRef, workDir: Path): Path

    /** 作業領域の [file] を出力先へ [name] で書き出す */
    suspend fun export(file: Path, name: String): FileRef

    /** 1 件の変換に使う空の作業ディレクトリを作る */
    suspend fun newWorkDir(): Path

    suspend fun deleteWorkDir(dir: Path)

    /** 一時的にしか読めないファイルを、変換が終わるまで読めるよう手元に写す */
    suspend fun retain(source: FileRef): FileRef

    /** [retain] で写したファイルなら消す。それ以外は何もしない */
    suspend fun release(source: FileRef)
}

/** 利用者の設定 */
interface SettingsRepository {
    /** 出力先のフォルダ。null なら既定の場所 */
    fun outputDir(): Flow<FileRef?>
    suspend fun setOutputDir(dir: FileRef?)
}

interface PresetRepository {
    fun observeAll(): Flow<List<Preset>>
    suspend fun save(preset: Preset)
    suspend fun delete(id: String)
}

/** 登録済みジョブをバックグラウンドで順に実行させる */
interface JobScheduler {
    fun schedule()
    fun cancel(jobId: String)
}
