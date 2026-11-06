# Album playlists: design

Status: approved design, not yet implemented. Written 2026-10-27 against `dev` at c05cebc5.
Owner: Andrew (tacertain). Companion working notes: [`CLAUDE.md`](../CLAUDE.md) at the repo root.

## Goal

A playlist may contain whole albums as entries, alongside individual songs. An album entry
plays as that album's tracks in disc-then-track order, shows as one row on the playlist page,
and is reordered or removed as a unit. Shuffling such a playlist shuffles the order of the
entries and keeps each album's tracks in order.

This is a fork feature. The upstream maintainer has declined album-grouped queues
(OxygenCobalt/Auxio#221), so upstreaming is not a goal. Keeping upstream merges easy is: keep
changes additive, in new files where possible, and avoid reformatting untouched code.

## Decisions already made

| # | Question | Decision |
| --- | --- | --- |
| 1 | Track order inside an album entry | Disc then track, computed in `musikr`. The app's album song sort setting is ignored. |
| 2 | Partial removal of an album entry | Not supported. Whole entry only. |
| 3 | Playlist sort dialog on a playlist with album entries | Hidden. |
| 4 | Room schema export | Turn `exportSchema = true` on for `musikr` and commit the JSON under `musikr/schemas/`. Write a real migration 75 to 76 even though no existing playlists need protecting; a migration test is optional. |
| 5 | Artist and genre "Add to playlist" | Keep flattening to songs. |
| 6 | Shuffle | Album-grouped: entries shuffled, tracks within an album kept in order. |

## How playlists flow today

Seven hops from SQLite to screen. `musikr` paths are under
`musikr/src/main/java/org/oxycblt/musikr/`, app paths under `app/src/main/java/org/oxycblt/auxio/`.

1. **Storage.** `playlist/db/RawPlaylist.kt`: entities `PlaylistInfo` (playlist UID, name),
   `PlaylistSong` (song UID), `PlaylistSongCrossRef` (auto-increment id, playlist UID, song UID).
   Order comes from the cross-ref row id. `playlist/db/PlaylistDatabase.kt`: schema version 75,
   one hand-written `Migration(30, 75)`, and `fallbackToDestructiveMigration(true)`.
2. **Read and write.** `playlist/db/StoredPlaylists.kt` maps rows to `PlaylistFile(name,
   List<SongPointer>, handle)`. `playlist/db/StoredPlaylistHandle.kt` implements
   `PlaylistHandle` (`rename`, `add`, `rewrite`, `delete`). `playlist/PlaylistFile.kt` defines
   `SongPointer.UID`.
3. **Interpretation.** `playlist/interpret/PlaylistInterpreter.kt` wraps a file as `PrePlaylist`
   (name, raw name, handle, pointers). `PostPlaylist` is the pointer-less form produced by mutations.
4. **Graph.** `graph/MusicGraph.kt`: `PlaylistVertex` has `songVertices: Array<SongVertex?>`
   indexed by pointer position and a `pointerMap`. In `build()` every song is matched under three
   UID generations (v363, v400, v401). Unmatched pointers stay null.
5. **Library.** `model/LibraryFactory.kt` builds `PlaylistImpl(PlaylistVertexCore(vertex))`;
   `songs` is `songVertices.mapNotNull { tag(it) }`, so missing songs drop silently.
   `model/PlaylistImpl.kt` derives duration and covers from `songs`.
6. **Mutation.** `model/LibraryImpl.kt`: `createPlaylist`, `renamePlaylist`, `addToPlaylist`,
   `rewritePlaylist`, `deletePlaylist`; each calls the handle, builds `NewPlaylistCore`, and
   returns a copied library. `Library.kt` is the public interface.
7. **App.** `music/MusicRepository.kt` forwards mutations and emits `Changes(userLibrary = true)`.
   `music/MusicViewModel.kt` has `addToPlaylist` overloads; the album overload flattens to
   `listSettings.albumSongSort.songs(album.songs)`. `music/decision/AddToPlaylistDialog.kt` and
   `PlaylistPickerViewModel.kt` carry song UIDs through nav args (`res/navigation/inner.xml`,
   `songUids` arguments at lines 417 and 474). `detail/DetailGenerator.kt` renders a playlist as
   one `DetailSection.Songs`. `detail/DetailViewModel.kt` keeps
   `editedPlaylist: StateFlow<List<Song>?>` with move, remove, sort, save.
   `detail/list/PlaylistDetailListAdapter.kt` has `EditHeader` and `PlaylistSongViewHolder`
   (`item_editable_song.xml`). `detail/PlaylistDetailFragment.kt` wires drag, swipe-delete, edit toolbar.

Playback reads only `playlist.songs` (`playback/state/PlaybackCommand.kt`,
`playback/PlaybackViewModel.kt`), as do `music/service/MusicBrowser.kt` and M3U export. Reusable
pieces: `list/recycler/ViewHolders.kt` has `AlbumViewHolder` on `item_parent.xml`;
`DetailGenerator` already emits `DetailSection.Albums` for artists.

## Data model and migration

Replace the song-only cross-ref with a generic entry table. UIDs already encode their item
kind (`Music.UID.Item`: `s` song, `a` album), so one `entryUid` column can hold either; store
`entryType` anyway so queries do not depend on parsing.

### Schema, version 76 (`playlist/db/RawPlaylist.kt`)

| Entity | Columns | Change |
| --- | --- | --- |
| `PlaylistInfo` | `playlistUid` (PK), `name` | Unchanged |
| `PlaylistEntry` | `id` (PK, auto-increment), `playlistUid` (indexed), `entryType` (0 = song, 1 = album), `entryUid` (indexed) | Replaces `PlaylistSongCrossRef` |
| `PlaylistSong` | `songUid` (PK) | Dropped; it only existed for the junction |

`RawPlaylist` becomes `@Embedded PlaylistInfo` plus
`@Relation(parentColumn = "playlistUid", entityColumn = "playlistUid") val entries: List<PlaylistEntry>`.
Room does not guarantee relation row order, so sort `entries` by `id` in `StoredPlaylists.read()`.

### Pointers and refs (`playlist/PlaylistFile.kt`)

- `SongPointer` becomes `EntryPointer` with `Song(uid)` and `Album(uid)`. `PlaylistFile.songPointers`
  becomes `entryPointers`.
- New public sealed type `PlaylistEntryRef` with `SongRef(song)` and `AlbumRef(album)`, used by the
  app to say "this album, as one entry".
- `PlaylistHandle` gains `add(entries: List<PlaylistEntryRef>)` and `rewrite(entries)`. Keep the
  song-based `add(songs)` and `rewrite(songs)` as default wrappers so existing callers compile.
- `StoredPlaylistHandle` maps `SongRef` to `PlaylistEntry(entryType = 0, entryUid = song.uid)` and
  `AlbumRef` to `(1, album.uid)`. `StoredPlaylists.read()` maps rows back by `entryType` and skips
  unknown types rather than crashing.

### Migration 75 to 76 (`playlist/db/PlaylistDatabase.kt`)

Add next to the existing `Migration(30, 75)` object; both must stay registered so 30 → 75 → 76 chains.

```sql
CREATE TABLE PlaylistEntry (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  playlistUid TEXT NOT NULL,
  entryType INTEGER NOT NULL,
  entryUid TEXT NOT NULL
);
CREATE INDEX index_PlaylistEntry_playlistUid ON PlaylistEntry(playlistUid);
CREATE INDEX index_PlaylistEntry_entryUid ON PlaylistEntry(entryUid);
INSERT INTO PlaylistEntry (playlistUid, entryType, entryUid)
  SELECT playlistUid, 0, songUid FROM PlaylistSongCrossRef ORDER BY id;
DROP TABLE PlaylistSongCrossRef;
DROP TABLE PlaylistSong;
```

Room validates migrated tables against the entities at open and throws on any mismatch. Turn on
`exportSchema = true` first, build, read the generated `musikr/schemas/.../76.json`, and make the
SQL match it (column order, `NOT NULL`, index names), not the other way round.

## Expansion at load

### Graph (`graph/MusicGraph.kt`)

`PlaylistVertex.songVertices` becomes `entryVertices: Array<Vertex?>` with separate pointer maps
for song and album pointers. The existing song loop fills song slots. Add an album pass after
albums are finalised: map `preAlbum.uid` to `AlbumVertex` (albums have a single UID, no versioned
history, see `tag/interpret/PreMusic.kt`) and fill album slots. Unmatched albums stay null and drop.

### Library factory and model

- `model/LibraryFactory.kt`: `PlaylistVertexCore` builds `entries` (`PlaylistEntry.Song` or
  `PlaylistEntry.Album` per resolved slot) and `songs` (album entries flattened in disc-then-track order).
- `model/PlaylistImpl.kt`: add an `albumTrackOrder` comparator on `Song.disc` then `Song.track`,
  nulls last, then name. `musikr` has no `Sort` class; do not import the app's.
- `Music.kt`: add to the public API

  ```kotlin
  sealed interface PlaylistEntry {
      data class Song(val song: org.oxycblt.musikr.Song) : PlaylistEntry
      data class Album(val album: org.oxycblt.musikr.Album) : PlaylistEntry
  }

  interface Playlist : MusicParent {
      val entries: List<PlaylistEntry>
      override val songs: List<Song>   // entries flattened; contract unchanged
      ...
  }
  ```

- `PlaylistImpl.equals` and `hashCode` compare `entries`, not `songs`.
- `Library.kt` and `model/LibraryImpl.kt`: add `createPlaylist(name, entries)`,
  `addToPlaylist(playlist, entries)`, `rewritePlaylist(playlist, entries)`; the song-based forms
  become wrappers. `NewPlaylistCore` takes entries and flattens with the same comparator.

Nothing in playback, `MusicBrowser`, `MediaSessionInterface`, search, home lists, cover stacking
or the persisted playing-parent UID needs to change; verify with
`grep -rn "\.songs" app/src/main/java | grep -i playlist` after the model change.

## UI

All under `app/src/main/java/org/oxycblt/auxio/`.

### Adding an album as one entry

- `MusicViewModel.addToPlaylist(album)` passes the album through as an entry instead of flattening.
- `PlaylistDecision.Add` and the nav args (`res/navigation/inner.xml`, `AddToPlaylistDialog`,
  `NewPlaylistDialog`) switch from `songUids` to `entryUids: Array<Music.UID>`.
  `PlaylistPickerViewModel` resolves each UID with `library.findSong(uid) ?: library.findAlbum(uid)`
  and rebuilds `List<PlaylistEntryRef>`. One dialog, one route, both cases.
- `PlaylistPickerViewModel.currentSongsToAdd` becomes `currentEntriesToAdd`;
  `PendingNewPlaylist.songs` becomes `entries`. The `onMusicChanges` resync uses the same lookup.
- `MusicRepository.addToPlaylist` and `createPlaylist` gain entry overloads.
- Artist and genre adds keep flattening to songs.

### Playlist detail page

- `DetailGenerator.playlist` emits a new `DetailSection.PlaylistEntries(playlist.entries)`.
- `PlaylistDetailListAdapter` gains `PlaylistAlbumViewHolder` on a new `item_editable_album.xml`
  (mirror `item_editable_song.xml`: cover, name, artist and track count, drag handle, menu button).
  Register `VIEW_TYPE_PLAYLIST_ALBUM` in `IntegerTable.kt`. Diff callbacks compare entries.
- Album row: tap opens album detail; menu is the existing `R.menu.album` via `Menu.ForAlbum`;
  playing indicator lights when the current song's album matches and the parent is this playlist.
- Header count, duration and the stacked cover derive from `songs` and stay correct.

### Editing

- `DetailViewModel.editedPlaylist` becomes `StateFlow<List<PlaylistEntry>?>`. Move, remove and save
  are index-based and need only the type change; save calls `rewritePlaylist(playlist, entries)`.
  `PlaylistDragCallback` and swipe-delete carry over.
- Hide the sort action (`PlaylistSongSortDialog`, `applyPlaylistSongSort`) while the playlist
  contains an album entry.
- Selection mode already works on `Music`, so a selected album row behaves as an album.

## Album-grouped shuffle

### Today

The queue is a heap of songs plus `shuffledMapping`, a permutation of heap indices
(`playback/state/PlaybackStateHolder.kt`, `RawQueue`). `playback/service/BetterShuffleOrder.kt`
generates it (`createShuffledList(length, startIndex)`, uniform random with the start song first).
`playback/service/ExoPlaybackStateHolder.kt` applies it in `newPlayback` (shuffled command),
`shuffled(true)` (user toggles shuffle on), and `applySavedState` (replays the persisted mapping).
Insertions and removals patch the permutation via `cloneAndInsert` and `cloneAndRemove`. The mapping
is persisted as-is, so a grouped permutation needs no persistence change.

### Design

- New file `playback/service/GroupedShuffle.kt`:
  `fun shuffleGroups(parent: MusicParent?, heap: List<Song>): IntArray?`. Returns null unless
  `parent` is a `Playlist` with at least one album entry and `heap == parent.songs` element for
  element. Otherwise returns group sizes in heap order: album entry → its track count, song entry → 1.
- `BetterShuffleOrder` gains a constructor `(groupSizes: IntArray, startIndex: Int)` backed by
  `createGroupedShuffledList`: shuffle the groups as units, keep order within each group, rotate the
  group containing `startIndex` to the front. Playback starts at `startIndex`; the earlier tracks of
  that album sit before it and are reachable with previous, as in unshuffled play.
- `ExoPlaybackStateHolder.newPlayback` and `shuffled(true)` call `shuffleGroups(parent, heap)` and
  use the grouped constructor when non-null, else the existing one. `applySavedState` is unchanged.
- After any queue edit the heap no longer equals the playlist, so the next toggle falls back to
  per-song shuffle. Intended: an edited queue is no longer "the playlist". Note it in the changelog.
- Shuffle off restores heap order (playlist order, albums intact). No UI changes; the shuffle button,
  "Remember shuffle", media session and Android Auto all go through the same two paths.

Unit tests: `createGroupedShuffledList` (groups contiguous, internal order kept, start group first,
valid permutation) and `shuffleGroups` (null for song-only playlist, null for edited heap).

## M3U

Last PR, optional. In `musikr/.../playlist/m3u/M3U.kt`:

- Export iterates `playlist.entries`; before an album entry's first track write
  `#EXT-X-AUXIO-ALBUM:<album uid string>`, then the tracks in the normal per-song format. Other
  players ignore unknown `#` lines.
- Import: extend `ImportedPlaylist` with `albumMarkers: Map<Int, Music.UID>` (path index → album
  UID). After the app resolves paths to songs (`MusicViewModel.importPlaylist`), collapse a run of
  songs starting at a marker whose songs all belong to that album into one album entry. Unresolvable
  UIDs leave the songs as songs.

## Testing

`musikr` has JUnit 4, MockK, Robolectric 4.16 and `androidx.test:core-ktx` as `testImplementation`.
`musikr/src/test/java/org/oxycblt/musikr/cache/db/MutableDBCacheWriteAllTest.kt` shows the
Room-under-Robolectric pattern. CI runs `./gradlew spotlessCheck` and `./gradlew musikr:testDebug`.
There is no `androidTest` source set; do not add one.

- `playlist/db/StoredPlaylistsTest.kt`: in-memory DB; create song, album, song; read back three
  pointers in order with the right kinds; rewrite and add; read again.
- `graph/MusicGraphPlaylistTest.kt`: `PreSong` fixtures across two albums plus a `PrePlaylist` of
  `[song A1, album B, song A2]`; assert three entries and 1 + |B| + 1 songs in disc-then-track order;
  a non-matching album UID drops without affecting the others. See `tag/parse/TagParserTest.kt`
  and `tag/interpret/NamingTest.kt` for fixture style.
- `playback/service/GroupedShuffleTest.kt` in `app`: see the shuffle section. Check that `app` has
  a unit-test source set and JUnit dependency before writing it; add `testImplementation
  "junit:junit:4.13.2"` to `app/build.gradle` if not.
- `m3u/M3UExportTest.kt`: marker precedes the album's first track; file still parses as a flat list.
- Optional: `PlaylistDatabaseMigrationTest.kt` with Room's `MigrationTestHelper` once schemas are exported.

Manual checks on the phone after each PR:

- [ ] Add an album from its menu; open the playlist; see one album row.
- [ ] Play the playlist; the queue is the album's tracks in order; the indicator lands on the album row.
- [ ] Edit: drag the album row above a song, remove a song, save; reopen and confirm.
- [ ] Shuffle: albums play whole, in random order; toggle off restores playlist order.
- [ ] Rescan the library; the album entry survives.
- [ ] Remove an album's files, rescan; the entry is gone, no crash.
- [ ] Export to M3U, import into a new playlist; the album comes back as one entry.
- [ ] Android Auto or a Bluetooth media browser still lists and plays the playlist.

## Implementation sequence

Branch `album-playlists` from `dev`; each row is committed directly to it on `tacertain/Auxio`
(no PRs; the "PR" numbers below just name the steps). Each step must build (`./gradlew app:packageDebug`), pass `spotlessCheck` and `musikr:testDebug`,
and install on the phone.

| PR | Scope | Main files | Days | Done when |
| --- | --- | --- | --- | --- |
| 1 Storage | `PlaylistEntry`, DAO, `EntryPointer`, `PlaylistEntryRef`, migration 75→76, `exportSchema = true`, DAO test | `musikr/.../playlist/db/*`, `playlist/PlaylistFile.kt` | 2 | Tests pass; UI unchanged |
| 2 Expansion | graph album resolution, `Playlist.entries`, flattening comparator, entry-based `Library` mutations, graph test | `musikr/.../graph/MusicGraph.kt`, `model/*`, `Music.kt`, `Library.kt` | 3 | Hand-inserted `entryType = 1` row plays the album |
| 3 Add album | album menu adds an entry, nav args carry entry UIDs, picker resolves song or album, repository overloads | `app/.../music/*`, `res/navigation/inner.xml` | 2 | Add from album menu, play, tracks in order |
| 4 Detail and edit | `DetailSection.PlaylistEntries`, album row, playing indicator, `editedPlaylist` as entries, sort hidden | `app/.../detail/*`, `res/layout/item_editable_album.xml`, `IntegerTable.kt` | 4 | Album rows show; drag, remove, save round-trip |
| 5 Grouped shuffle | `GroupedShuffle.kt`, grouped `BetterShuffleOrder`, two call sites, tests | `app/.../playback/service/*` | 2 | Shuffle plays whole albums in random order |
| 6 M3U and polish | export marker, import collapse, strings, a11y, changelog | `musikr/.../playlist/m3u/*`, `app/.../music/MusicViewModel.kt`, `res/values/strings.xml`, `CHANGELOG.md` | 3 | All manual checks pass |

Usable after PR 3. Total 16 days without slack, 22 realistic.

## Risks

- **Room schema validation.** A column, nullability or index-name mismatch between the migration SQL
  and the entities throws at open. Generate the schema JSON first and write SQL to match.
- **Destructive fallback stays.** `fallbackToDestructiveMigration(true)` remains, so a wrong migration
  wipes playlists on real devices. Andrew has none today, but test the install-over-old-build path anyway.
- **Equality and diffing.** `PlaylistImpl.equals` and the adapter diff callbacks must compare entries.
- **Album UID changes on retag.** An album's UID hashes name and album artists unless MusicBrainz-tagged;
  retagging drops the entry on the next scan, as retagging a song does today.
- **Grouped shuffle after queue edits** silently reverts to per-song shuffle on the next toggle.
- **Upstream drift.** `MusicGraph.kt`, `PlaylistDetailListAdapter.kt` and `ExoPlaybackStateHolder.kt`
  are the three upstream-active files this touches. Keep edits there small and prefer new files.

## References

- Feasibility assessment: https://claude.ai/artifact/KUUdxPPGTASxCJ24KJTeZ2
- Plan with discussion history: https://claude.ai/artifact/QY9nDGCUL6ryBGL3zovG6J
- Upstream issues: https://github.com/OxygenCobalt/Auxio/issues/221 (album shuffle, declined),
  https://github.com/OxygenCobalt/Auxio/issues/1282 (per-album playlists as a workaround)
