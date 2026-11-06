/*
 * Copyright (c) 2026 Auxio Project
 * GroupedShuffleTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.service

import io.mockk.every
import io.mockk.mockk
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.PlaylistEntry
import org.oxycblt.musikr.Song

class GroupedShuffleTest {
    @Test
    fun groupedList_isPermutationWithGroupsWholeAndInOrder() {
        val sizes = intArrayOf(3, 1, 4, 2, 1)
        repeat(200) { seed ->
            val shuffled = createGroupedShuffledList(sizes, -1, Random(seed))
            assertEquals((0 until 11).toList(), shuffled.sorted())
            assertGroupsIntact(sizes, shuffled)
        }
    }

    @Test
    fun groupedList_startGroupComesFirst() {
        val sizes = intArrayOf(3, 1, 4, 2)
        repeat(200) { seed ->
            // Index 6 is the third track of the third group (indices 4..7).
            val shuffled = createGroupedShuffledList(sizes, 6, Random(seed))
            assertArrayEquals(intArrayOf(4, 5, 6, 7), shuffled.copyOfRange(0, 4))
            assertGroupsIntact(sizes, shuffled)
        }
    }

    @Test
    fun groupedList_actuallyShuffles() {
        val sizes = IntArray(6) { 2 }
        val orders = (0 until 50).map { createGroupedShuffledList(sizes, -1, Random(it)).toList() }
        assertTrue(orders.toSet().size > 1)
    }

    @Test
    fun shuffleGroups_albumPlaylist_givesEntrySizes() {
        val a = songs(3)
        val b = songs(2)
        val single = song()
        val playlist = playlist(album(a), PlaylistEntry.Song(single), album(b))
        assertArrayEquals(intArrayOf(3, 1, 2), shuffleGroups(playlist, a + single + b))
    }

    @Test
    fun shuffleGroups_songOnlyPlaylist_isNull() {
        val s = songs(3)
        val playlist = playlist(*s.map { PlaylistEntry.Song(it) }.toTypedArray())
        assertNull(shuffleGroups(playlist, s))
    }

    @Test
    fun shuffleGroups_editedQueue_isNull() {
        val a = songs(3)
        val playlist = playlist(album(a))
        assertNull(shuffleGroups(playlist, a + song()))
        assertNull(shuffleGroups(playlist, a.reversed()))
    }

    @Test
    fun shuffleGroups_notAPlaylist_isNull() {
        val a = songs(3)
        assertNull(shuffleGroups(mockk<Album>(), a))
        assertNull(shuffleGroups(null, a))
    }

    private fun assertGroupsIntact(sizes: IntArray, shuffled: IntArray) {
        var offset = 0
        for (size in sizes) {
            val first = shuffled.indexOf(offset)
            for (i in 0 until size) {
                assertEquals(offset + i, shuffled[first + i])
            }
            offset += size
        }
    }

    private fun song() = mockk<Song>()

    private fun songs(n: Int) = List(n) { song() }

    private fun album(songs: List<Song>) =
        PlaylistEntry.Album(mockk<Album> { every { this@mockk.songs } returns songs })

    private fun playlist(vararg entries: PlaylistEntry): Playlist {
        val flat = entries.flatMap {
            when (it) {
                is PlaylistEntry.Song -> listOf(it.song)
                is PlaylistEntry.Album -> it.album.songs
            }
        }
        return mockk {
            every { this@mockk.entries } returns entries.toList()
            every { songs } returns flat
        }
    }
}
