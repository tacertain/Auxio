/*
 * Copyright (c) 2024 Auxio Project
 * StoredPlaylistHandle.kt is part of Auxio.
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

import org.oxycblt.musikr.Music
import org.oxycblt.musikr.PlaylistEntry as LibraryEntry
import org.oxycblt.musikr.playlist.PlaylistHandle

internal class StoredPlaylistHandle(
    private val playlistInfo: PlaylistInfo,
    private val playlistDao: PlaylistDao,
) : PlaylistHandle {
    override val uid = playlistInfo.playlistUid

    override suspend fun rename(name: String) {
        playlistDao.replacePlaylistInfo(playlistInfo.copy(name = name))
    }

    override suspend fun rewriteEntries(entries: List<LibraryEntry>) {
        playlistDao.replacePlaylistEntries(uid, entries.toRows(uid))
    }

    override suspend fun addEntries(entries: List<LibraryEntry>) {
        playlistDao.insertPlaylistEntries(entries.toRows(uid))
    }

    override suspend fun delete() {
        playlistDao.deletePlaylist(uid)
    }
}

/** Convert library [LibraryEntry]s to the [PlaylistEntry] rows that store them in [playlistUid]. */
internal fun List<LibraryEntry>.toRows(playlistUid: Music.UID) = map {
    when (it) {
        is LibraryEntry.Song ->
            PlaylistEntry(
                playlistUid = playlistUid,
                entryType = PlaylistEntry.TYPE_SONG,
                entryUid = it.song.uid,
            )
        is LibraryEntry.Album ->
            PlaylistEntry(
                playlistUid = playlistUid,
                entryType = PlaylistEntry.TYPE_ALBUM,
                entryUid = it.album.uid,
            )
    }
}
