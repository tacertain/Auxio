/*
 * Copyright (c) 2023 Auxio Project
 * PlaylistImpl.kt is part of Auxio.
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
 
package org.oxycblt.musikr.model

import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.PlaylistEntry
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.covers.CoverCollection
import org.oxycblt.musikr.playlist.interpret.PrePlaylistInfo
import org.oxycblt.musikr.tag.Name

internal interface PlaylistCore {
    val prePlaylist: PrePlaylistInfo
    val entries: List<PlaylistEntry>
}

internal class PlaylistImpl(val core: PlaylistCore) : Playlist {
    override val uid = core.prePlaylist.handle.uid
    override val name: Name.Known = core.prePlaylist.name
    override val entries = core.entries
    override val songs = entries.flattenToSongs()
    override val durationMs = songs.sumOf { it.durationMs }
    override val covers = CoverCollection.from(songs.mapNotNull { it.cover })

    private var hashCode =
        31 * (31 * uid.hashCode() + core.prePlaylist.hashCode()) + entries.hashCode()

    override fun equals(other: Any?) =
        other is PlaylistImpl &&
            core.prePlaylist == other.core.prePlaylist &&
            entries == other.entries

    override fun hashCode() = hashCode

    override fun toString() = "Playlist(uid=$uid, name=$name)"
}

/**
 * The order of tracks inside an album entry: disc, then track number, with missing numbers last,
 * then name. This deliberately ignores the app's album sort setting.
 */
internal val albumTrackOrder: Comparator<Song> =
    compareBy<Song, Int?>(nullsLast()) { it.disc?.number }
        .thenBy(nullsLast()) { it.track }
        .thenBy { it.name }

/**
 * Expand each album entry to its tracks in disc-then-track order, keeping entry order. This is how
 * [Playlist.songs] is derived from [Playlist.entries].
 */
fun List<PlaylistEntry>.flattenToSongs(): List<Song> = flatMap {
    when (it) {
        is PlaylistEntry.Song -> listOf(it.song)
        is PlaylistEntry.Album -> it.album.songs.sortedWith(albumTrackOrder)
    }
}
