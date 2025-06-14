package com.example.authguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.authguardian.data.local.entities.GyroscopeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GyroscopeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGyroscopeReading(gyroscopeReading: GyroscopeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllGyroscopeReadings(gyroscopeReadings: List<GyroscopeEntity>)

    // Obtener las últimas N lecturas, ordenadas por timestamp
    // Como no hay userId, esto obtiene las últimas N lecturas globales.
    @Query("SELECT * FROM gyroscope_readings ORDER BY timestamp DESC LIMIT :limit")
    fun getLatestGyroscopeReadings(limit: Int): Flow<List<GyroscopeEntity>>

    // Obtener lecturas en un rango de tiempo
    @Query("SELECT * FROM gyroscope_readings WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getGyroscopeReadingsInTimeRange(startTime: Long, endTime: Long): Flow<List<GyroscopeEntity>>

    // Obtener todas las lecturas (podría ser muchas, usar con precaución)
    @Query("SELECT * FROM gyroscope_readings ORDER BY timestamp DESC")
    fun getAllGyroscopeReadings(): Flow<List<GyroscopeEntity>>

    // Borrar todas las lecturas
    @Query("DELETE FROM gyroscope_readings")
    suspend fun clearAllGyroscopeReadings()

    // Borrar lecturas más antiguas que un timestamp específico
    @Query("DELETE FROM gyroscope_readings WHERE timestamp < :olderThanTimestamp")
    suspend fun clearOldGyroscopeReadings(olderThanTimestamp: Long)
}