package com.pokedex.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Guards the Room schema history. Right now it just proves the current schema
 * opens cleanly from scratch and through the real database builder; when a
 * version 3 is added, add a `helper.runMigrationsAndValidate(TEST_DB, 3, true,
 * PokeDexMigrations.MIGRATION_2_3)` case here.
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
