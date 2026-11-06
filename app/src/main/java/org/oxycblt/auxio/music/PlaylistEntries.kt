/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistEntries.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music

import org.oxycblt.musikr.Library
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.PlaylistEntry

/**
 * What a playlist holds. A playlist holds only songs or only albums, never a mix; an empty playlist
 * has no kind yet and accepts either.
 */
enum class PlaylistKind {
    SONGS,
    ALBUMS,
}

/** The [Music.UID] of the song or album this entry refers to. */
val PlaylistEntry.uid: Music.UID
    get() =
        when (this) {
            is PlaylistEntry.Song -> song.uid
            is PlaylistEntry.Album -> album.uid
        }

/** The song or album this entry refers to, as shown in a list. */
val PlaylistEntry.music: Music
    get() =
        when (this) {
            is PlaylistEntry.Song -> song
            is PlaylistEntry.Album -> album
        }

/** The [PlaylistKind] of these entries, or null if there are none. */
val List<PlaylistEntry>.kind: PlaylistKind?
    get() =
        when (firstOrNull()) {
            null -> null
            is PlaylistEntry.Song -> PlaylistKind.SONGS
            is PlaylistEntry.Album -> PlaylistKind.ALBUMS
        }

/** The [PlaylistKind] of this playlist, or null if it is empty. */
val Playlist.kind: PlaylistKind?
    get() = entries.kind

/** Whether [entries] can be added to this playlist without mixing songs and albums. */
fun Playlist.accepts(entries: List<PlaylistEntry>): Boolean {
    val mine = kind ?: return true
    return entries.all { it.kindOf() == mine }
}

/** Whether every one of [entries] is already in this playlist. */
fun Playlist.containsAll(entries: List<PlaylistEntry>): Boolean {
    val present = this.entries.mapTo(mutableSetOf()) { it.uid }
    return entries.all { it.uid in present }
}

/** Look up a song or album by [uid] and wrap it as a [PlaylistEntry]. */
fun Library.findEntry(uid: Music.UID): PlaylistEntry? =
    findSong(uid)?.let { PlaylistEntry.Song(it) } ?: findAlbum(uid)?.let { PlaylistEntry.Album(it) }

private fun PlaylistEntry.kindOf() =
    when (this) {
        is PlaylistEntry.Song -> PlaylistKind.SONGS
        is PlaylistEntry.Album -> PlaylistKind.ALBUMS
    }
