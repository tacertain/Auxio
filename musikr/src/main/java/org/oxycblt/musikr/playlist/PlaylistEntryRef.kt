/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistEntryRef.kt is part of Auxio.
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

import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Song

/**
 * One entry to write into a playlist: either a single song or a whole album. An album entry is
 * stored as the album itself, not as its songs, and is expanded to its tracks when the library is
 * loaded.
 */
sealed interface PlaylistEntryRef {
    /** A single [Song] entry. */
    data class SongRef(val song: Song) : PlaylistEntryRef

    /** A whole-[Album] entry. */
    data class AlbumRef(val album: Album) : PlaylistEntryRef
}
