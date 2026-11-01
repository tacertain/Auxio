/*
 * Copyright (c) 2023 Auxio Project
 * PlaylistDatabase.kt is part of Auxio.
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

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.getStringOrNull
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import org.oxycblt.musikr.Music

/**
 * Allows persistence of all user-created music information.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@Database(
    entities = [PlaylistInfo::class, PlaylistEntry::class],
    version = 76,
    exportSchema = true,
)
@TypeConverters(Music.UID.TypeConverters::class)
internal abstract class PlaylistDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    companion object {
        fun from(context: Context) =
            Room.databaseBuilder(
                    context.applicationContext,
                    PlaylistDatabase::class.java,
                    "user_music.db",
                )
                .addMigrations(
                    object : Migration(30, 75) {
                        private fun washUids(
                            db: SupportSQLiteDatabase,
                            table: String,
                            column: String,
                        ) {
                            val playlistUidMigration =
                                db.query(
                                        SupportSQLiteQueryBuilder.builder(table)
                                            .columns(arrayOf(column))
                                            .create()
                                    )
                                    .use {
                                        val migrations = mutableListOf<Pair<String, String>>()
                                        val playlistUidIndex = it.getColumnIndexOrThrow(column)
                                        while (it.moveToNext()) {
                                            val oldUid =
                                                requireNotNull(
                                                    it.getStringOrNull(playlistUidIndex)
                                                ) {
                                                    "catastrophic migration failure: $table has no UID"
                                                }
                                            val newUid =
                                                requireNotNull(Music.UID.fromString(oldUid)) {
                                                        "catastrophic migration failure: $table has bad UID"
                                                    }
                                                    .toString()
                                            migrations.add(oldUid to newUid)
                                        }
                                        migrations
                                    }
                            for ((old, new) in playlistUidMigration) {
                                db.update(
                                    table,
                                    SQLiteDatabase.CONFLICT_REPLACE,
                                    ContentValues().apply {
                                        put(column, old)
                                    },
                                    "$column = ?",
                                    arrayOf(new),
                                )
                            }
                            for ((old, new) in playlistUidMigration) {
                                db.update(
                                    table,
                                    SQLiteDatabase.CONFLICT_REPLACE,
                                    ContentValues().apply {
                                        put(column, new)
                                    },
                                    "$column = ?",
                                    arrayOf(old),
                                )
                            }
                        }

                        override fun migrate(db: SupportSQLiteDatabase) {
                            washUids(db, "PlaylistInfo", "playlistUid")
                            washUids(db, "PlaylistSongCrossRef", "playlistUid")
                        }
                    },
                    MIGRATION_75_76,
                )
                .fallbackToDestructiveMigration(true)
                .build()

        /**
         * Replaces the song-only `PlaylistSongCrossRef` junction (and the `PlaylistSong` table it
         * needed) with `PlaylistEntry`, which can hold songs or whole albums. Existing rows become
         * song entries in their original order. The SQL matches `schemas/.../76.json`.
         */
        internal val MIGRATION_75_76 =
            object : Migration(75, 76) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `PlaylistEntry` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`playlistUid` TEXT NOT NULL, " +
                            "`entryType` INTEGER NOT NULL, " +
                            "`entryUid` TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_PlaylistEntry_playlistUid` " +
                            "ON `PlaylistEntry` (`playlistUid`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_PlaylistEntry_entryUid` " +
                            "ON `PlaylistEntry` (`entryUid`)"
                    )
                    db.execSQL(
                        "INSERT INTO `PlaylistEntry` (`playlistUid`, `entryType`, `entryUid`) " +
                            "SELECT `playlistUid`, ${PlaylistEntry.TYPE_SONG}, `songUid` " +
                            "FROM `PlaylistSongCrossRef` ORDER BY `id`"
                    )
                    db.execSQL("DROP TABLE IF EXISTS `PlaylistSongCrossRef`")
                    db.execSQL("DROP TABLE IF EXISTS `PlaylistSong`")
                }
            }
    }
}

// TODO: Handle playlist defragmentation? I really don't want dead songs to accumulate in this
//  database.

/**
 * The DAO for persisted playlist information.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@Dao
internal abstract class PlaylistDao {
    /**
     * Read out all playlists stored in the database.
     *
     * @return A list of [RawPlaylist] representing each playlist stored.
     */
    @Transaction
    @Query("SELECT * FROM PlaylistInfo")
    abstract suspend fun readRawPlaylists(): List<RawPlaylist>

    /**
     * Create a new playlist.
     *
     * @param info The [PlaylistInfo] of the new playlist.
     * @param entries The [PlaylistEntry] rows of the new playlist, in order.
     */
    @Transaction
    open suspend fun insertPlaylist(info: PlaylistInfo, entries: List<PlaylistEntry>) {
        insertInfo(info)
        insertEntries(entries)
    }

    /**
     * Replace the currently-stored [PlaylistInfo] for a playlist entry.
     *
     * @param playlistInfo The new [PlaylistInfo] to store.
     */
    @Transaction
    open suspend fun replacePlaylistInfo(playlistInfo: PlaylistInfo) {
        deleteInfo(playlistInfo.playlistUid)
        insertInfo(playlistInfo)
    }

    /**
     * Delete a playlist's [PlaylistInfo] and [PlaylistEntry] rows.
     *
     * @param playlistUid The [Music.UID] of the playlist to delete.
     */
    @Transaction
    open suspend fun deletePlaylist(playlistUid: Music.UID) {
        deleteInfo(playlistUid)
        deleteEntries(playlistUid)
    }

    /**
     * Append entries to the end of a playlist.
     *
     * @param entries The [PlaylistEntry] rows to append, in order.
     */
    @Transaction
    open suspend fun insertPlaylistEntries(entries: List<PlaylistEntry>) {
        insertEntries(entries)
    }

    /**
     * Replace the currently stored entries of the given playlist.
     *
     * @param playlistUid The [Music.UID] of the playlist to update.
     * @param entries The new [PlaylistEntry] rows of the playlist, in order.
     */
    @Transaction
    open suspend fun replacePlaylistEntries(playlistUid: Music.UID, entries: List<PlaylistEntry>) {
        deleteEntries(playlistUid)
        insertEntries(entries)
    }

    /** Internal, do not use. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertInfo(info: PlaylistInfo)

    /** Internal, do not use. */
    @Query("DELETE FROM PlaylistInfo where playlistUid = :playlistUid")
    abstract suspend fun deleteInfo(playlistUid: Music.UID)

    /** Internal, do not use. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertEntries(entries: List<PlaylistEntry>)

    /** Internal, do not use. */
    @Query("DELETE FROM PlaylistEntry where playlistUid = :playlistUid")
    abstract suspend fun deleteEntries(playlistUid: Music.UID)
}
