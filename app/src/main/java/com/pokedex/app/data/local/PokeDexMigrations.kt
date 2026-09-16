package com.pokedex.app.data.local

import androidx.room.migration.Migration

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
 * the shiny flag — see `TeamRepository`), the first real migration should
 * probably also give gender / shiny / form their own columns and backfill them.
 */
object PokeDexMigrations {

    /** All migrations, in order. Empty until the schema moves past version 2. */
    val ALL: Array<Migration> = emptyArray()

    // Example, once a version 3 exists:
    //
    // val MIGRATION_2_3 = object : Migration(2, 3) {
    //     override fun migrate(db: SupportSQLiteDatabase) {
    //         db.execSQL("ALTER TABLE team_member ADD COLUMN gender TEXT NOT NULL DEFAULT 'DEFAULT'")
    //         // …backfill from the packed teraType column…
    //     }
    // }
}
