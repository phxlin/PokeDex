package com.pokedex.app.data.local

import android.content.ContentValues
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Guards the Room schema history. Proves the current schema opens cleanly from
 * scratch and through the real database builder, and that each hand-written
 * migration actually runs (rather than just being syntactically valid) and
 * produces the data it promises.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PokeDexDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun currentSchemaCreatesFromScratch() {
        helper.createDatabase(testDb, PokeDexDatabase.LATEST_VERSION).close()
    }

    @Test
    @Throws(IOException::class)
    fun migration2To3BackfillsSortOrderFromUpdatedAt() {
        val db = helper.createDatabase(testDb, 2)
        // Row order here doesn't matter — sortOrder must come out matching each
        // team's rank by updatedAt DESC (most recently updated first), same as
        // observeTeams()'s old ORDER BY, not by insertion or id order.
        db.insert("team", android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL, ContentValues().apply {
            put("id", 1L); put("name", "oldest"); put("updatedAt", 100L)
        })
        db.insert("team", android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL, ContentValues().apply {
            put("id", 2L); put("name", "newest"); put("updatedAt", 300L)
        })
        db.insert("team", android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL, ContentValues().apply {
            put("id", 3L); put("name", "middle"); put("updatedAt", 200L)
        })
        db.close()

        val migrated = helper.runMigrationsAndValidate(testDb, 3, true, PokeDexMigrations.MIGRATION_2_3)

        val order = mutableMapOf<Long, Long>()
        migrated.query("SELECT id, sortOrder FROM team").use { cursor ->
            while (cursor.moveToNext()) {
                order[cursor.getLong(0)] = cursor.getLong(1)
            }
        }
        assertThat(order[2L]).isLessThan(order[3L]) // newest ranks before middle
        assertThat(order[3L]).isLessThan(order[1L]) // middle ranks before oldest
    }

    @Test
    @Throws(IOException::class)
    fun realBuilderOpensTheCurrentSchema() {
        helper.createDatabase(testDb, PokeDexDatabase.LATEST_VERSION).close()

        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PokeDexDatabase::class.java,
            testDb,
        )
            .addMigrations(*PokeDexMigrations.ALL)
            .fallbackToDestructiveMigrationFrom(1)
            .build()
            .apply {
                openHelper.writableDatabase // forces Room's schema validation
                close()
            }
    }
}
