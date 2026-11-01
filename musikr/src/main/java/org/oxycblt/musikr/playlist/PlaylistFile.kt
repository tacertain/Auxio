/*
 * Copyright (c) 2024 Auxio Project
 * PlaylistFile.kt is part of Auxio.
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
 
package org.oxycblt.musikr.playlist

import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song

internal data class PlaylistFile(
    val name: String,
    val entryPointers: List<EntryPointer>,
    val handle: PlaylistHandle,
)

/** A reference to one entry of a stored playlist, before it is resolved against the library. */
internal sealed interface EntryPointer {
    /** The [Music.UID] of the song or album this pointer refers to. */
    val uid: Music.UID

    /** A single song, which may match any of the song's historical UIDs. */
    data class Song(override val uid: Music.UID) : EntryPointer

    /** A whole album, played as its tracks in disc-then-track order. */
    data class Album(override val uid: Music.UID) : EntryPointer
    //    data class Path(val options: List<Path>) : EntryPointer
}

internal interface PlaylistHandle {
    val uid: Music.UID

    suspend fun rename(name: String)

    /** Append [entries] to the end of the playlist. */
    suspend fun addEntries(entries: List<PlaylistEntryRef>)

    /** Replace the contents of the playlist with [entries]. */
    suspend fun rewriteEntries(entries: List<PlaylistEntryRef>)

    /** Append [songs] to the end of the playlist, one entry per song. */
    suspend fun add(songs: List<Song>) = addEntries(songs.map { PlaylistEntryRef.SongRef(it) })

    /** Replace the contents of the playlist with [songs], one entry per song. */
    suspend fun rewrite(songs: List<Song>) =
        rewriteEntries(songs.map { PlaylistEntryRef.SongRef(it) })

    suspend fun delete()
}
