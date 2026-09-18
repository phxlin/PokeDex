package com.pokedex.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written Room migrations for [PokeDexDatabase].
 *
 * The Pokédex cache tables (`pokemon_index`, `raw_cache`, `meta`) are disposable —
 * losing them just triggers a re-fetch. But `team` / `team_member` hold data the
 * user created, so **every schema change from version 2 onward must add a
 * `Migration` here** (with a case in `MigrationTest`). The database builder only
 * allows a destructive fallback *from version 1* (which predates the team tables);
 * a missing migration for any later version makes the app fail to open, on
 * purpose, rather than silently wiping saved teams.
 *
 * Because two columns are currently reused to dodge a schema bump
 * (`team_member.teraType` packs `"<gender>|<formSlug>"`, `team_member.level` packs
 * the shiny flag — see `TeamRepository`), a future migration should probably also
 * give gender / shiny / form their own columns and backfill them.
 */
object PokeDexMigrations {

    /** Adds manual drag-reorder position to the Teams list (`team.sortOrder`), backfilled
     * to match each team's current on-screen rank (most-recently-updated first) so the
     * first post-migration list looks unchanged until the user actually drags something. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE team ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                UPDATE team SET sortOrder = (
                    SELECT COUNT(*) FROM team t2
                    WHERE t2.updatedAt > team.updatedAt
                       OR (t2.updatedAt = team.updatedAt AND t2.id < team.id)
                )
                """.trimIndent(),
            )
        }
    }

    /** All migrations, in order. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_2_3)
}
