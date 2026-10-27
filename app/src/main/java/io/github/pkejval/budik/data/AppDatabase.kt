package io.github.pkejval.budik.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute, id")
    fun observeAll(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms")
    suspend fun all(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): Alarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: Alarm): Long

    @Delete
    suspend fun delete(alarm: Alarm)
}

@Dao
interface IntegrationDao {
    @Query("SELECT * FROM integrations ORDER BY name, id")
    fun observeAll(): Flow<List<Integration>>

    @Query("SELECT * FROM integrations WHERE enabled = 1")
    suspend fun enabled(): List<Integration>

    @Query("SELECT * FROM integrations WHERE id = :id")
    suspend fun get(id: Long): Integration?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(integration: Integration): Long

    @Delete
    suspend fun delete(integration: Integration)
}

@Database(entities = [Alarm::class, Integration::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun integrations(): IntegrationDao

    companion object {
        fun create(context: Context) =
            Room.databaseBuilder(context, AppDatabase::class.java, "budik.db").build()
    }
}
