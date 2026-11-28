package dev.staka.omniconverter.infrastructure

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.staka.omniconverter.domain.model.ConversionJob
import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.domain.model.Preset
import dev.staka.omniconverter.domain.model.RawCommand
import dev.staka.omniconverter.infrastructure.db.OmniDatabase
import dev.staka.omniconverter.infrastructure.db.RoomJobRepository
import dev.staka.omniconverter.infrastructure.db.RoomPresetRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PresetStoreIT {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val command = RawCommand(EngineId("ffmpeg"), listOf("-i", "{input}", "-vf", "scale=640:-2", "{output}"), "mp4")

    @Test
    fun プリセットを保存して名前順に読み出し削除できる() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, OmniDatabase::class.java).build()
        try {
            val repo = RoomPresetRepository(db.presetDao())
            repo.save(Preset("2", "縮小", command))
            repo.save(Preset("1", "音声だけ", command.copy(args = listOf("{input}", "-vn", "{output}"), outputExtension = "mp3")))
            assertEquals(listOf("縮小", "音声だけ").sorted(), repo.observeAll().first().map { it.name })
            assertEquals(command, repo.observeAll().first().single { it.id == "2" }.command)
            repo.delete("2")
            assertEquals(listOf("1"), repo.observeAll().first().map { it.id })
        } finally {
            db.close()
        }
    }

    /** Room は移行後に表の形を検証するので、移行の SQL が実体と食い違えば開く時点で失敗する */
    @Test
    fun 版1のデータベースを移行してもジョブが残る() = runBlocking {
        val name = "migration-${System.nanoTime()}.db"
        createVersion1Database(name)
        val db = Room.databaseBuilder(context, OmniDatabase::class.java, name).addMigrations(OmniDatabase.MIGRATION_1_2).build()
        try {
            assertTrue(RoomJobRepository(db.jobDao()).get("j") != null)
            RoomPresetRepository(db.presetDao()).save(Preset("p", "x", command))
            assertEquals(1, RoomPresetRepository(db.presetDao()).observeAll().first().size)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    /** 版 2 で作ってからプリセットの表を消し、版 1 の状態に戻す */
    private suspend fun createVersion1Database(name: String) {
        Room.databaseBuilder(context, OmniDatabase::class.java, name).build().apply {
            RoomJobRepository(jobDao()).upsert(listOf(ConversionJob("j", FileRef("u", "a.png"), Formats.of("jpg"))))
            close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE presets")
            it.version = 1
        }
    }
}
