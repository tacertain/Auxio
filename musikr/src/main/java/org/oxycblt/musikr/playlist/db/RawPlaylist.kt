/*
 * Copyright (c) 2023 Auxio Project
 * RawPlaylist.kt is part of Auxio.
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

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import org.oxycblt.musikr.Music

/**
 * Raw playlist information persisted to [PlaylistDatabase].
 *
 * @param entries The playlist's entries. Room does not guarantee the order of relation rows, so
 *   sort by [PlaylistEntry.id] before use.
 * @author Alexander Capehart (OxygenCobalt)
 */
internal data class RawPlaylist(
    @Embedded val playlistInfo: PlaylistInfo,
    @Relation(parentColumn = "playlistUid", entityColumn = "playlistUid")
    val entries: List<PlaylistEntry>,
)

/**
 * UID and name information corresponding to a [RawPlaylist] entry.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@Entity internal data class PlaylistInfo(@PrimaryKey val playlistUid: Music.UID, val name: String)

/**
 * One entry of a playlist: a song or a whole album. Entries are ordered by [id].
 *
 * @param id Auto-generated row id, which also gives the entry's position in the playlist.
 * @param playlistUid The [Music.UID] of the playlist this entry belongs to.
 * @param entryType [TYPE_SONG] or [TYPE_ALBUM].
 * @param entryUid The [Music.UID] of the song or album.
 */
@Entity
internal data class PlaylistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(index = true) val playlistUid: Music.UID,
    val entryType: Int,
    @ColumnInfo(index = true) val entryUid: Music.UID,
) {
    companion object {
        /** [entryType] of a single song. */
        const val TYPE_SONG = 0

        /** [entryType] of a whole album. */
        const val TYPE_ALBUM = 1
    }
}
