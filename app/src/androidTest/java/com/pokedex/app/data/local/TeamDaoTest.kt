package com.pokedex.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against a real (in-memory) SQLite database, not a mock — [TeamDao.swapSortOrder] is a
 * raw multi-statement swap, and a single `UPDATE ... CASE` version of it previously passed
 * review and every mocked test while silently collapsing both rows onto the same value,
 * because SQLite doesn't guarantee row-processing order within one multi-row UPDATE.
 */
@RunWith(AndroidJUnit4::class)
class TeamDaoTest {

    private lateinit var db: PokeDexDatabase
    private lateinit var dao: TeamDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PokeDexDatabase::class.java)
            .build()
        dao = db.teamDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun swapSortOrderExchangesTheTwoTeamsPositionsWithoutAffectingOthers() = runTest {
        val id1 = dao.insertTeam(TeamEntity(name = "A", updatedAt = 1L, sortOrder = 10))
        val id2 = dao.insertTeam(TeamEntity(name = "B", updatedAt = 2L, sortOrder = 20))
        val id3 = dao.insertTeam(TeamEntity(name = "C", updatedAt = 3L, sortOrder = 30))

        dao.swapSortOrder(id1, id3)

        assertThat(dao.sortOrderOf(id1)).isEqualTo(30)
        assertThat(dao.sortOrderOf(id2)).isEqualTo(20)
        assertThat(dao.sortOrderOf(id3)).isEqualTo(10)
    }
}
