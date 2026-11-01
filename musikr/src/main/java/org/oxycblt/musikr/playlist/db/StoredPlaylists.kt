/*
 * Copyright (c) 2024 Auxio Project
 * StoredPlaylists.kt is part of Auxio.
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

import android.content.Context
import android.util.Log
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.playlist.EntryPointer
import org.oxycblt.musikr.playlist.PlaylistEntryRef
import org.oxycblt.musikr.playlist.PlaylistFile
import org.oxycblt.musikr.playlist.PlaylistHandle

abstract class StoredPlaylists {
    /** Create a new playlist named [name] holding [entries], in order. */
    internal abstract suspend fun newWithEntries(
        name: String,
        entries: List<PlaylistEntryRef>,
    ): PlaylistHandle

    /** Create a new playlist named [name] holding [songs], one entry per song. */
    internal suspend fun new(name: String, songs: List<Song>): PlaylistHandle =
        newWithEntries(name, songs.map { PlaylistEntryRef.SongRef(it) })

    internal abstract suspend fun read(): List<PlaylistFile>

    companion object {
        fun from(context: Context): StoredPlaylists =
            StoredPlaylistsImpl(PlaylistDatabase.from(context).playlistDao())

        /** Wrap an existing [PlaylistDao], for tests. */
        internal fun from(playlistDao: PlaylistDao): StoredPlaylists =
            StoredPlaylistsImpl(playlistDao)
    }
}

private class StoredPlaylistsImpl(private val playlistDao: PlaylistDao) : StoredPlaylists() {
    override suspend fun newWithEntries(
        name: String,
        entries: List<PlaylistEntryRef>,
    ): PlaylistHandle {
        val info = PlaylistInfo(Music.UID.auxio(Music.UID.Item.PLAYLIST), name)
        playlistDao.insertPlaylist(info, entries.toRows(info.playlistUid))
        return StoredPlaylistHandle(info, playlistDao)
    }

    override suspend fun read() =
        playlistDao.readRawPlaylists().map {
            PlaylistFile(
                it.playlistInfo.name,
                it.entries.sortedBy { entry -> entry.id }.mapNotNull { entry -> entry.toPointer() },
                StoredPlaylistHandle(it.playlistInfo, playlistDao),
            )
        }

    private fun PlaylistEntry.toPointer(): EntryPointer? =
        when (entryType) {
            PlaylistEntry.TYPE_SONG -> EntryPointer.Song(entryUid)
            PlaylistEntry.TYPE_ALBUM -> EntryPointer.Album(entryUid)
            else -> {
                Log.w("StoredPlaylists", "Skipping playlist entry $id with unknown type $entryType")
                null
            }
        }
}
