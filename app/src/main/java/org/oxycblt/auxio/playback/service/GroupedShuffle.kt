/*
 * Copyright (c) 2026 Auxio Project
 * GroupedShuffle.kt is part of Auxio.
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

import kotlin.random.Random
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.PlaylistEntry
import org.oxycblt.musikr.Song

/**
 * Work out how a queue should be grouped when shuffled. An album playlist shuffles its albums as
 * units and keeps each album's tracks in order, so the result is the size of each group in queue
 * order: an album entry's track count, or 1 for a song entry.
 *
 * Returns null, meaning "shuffle songs individually", unless [parent] is a [Playlist] with at least
 * one album entry and [heap] is still exactly that playlist's songs. Once the queue has been edited
 * it is no longer the playlist, and shuffling falls back to per-song.
 *
 * @param parent The parent the queue was started from.
 * @param heap The unshuffled queue.
 */
fun shuffleGroups(parent: MusicParent?, heap: List<Song>): IntArray? {
    if (parent !is Playlist) return null
    if (parent.entries.none { it is PlaylistEntry.Album }) return null
    if (heap != parent.songs) return null
    return parent.entries
        .map {
            when (it) {
                is PlaylistEntry.Song -> 1
                is PlaylistEntry.Album -> it.album.songs.size
            }
        }
        .toIntArray()
}

/**
 * Create a shuffled permutation of queue indices that shuffles contiguous groups as units and keeps
 * the order inside each group. If [startIndex] is not -1, the group containing it goes first.
 *
 * @param groupSizes The size of each group, in queue order. Must add up to the queue length.
 * @param startIndex The queue index playback starts at, or -1 for none.
 * @param random The source of randomness, for tests.
 */
fun createGroupedShuffledList(
    groupSizes: IntArray,
    startIndex: Int,
    random: Random = Random.Default,
): IntArray {
    val groups = mutableListOf<IntRange>()
    var offset = 0
    for (size in groupSizes) {
        groups.add(offset until offset + size)
        offset += size
    }
    groups.shuffle(random)
    if (startIndex != -1) {
        val startGroup = groups.indexOfFirst { startIndex in it }
        if (startGroup > 0) {
            groups.add(0, groups.removeAt(startGroup))
        }
    }
    return groups.flatMap { it.toList() }.toIntArray()
}
