/*
 * Copyright (c) 2026 Auxio Project
 * MusicGraphPlaylistTest.kt is part of Auxio.
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
 
package org.oxycblt.musikr.graph

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.PlaylistEntry
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.model.LibraryFactory
import org.oxycblt.musikr.playlist.EntryPointer
import org.oxycblt.musikr.playlist.PlaylistHandle
import org.oxycblt.musikr.playlist.interpret.PrePlaylist
import org.oxycblt.musikr.tag.Disc
import org.oxycblt.musikr.tag.Name
import org.oxycblt.musikr.tag.ReleaseType
import org.oxycblt.musikr.tag.interpret.PreAlbum
import org.oxycblt.musikr.tag.interpret.PreArtist
import org.oxycblt.musikr.tag.interpret.PreArtistsFrom
import org.oxycblt.musikr.tag.interpret.PreSong
import org.oxycblt.musikr.util.update
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MusicGraphPlaylistTest {
    private val artist = PreArtist(null, mockk(relaxed = true), "Artist")
    private val albumA = preAlbum("Album A")
    private val albumB = preAlbum("Album B")

    private val a1 = preSong("A1", albumA, disc = 1, track = 1)
    private val a2 = preSong("A2", albumA, disc = 1, track = 2)
    // Added out of order, and across two discs, to check disc-then-track ordering.
    private val b4 = preSong("B4", albumB, disc = 2, track = 1)
    private val b2 = preSong("B2", albumB, disc = 1, track = 2)
    private val b5 = preSong("B5", albumB, disc = null, track = null)
    private val b1 = preSong("B1", albumB, disc = 1, track = 1)
    private val b3 = preSong("B3", albumB, disc = 1, track = null)
    private val allSongs = listOf(a1, a2, b4, b2, b5, b1, b3)

    @Test
    fun albumEntry_expandsInDiscThenTrackOrder() {
        val playlist =
            loadPlaylist(
                EntryPointer.Song(a1.v363Uid),
                EntryPointer.Album(albumB.uid),
                EntryPointer.Song(a2.v363Uid),
            )

        assertEquals(3, playlist.entries.size)
        val first = playlist.entries[0] as PlaylistEntry.Song
        val middle = playlist.entries[1] as PlaylistEntry.Album
        val last = playlist.entries[2] as PlaylistEntry.Song
        assertEquals(a1.v363Uid, first.song.uid)
        assertEquals(albumB.uid, middle.album.uid)
        assertEquals(a2.v363Uid, last.song.uid)

        // Disc 1 tracks 1 and 2, then disc 1 with no track, then disc 2, then no disc at all.
        assertEquals(listOf("A1", "B1", "B2", "B3", "B4", "B5", "A2"), playlist.songs.names())
    }

    @Test
    fun unknownAlbum_dropsWithoutAffectingOtherEntries() {
        val missing = preAlbum("Not In Library")
        val playlist =
            loadPlaylist(
                EntryPointer.Album(missing.uid),
                EntryPointer.Song(a1.v363Uid),
                EntryPointer.Album(albumA.uid),
            )

        assertEquals(2, playlist.entries.size)
        assertEquals(listOf("A1", "A1", "A2"), playlist.songs.names())
    }

    @Test
    fun songOnlyPlaylist_isUnchanged() {
        val playlist = loadPlaylist(EntryPointer.Song(a2.v363Uid), EntryPointer.Song(b1.v400Uid))

        assertEquals(
            listOf(PlaylistEntry.Song::class, PlaylistEntry.Song::class),
            playlist.entries.map { it::class },
        )
        assertEquals(listOf("A2", "B1"), playlist.songs.names())
    }

    @Test
    fun sameAlbumTwice_givesTwoEntries() {
        val playlist = loadPlaylist(EntryPointer.Album(albumA.uid), EntryPointer.Album(albumA.uid))

        assertEquals(2, playlist.entries.size)
        assertEquals(listOf("A1", "A2", "A1", "A2"), playlist.songs.names())
    }

    private fun loadPlaylist(vararg pointers: EntryPointer): org.oxycblt.musikr.Playlist {
        val handle =
            mockk<PlaylistHandle> { every { uid } returns Music.UID.auxio(Music.UID.Item.PLAYLIST) }
        val prePlaylist = PrePlaylist(mockk(relaxed = true), "Playlist", handle, pointers.toList())
        val builder = MusicGraph.builder()
        allSongs.forEach { builder.add(it) }
        builder.add(prePlaylist)
        val library = LibraryFactory.new().create(builder.build(), mockk(), mockk())
        return library.playlists.single()
    }

    private fun List<Song>.names() = map { song ->
        allSongs.single { it.v363Uid == song.uid }.rawName
    }

    private fun preAlbum(name: String) =
        PreAlbum(
            musicBrainzId = null,
            name = mockk(relaxed = true),
            rawName = name,
            releaseType = ReleaseType.Album(null),
            preArtists = PreArtistsFrom.Album(listOf(artist)),
        )

    private fun preSong(id: String, album: PreAlbum, disc: Int?, track: Int?): PreSong {
        val name = mockk<Name.Known>(relaxed = true)
        return PreSong(
            v363Uid = Music.UID.auxio(Music.UID.Item.SONG) { update("v363 $id") },
            v400Uid = Music.UID.auxio(Music.UID.Item.SONG) { update("v400 $id") },
            v401Uid = Music.UID.auxio(Music.UID.Item.SONG) { update("v401 $id") },
            musicBrainzId = null,
            name = name,
            rawName = id,
            track = track,
            disc = disc?.let { Disc(it, null) },
            date = null,
            uri = mockk(relaxed = true),
            path = mockk(relaxed = true),
            format = mockk(relaxed = true),
            size = 0,
            durationMs = 1000,
            bitrateKbps = 0,
            sampleRateHz = 0,
            replayGainAdjustment = mockk(relaxed = true),
            modifiedMs = 0,
            addedMs = 0,
            cover = null,
            preAlbum = album,
            preArtists = listOf(artist),
            preGenres = emptyList(),
        )
    }
}
