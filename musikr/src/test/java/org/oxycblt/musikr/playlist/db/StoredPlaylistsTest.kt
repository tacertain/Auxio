/*
 * Copyright (c) 2026 Auxio Project
 * StoredPlaylistsTest.kt is part of Auxio.
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
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.playlist.EntryPointer
import org.oxycblt.musikr.playlist.PlaylistEntryRef
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class StoredPlaylistsTest {
    private lateinit var db: PlaylistDatabase
    private lateinit var storedPlaylists: StoredPlaylists

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    PlaylistDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        storedPlaylists = StoredPlaylists.from(db.playlistDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun newWithEntries_mixedEntries_readsBackInOrderWithKinds() = runTest {
        val songA = song()
        val album = album()
        val songB = song()

        storedPlaylists.newWithEntries(
            "Mixed",
            listOf(
                PlaylistEntryRef.SongRef(songA),
                PlaylistEntryRef.AlbumRef(album),
                PlaylistEntryRef.SongRef(songB),
            ),
        )

        val files = storedPlaylists.read()
        assertEquals(1, files.size)
        assertEquals("Mixed", files[0].name)
        assertEquals(
            listOf(
                EntryPointer.Song(songA.uid),
                EntryPointer.Album(album.uid),
                EntryPointer.Song(songB.uid),
            ),
            files[0].entryPointers,
        )
    }

    @Test
    fun rewriteAndAdd_updateEntriesInOrder() = runTest {
        val songA = song()
        val albumA = album()
        val albumB = album()
        val songB = song()
        val handle = storedPlaylists.new("Songs", listOf(songA))

        handle.rewriteEntries(
            listOf(PlaylistEntryRef.AlbumRef(albumA), PlaylistEntryRef.SongRef(songA))
        )
        handle.addEntries(listOf(PlaylistEntryRef.AlbumRef(albumB)))
        handle.add(listOf(songB))

        assertEquals(
            listOf(
                EntryPointer.Album(albumA.uid),
                EntryPointer.Song(songA.uid),
                EntryPointer.Album(albumB.uid),
                EntryPointer.Song(songB.uid),
            ),
            storedPlaylists.read().single().entryPointers,
        )
    }

    @Test
    fun songWrappers_storeSongEntries() = runTest {
        val songs = List(3) { song() }
        val handle = storedPlaylists.new("Songs", songs)
        handle.rewrite(songs.reversed())

        assertEquals(
            songs.reversed().map { EntryPointer.Song(it.uid) },
            storedPlaylists.read().single().entryPointers,
        )
    }

    @Test
    fun read_unknownEntryType_isSkipped() = runTest {
        val song = song()
        val handle = storedPlaylists.new("Songs", listOf(song))
        db.playlistDao()
            .insertPlaylistEntries(
                listOf(
                    PlaylistEntry(
                        playlistUid = handle.uid,
                        entryType = 99,
                        entryUid = Music.UID.auxio(Music.UID.Item.ARTIST),
                    )
                )
            )

        assertEquals(
            listOf(EntryPointer.Song(song.uid)),
            storedPlaylists.read().single().entryPointers,
        )
    }

    @Test
    fun delete_removesPlaylistAndEntries() = runTest {
        val handle = storedPlaylists.new("Songs", listOf(song()))
        val other =
            storedPlaylists.newWithEntries("Album", listOf(PlaylistEntryRef.AlbumRef(album())))
        handle.delete()

        val files = storedPlaylists.read()
        assertEquals(listOf("Album"), files.map { it.name })
        assertEquals(other.uid, files.single().handle.uid)
        assertEquals(1, files.single().entryPointers.size)
    }

    private fun song(): Song {
        val uid = Music.UID.auxio(Music.UID.Item.SONG)
        return mockk { every { this@mockk.uid } returns uid }
    }

    private fun album(): Album {
        val uid = Music.UID.auxio(Music.UID.Item.ALBUM)
        return mockk { every { this@mockk.uid } returns uid }
    }
}
