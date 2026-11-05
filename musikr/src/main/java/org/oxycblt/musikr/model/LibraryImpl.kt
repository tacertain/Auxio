/*
 * Copyright (c) 2024 Auxio Project
 * LibraryImpl.kt is part of Auxio.
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

import org.oxycblt.musikr.Music
import org.oxycblt.musikr.MutableLibrary
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.PlaylistEntry
import org.oxycblt.musikr.fs.Path
import org.oxycblt.musikr.playlist.db.StoredPlaylists
import org.oxycblt.musikr.playlist.interpret.PlaylistInterpreter
import org.oxycblt.musikr.playlist.interpret.PrePlaylistInfo

internal data class LibraryImpl(
    override val songs: Set<SongImpl>,
    override val albums: Set<AlbumImpl>,
    override val artists: Set<ArtistImpl>,
    override val genres: Set<GenreImpl>,
    override val playlists: Set<PlaylistImpl>,
    private val storedPlaylists: StoredPlaylists,
    private val playlistInterpreter: PlaylistInterpreter,
) : MutableLibrary {
    private val songUidMap = songs.associateBy { it.uid }
    private val v400SongUidMap = songs.associateBy { it.v400Uid }
    private val v401SongUidMap = songs.associateBy { it.v401Uid }
    private val albumUidMap = albums.associateBy { it.uid }
    private val artistUidMap = artists.associateBy { it.uid }
    private val genreUidMap = genres.associateBy { it.uid }
    private val playlistUidMap = playlists.associateBy { it.uid }

    override fun empty() = songs.isEmpty()

    // Compat hack. See TagInterpreter for why this needs to be done
    override fun findSong(uid: Music.UID) =
        songUidMap[uid] ?: v400SongUidMap[uid] ?: v401SongUidMap[uid]

    override fun findSongByPath(path: Path) = songs.find { it.path == path }

    override fun findAlbum(uid: Music.UID) = albumUidMap[uid]

    override fun findArtist(uid: Music.UID) = artistUidMap[uid]

    override fun findGenre(uid: Music.UID) = genreUidMap[uid]

    override fun findPlaylist(uid: Music.UID) = playlistUidMap[uid]

    override fun findPlaylistByName(name: String) = playlists.find { it.name.raw == name }

    override suspend fun createPlaylistWithEntries(
        name: String,
        entries: List<PlaylistEntry>,
    ): MutableLibrary {
        val handle = storedPlaylists.newWithEntries(name, entries)
        val postPlaylist = playlistInterpreter.interpret(name, handle)
        val core = NewPlaylistCore(postPlaylist, entries)
        val playlist = PlaylistImpl(core)
        return copy(playlists = playlists + playlist)
    }

    override suspend fun renamePlaylist(playlist: Playlist, name: String): MutableLibrary {
        val playlistImpl =
            requireNotNull(playlistUidMap[playlist.uid]) {
                "Playlist to rename is not in this library"
            }
        val prePlaylist = playlistImpl.core.prePlaylist
        prePlaylist.handle.rename(name)
        val postPlaylist = playlistInterpreter.interpret(name, prePlaylist.handle)
        val core = NewPlaylistCore(postPlaylist, playlistImpl.entries)
        val newPlaylist = PlaylistImpl(core)
        return copy(playlists = playlists - playlistImpl + newPlaylist)
    }

    override suspend fun addEntriesToPlaylist(
        playlist: Playlist,
        entries: List<PlaylistEntry>,
    ): MutableLibrary {
        val playlistImpl =
            requireNotNull(playlistUidMap[playlist.uid]) {
                "Playlist to add to is not in this library"
            }
        playlistImpl.core.prePlaylist.handle.addEntries(entries)
        val core = NewPlaylistCore(playlistImpl.core.prePlaylist, playlistImpl.entries + entries)
        val newPlaylist = PlaylistImpl(core)
        return copy(playlists = playlists - playlistImpl + newPlaylist)
    }

    override suspend fun rewritePlaylistEntries(
        playlist: Playlist,
        entries: List<PlaylistEntry>,
    ): MutableLibrary {
        val playlistImpl =
            requireNotNull(playlistUidMap[playlist.uid]) {
                "Playlist to rewrite is not in this library"
            }
        playlistImpl.core.prePlaylist.handle.rewriteEntries(entries)
        val core = NewPlaylistCore(playlistImpl.core.prePlaylist, entries)
        val newPlaylist = PlaylistImpl(core)
        return copy(playlists = playlists - playlistImpl + newPlaylist)
    }

    override suspend fun deletePlaylist(playlist: Playlist): MutableLibrary {
        val playlistImpl =
            requireNotNull(playlistUidMap[playlist.uid]) {
                "Playlist to delete is not in this library"
            }
        playlistImpl.core.prePlaylist.handle.delete()
        return copy(playlists = playlists - playlistImpl)
    }

    private class NewPlaylistCore(
        override val prePlaylist: PrePlaylistInfo,
        override val entries: List<PlaylistEntry>,
    ) : PlaylistCore
}
