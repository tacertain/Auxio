/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistAlbums.kt is part of Auxio.
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

import org.oxycblt.musikr.playlist.EntryPointer

/**
 * Fill the album slots of every playlist in [playlistVertices] with the matching vertex from
 * [albumVertices]. Must run after album clustering and melding, so each UID maps to its final
 * vertex. Album pointers that match nothing are left null and drop out of the playlist.
 */
internal fun resolvePlaylistAlbums(
    albumVertices: Collection<AlbumVertex>,
    playlistVertices: Collection<PlaylistVertex>,
) {
    if (playlistVertices.isEmpty()) return
    val albumsByUid = albumVertices.associateBy { it.preAlbum.uid }
    for (playlistVertex in playlistVertices) {
        for ((pointer, indices) in playlistVertex.pointerMap) {
            if (pointer !is EntryPointer.Album) continue
            val albumVertex = albumsByUid[pointer.uid] ?: continue
            indices.forEach { playlistVertex.entryVertices[it] = albumVertex }
        }
    }
}
