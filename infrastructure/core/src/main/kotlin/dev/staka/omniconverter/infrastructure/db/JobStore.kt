package dev.staka.omniconverter.infrastructure.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.staka.omniconverter.domain.model.ConversionJob
import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.domain.model.JobStatus
import dev.staka.omniconverter.domain.model.Preset
import dev.staka.omniconverter.domain.model.RawCommand
import dev.staka.omniconverter.domain.port.ConversionJobRepository
import dev.staka.omniconverter.domain.port.PresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val sourceUri: String,
    val sourceName: String,
    val targetId: String?,
    val optionsJson: String,
    val rawEngine: String?,
    val rawArgsJson: String?,
    val rawExtension: String?,
    val status: String,
    val progress: Float?,
    val outputUri: String?,
    val outputName: String?,
    val error: String?,
)

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt, id")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: String): JobEntity?

    @Query("SELECT createdAt FROM jobs WHERE id = :id")
    suspend fun createdAt(id: String): Long?

    @Upsert
    suspend fun upsert(jobs: List<JobEntity>)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT id FROM jobs WHERE status = 'QUEUED' ORDER BY createdAt, id")
    suspend fun queuedIds(): List<String>

    /** 前回のプロセスが途中で終わったジョブを待機に戻す */
    @Query("UPDATE jobs SET status = 'QUEUED', progress = NULL WHERE status = 'RUNNING'")
    suspend fun requeueRunning()
}

/** Room を合成ルートから隠すための入口。1 つのデータベースから各リポジトリを作る */
class Storage(context: Context) {
    private val db = OmniDatabase.create(context)
    val jobDao: JobDao get() = db.jobDao()
    val presets: PresetRepository = RoomPresetRepository(db.presetDao())
}

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val engine: String,
    val argsJson: String,
    val extension: String,
)

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY name")
    fun observeAll(): Flow<List<PresetEntity>>

    @Upsert
    suspend fun upsert(preset: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun delete(id: String)
}

class RoomPresetRepository(private val dao: PresetDao) : PresetRepository {
    override fun observeAll(): Flow<List<Preset>> = dao.observeAll().map { list ->
        list.map { e ->
            val args = JSONArray(e.argsJson)
            Preset(e.id, e.name, RawCommand(EngineId(e.engine), List(args.length()) { args.getString(it) }, e.extension))
        }
    }

    override suspend fun save(preset: Preset) = dao.upsert(
        PresetEntity(preset.id, preset.name, preset.command.engine.value, JSONArray(preset.command.args).toString(), preset.command.outputExtension),
    )

    override suspend fun delete(id: String) = dao.delete(id)
}

@Database(entities = [JobEntity::class, PresetEntity::class], version = 2, exportSchema = false)
abstract class OmniDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun presetDao(): PresetDao

    companion object {
        /** 版 2 でプリセットの表を足した。ジョブの表には手を付けない */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `presets` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `engine` TEXT NOT NULL, " +
                        "`argsJson` TEXT NOT NULL, `extension` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        fun create(context: Context): OmniDatabase =
            Room.databaseBuilder(context, OmniDatabase::class.java, "omni.db").addMigrations(MIGRATION_1_2).build()
    }
}

class RoomJobRepository(private val dao: JobDao, private val clock: () -> Long = System::currentTimeMillis) :
    ConversionJobRepository {

    override fun observeAll(): Flow<List<ConversionJob>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: String): ConversionJob? = dao.get(id)?.toDomain()

    /** 既存のジョブは作成時刻を保つ。新規のジョブには一括登録の中の順番を時刻に足し、登録順に並べる */
    override suspend fun upsert(jobs: List<ConversionJob>) {
        val batchStart = clock()
        dao.upsert(jobs.mapIndexed { position, job -> job.toEntity(dao.createdAt(job.id) ?: (batchStart + position)) })
    }

    override suspend fun delete(id: String) = dao.delete(id)
}

internal fun ConversionJob.toEntity(createdAt: Long) = JobEntity(
    id = id,
    createdAt = createdAt,
    sourceUri = source.uri,
    sourceName = source.name,
    targetId = target?.id,
    optionsJson = JSONObject(options).toString(),
    rawEngine = rawCommand?.engine?.value,
    rawArgsJson = rawCommand?.let { JSONArray(it.args).toString() },
    rawExtension = rawCommand?.outputExtension,
    status = status.name,
    progress = progress,
    outputUri = output?.uri,
    outputName = output?.name,
    error = error,
)

internal fun JobEntity.toDomain() = ConversionJob(
    id = id,
    source = FileRef(sourceUri, sourceName),
    target = targetId?.let(Formats::of),
    options = JSONObject(optionsJson).let { o -> o.keys().asSequence().associateWith { o.getString(it) } },
    rawCommand = rawEngine?.let { engine ->
        val args = JSONArray(rawArgsJson!!)
        RawCommand(EngineId(engine), List(args.length()) { args.getString(it) }, rawExtension!!)
    },
    status = JobStatus.valueOf(status),
    progress = progress,
    output = outputUri?.let { FileRef(it, outputName!!) },
    error = error,
)
