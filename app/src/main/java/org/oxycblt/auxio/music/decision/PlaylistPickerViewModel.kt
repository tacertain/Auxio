/*
 * Copyright (c) 2023 Auxio Project
 * PlaylistPickerViewModel.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.decision

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.oxycblt.auxio.R
import org.oxycblt.auxio.list.sort.Sort
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.PlaylistDecision
import org.oxycblt.auxio.music.PlaylistKind
import org.oxycblt.auxio.music.accepts
import org.oxycblt.auxio.music.containsAll
import org.oxycblt.auxio.music.findEntry
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.uid
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.PlaylistEntry
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.playlist.ExportConfig
import timber.log.Timber as L

/**
 * A [ViewModel] managing the state of the playlist picker dialogs.
 *
 * @author Alexander Capehart
 */
@HiltViewModel
class PlaylistPickerViewModel @Inject constructor(private val musicRepository: MusicRepository) :
    ViewModel(), MusicRepository.UpdateListener {
    /** A new [Playlist] having it's name chosen by the user. Null if none yet. */
    val currentPendingNewPlaylist: StateFlow<PendingNewPlaylist?>
        field = MutableStateFlow<PendingNewPlaylist?>(null)

    /** An existing [Playlist] that is being renamed. Null if none yet. */
    val currentPendingRenamePlaylist: StateFlow<PendingRenamePlaylist?>
        field = MutableStateFlow<PendingRenamePlaylist?>(null)

    /** An existing [Playlist] that is being exported. Null if none yet. */
    val currentPlaylistToExport: StateFlow<Playlist?>
        field = MutableStateFlow<Playlist?>(null)

    /** The current [ExportConfig] to use when exporting a playlist. */
    val currentExportConfig: StateFlow<ExportConfig>
        field = MutableStateFlow(DEFAULT_EXPORT_CONFIG)

    /** The current [Playlist] that needs it's deletion confirmed. Null if none yet. */
    val currentPlaylistToDelete: StateFlow<Playlist?>
        field = MutableStateFlow<Playlist?>(null)

    /** The users chosen name for [currentPendingNewPlaylist] or [currentPendingRenamePlaylist]. */
    val chosenName: StateFlow<ChosenName>
        field = MutableStateFlow<ChosenName>(ChosenName.Empty)

    /**
     * A batch of entries (all songs or all albums) to add to a playlist chosen by the user. Null if
     * none yet.
     */
    val currentEntriesToAdd: StateFlow<List<PlaylistEntry>?>
        field = MutableStateFlow<List<PlaylistEntry>?>(null)

    /**
     * The [Playlist]s that [currentEntriesToAdd] could be added to: empty playlists and those of
     * the same [PlaylistKind].
     */
    val playlistAddChoices: StateFlow<List<PlaylistChoice>>
        field = MutableStateFlow<List<PlaylistChoice>>(listOf())

    init {
        musicRepository.addUpdateListener(this)
    }

    override fun onMusicChanges(changes: MusicRepository.Changes) {
        var refreshChoicesWith: List<PlaylistEntry>? = null
        val library = musicRepository.library
        if (changes.deviceLibrary && library != null) {
            currentPendingNewPlaylist.value =
                currentPendingNewPlaylist.value?.let { pendingPlaylist ->
                    PendingNewPlaylist(
                        pendingPlaylist.preferredName,
                        pendingPlaylist.entries.mapNotNull { library.findEntry(it.uid) },
                        pendingPlaylist.template,
                        pendingPlaylist.reason,
                    )
                }
            L.d("Updated pending playlist: ${currentPendingNewPlaylist.value?.preferredName}")

            currentEntriesToAdd.value =
                currentEntriesToAdd.value?.let { pendingEntries ->
                    pendingEntries
                        .mapNotNull { library.findEntry(it.uid) }
                        .ifEmpty { null }
                        .also { refreshChoicesWith = it }
                }
            L.d("Updated entries to add: ${currentEntriesToAdd.value?.size} entries")
        }

        val chosenName = chosenName.value
        if (changes.userLibrary) {
            when (chosenName) {
                is ChosenName.Valid -> updateChosenName(chosenName.value)
                is ChosenName.AlreadyExists -> updateChosenName(chosenName.prior)
                else -> {
                    // Nothing to do.
                }
            }
            L.d("Updated chosen name to $chosenName")
            refreshChoicesWith = refreshChoicesWith ?: currentEntriesToAdd.value

            // TODO: Add music syncing for other playlist states here

            currentPlaylistToExport.value =
                currentPlaylistToExport.value?.let { playlist ->
                    musicRepository.library?.findPlaylist(playlist.uid)
                }
            L.d("Updated playlist to export to ${currentPlaylistToExport.value}")
        }

        refreshChoicesWith?.let(::refreshPlaylistChoices)
    }

    override fun onCleared() {
        musicRepository.removeUpdateListener(this)
    }

    /**
     * Set a new [currentPendingNewPlaylist] from a new batch of pending song or album [Music.UID]s.
     *
     * @param context [Context] required to generate a playlist name.
     * @param entryUids The [Music.UID]s of the songs or albums to be present in the playlist.
     * @param reason The reason the playlist is being created.
     */
    fun setPendingPlaylist(
        context: Context,
        entryUids: Array<Music.UID>,
        template: String?,
        reason: PlaylistDecision.New.Reason,
    ) {
        L.d("Opening ${entryUids.size} entries to create a playlist from")
        val library = musicRepository.library ?: return
        val entries =
            musicRepository.library
                ?.let { entryUids.mapNotNull(it::findEntry) }
                ?.also(::refreshPlaylistChoices)

        val possibleName =
            musicRepository.library?.let {
                // Attempt to generate a unique default name for the playlist, like "Playlist 1".
                var i = 1
                var possibleName: String
                do {
                    possibleName = context.getString(R.string.fmt_def_playlist, i)
                    L.d("Trying $possibleName as a playlist name")
                    ++i
                } while (library.playlists.any { it.name.resolve(context) == possibleName })
                L.d("$possibleName is unique, using it as the playlist name")
                possibleName
            }

        currentPendingNewPlaylist.value =
            if (possibleName != null && entries != null) {
                PendingNewPlaylist(possibleName, entries, template, reason)
            } else {
                L.w("Given song UIDs to create were invalid")
                null
            }
    }

    /**
     * Set a new [currentPendingRenamePlaylist] from a [Playlist] [Music.UID].
     *
     * @param playlistUid The [Music.UID]s of the [Playlist] to rename.
     */
    fun setPlaylistToRename(
        playlistUid: Music.UID,
        applySongUids: Array<Music.UID>,
        template: String?,
        reason: PlaylistDecision.Rename.Reason,
    ) {
        L.d("Opening playlist $playlistUid to rename")
        val playlist = musicRepository.library?.findPlaylist(playlistUid)
        val applySongs = musicRepository.library?.let { applySongUids.mapNotNull(it::findSong) }

        currentPendingRenamePlaylist.value =
            if (playlist != null && applySongs != null) {
                PendingRenamePlaylist(playlist, applySongs, template, reason)
            } else {
                L.w("Given playlist UID to rename was invalid")
                null
            }
    }

    /**
     * Set a new [currentPlaylistToExport] from a [Playlist] [Music.UID].
     *
     * @param playlistUid The [Music.UID] of the [Playlist] to export.
     */
    fun setPlaylistToExport(playlistUid: Music.UID) {
        L.d("Opening playlist $playlistUid to export")
        // TODO: Add this guard to the rest of the methods here
        if (currentPlaylistToExport.value?.uid == playlistUid) return
        currentPlaylistToExport.value = musicRepository.library?.findPlaylist(playlistUid)
        if (currentPlaylistToExport.value == null) {
            L.w("Given playlist UID to export was invalid")
        } else {
            currentExportConfig.value = DEFAULT_EXPORT_CONFIG
        }
    }

    /**
     * Update [currentExportConfig] based on new user input.
     *
     * @param exportConfig The new [ExportConfig] to use.
     */
    fun setExportConfig(exportConfig: ExportConfig) {
        L.d("Setting export config to $exportConfig")
        currentExportConfig.value = exportConfig
    }

    /**
     * Set a new [currentPendingNewPlaylist] from a new [Playlist] [Music.UID].
     *
     * @param playlistUid The [Music.UID] of the [Playlist] to delete.
     */
    fun setPlaylistToDelete(playlistUid: Music.UID) {
        L.d("Opening playlist $playlistUid to delete")
        currentPlaylistToDelete.value = musicRepository.library?.findPlaylist(playlistUid)
        if (currentPlaylistToDelete.value == null) {
            L.w("Given playlist UID to delete was invalid")
        }
    }

    /**
     * Update the current [chosenName] based on new user input.
     *
     * @param name The new user-inputted name, or null if not present.
     */
    fun updateChosenName(name: String?) {
        L.d("Updating chosen name to $name")
        chosenName.value =
            when {
                name.isNullOrEmpty() -> {
                    L.e("Chosen name is empty")
                    ChosenName.Empty
                }
                name.isBlank() -> {
                    L.e("Chosen name is blank")
                    ChosenName.Blank
                }
                else -> {
                    val trimmed = name.trim()
                    val library = musicRepository.library
                    if (library != null && library.findPlaylistByName(trimmed) == null) {
                        L.d("Chosen name is valid")
                        ChosenName.Valid(trimmed)
                    } else {
                        L.d("Chosen name already exists in library")
                        ChosenName.AlreadyExists(trimmed)
                    }
                }
            }
    }

    /**
     * Set a new [currentEntriesToAdd] from a new batch of pending song or album [Music.UID]s.
     *
     * @param entryUids The [Music.UID]s of the songs or albums to add to a playlist.
     */
    fun setEntriesToAdd(entryUids: Array<Music.UID>) {
        L.d("Opening ${entryUids.size} entries to add to a playlist")
        currentEntriesToAdd.value =
            musicRepository.library
                ?.let { entryUids.mapNotNull(it::findEntry).ifEmpty { null } }
                ?.also(::refreshPlaylistChoices)
        if (
            currentEntriesToAdd.value == null || entryUids.size != currentEntriesToAdd.value?.size
        ) {
            L.w("Given entry UIDs to add were (partially) invalid")
        }
    }

    private fun refreshPlaylistChoices(entries: List<PlaylistEntry>) {
        val library = musicRepository.library ?: return
        L.d("Refreshing playlist choices")
        playlistAddChoices.value =
            Sort(Sort.Mode.ByName, Sort.Direction.ASCENDING)
                .playlists(library.playlists.filter { it.accepts(entries) })
                .map { PlaylistChoice(it, it.containsAll(entries)) }
    }

    private companion object {
        private val DEFAULT_EXPORT_CONFIG = ExportConfig(absolute = false, windowsPaths = false)
    }
}

