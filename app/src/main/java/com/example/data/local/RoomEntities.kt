package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: String,
    val originalUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val downloadedAt: Long,
    val mediaType: String,
    val fileSizeBytes: Long,
    val status: String
)

@Entity(tableName = "downloaded_media")
data class DownloadedMediaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val fileName: String,
    val mediaType: String,
    val mimeType: String,
    val platformId: String,
    val fileSizeBytes: Long,
    val uriString: String,
    val isMediaStoreUri: Boolean,
    val originalUrl: String,
    val sourceMediaUrl: String,
    val thumbnailUrl: String?,
    val createdAt: Long
)

@Dao
interface DownloadHistoryDao {
    @Query("SELECT * FROM download_history ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<DownloadHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadHistoryEntity): Long

    @Query("UPDATE download_history SET status = :status, fileSizeBytes = :sizeBytes WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, sizeBytes: Long)

    @Query("DELETE FROM download_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM download_history")
    suspend fun clearAll()
}

@Dao
interface DownloadedMediaDao {
    @Query("SELECT * FROM downloaded_media ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadedMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedMediaEntity)

    @Query("UPDATE downloaded_media SET title = :newTitle, fileName = :newFileName WHERE id = :id")
    suspend fun updateTitle(id: String, newTitle: String, newFileName: String)

    @Query("DELETE FROM downloaded_media WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM downloaded_media")
    fun observeCount(): Flow<Int>
}

@Database(
    entities = [DownloadHistoryEntity::class, DownloadedMediaEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadHistoryDao(): DownloadHistoryDao
    abstract fun downloadedMediaDao(): DownloadedMediaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mediasaver_pro.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
