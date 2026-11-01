/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistDatabaseMigrationTest.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.musikr.playlist.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.playlist.EntryPointer
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PlaylistDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            PlaylistDatabase::class.java,
        )

    @Test
    fun migrate75To76_keepsSongsInOrderAndDropsOldTables() = runTest {
        val playlistA = Music.UID.auxio(Music.UID.Item.PLAYLIST)
        val playlistB = Music.UID.auxio(Music.UID.Item.PLAYLIST)
        val songs = List(4) { Music.UID.auxio(Music.UID.Item.SONG) }

        helper.createDatabase(DB_NAME, 75).use { db ->
            db.execSQL("INSERT INTO PlaylistInfo VALUES ('$playlistA', 'A')")
            db.execSQL("INSERT INTO PlaylistInfo VALUES ('$playlistB', 'B')")
            for (song in songs) {
                db.execSQL("INSERT INTO PlaylistSong VALUES ('$song')")
            }
            // Interleave the two playlists so ordering by row id actually matters.
            for ((playlist, song) in
                listOf(
                    playlistA to songs[2],
                    playlistB to songs[3],
                    playlistA to songs[0],
                    playlistA to songs[2],
                    playlistB to songs[1],
                )) {
                db.execSQL(
                    "INSERT INTO PlaylistSongCrossRef (playlistUid, songUid) " +
                        "VALUES ('$playlist', '$song')"
                )
            }
        }

        // Validates the migrated schema against 76.json.
        helper.runMigrationsAndValidate(DB_NAME, 76, true, PlaylistDatabase.MIGRATION_75_76).use {
            db ->
            db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use {
                val tables = mutableListOf<String>()
                while (it.moveToNext()) tables.add(it.getString(0))
                assertFalse("PlaylistSong" in tables)
                assertFalse("PlaylistSongCrossRef" in tables)
            }
        }

        // Room's own open-time validation must accept the migrated file too.
        val db =
            Room.databaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    PlaylistDatabase::class.java,
                    DB_NAME,
                )
                .addMigrations(PlaylistDatabase.MIGRATION_75_76)
                .allowMainThreadQueries()
                .build()
        try {
            val files = StoredPlaylists.from(db.playlistDao()).read().associateBy { it.name }
            assertEquals(
                listOf(songs[2], songs[0], songs[2]).map { EntryPointer.Song(it) },
                files.getValue("A").entryPointers,
            )
            assertEquals(
                listOf(songs[3], songs[1]).map { EntryPointer.Song(it) },
                files.getValue("B").entryPointers,
            )
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