/**
 * Represents a playlist that will be created as soon as a name is chosen.
 *
 * @param preferredName The name to be used by default if no other name is chosen.
 * @param entries The entries (all songs or all albums) to be contained in the [PendingNewPlaylist]
 * @param reason The reason the playlist is being created.
 * @author Alexander Capehart (OxygenCobalt)
 */
data class PendingNewPlaylist(
    val preferredName: String,
    val entries: List<PlaylistEntry>,
    val template: String?,
    val reason: PlaylistDecision.New.Reason,
)

data class PendingRenamePlaylist(
    val playlist: Playlist,
    val applySongs: List<Song>,
    val template: String?,
    val reason: PlaylistDecision.Rename.Reason,
)

/**
 * Represents the (processed) user input from the playlist naming dialogs.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
sealed interface ChosenName {
    /** The current name is valid. */
    data class Valid(val value: String) : ChosenName

    /** The current name already exists. */
    data class AlreadyExists(val prior: String) : ChosenName

    /** The current name is empty. */
    data object Empty : ChosenName

    /** The current name only consists of whitespace. */
    data object Blank : ChosenName
}

/**
 * An individual [Playlist] choice to add [Song]s to.
 *
 * @param playlist The [Playlist] represented.
 * @param alreadyAdded Whether the songs currently pending addition have already been added to the
 *   [Playlist].
 * @author Alexander Capehart (OxygenCobalt)
 */
data class PlaylistChoice(val playlist: Playlist, val alreadyAdded: Boolean)
