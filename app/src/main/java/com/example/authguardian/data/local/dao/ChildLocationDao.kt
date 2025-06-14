package com.example.authguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.authguardian.data.local.entities.ChildLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildLocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: ChildLocationEntity)

    // Obtener todas las ubicaciones para un niño específico, ordenadas por más reciente primero
    @Query("SELECT * FROM child_locations WHERE childId = :childId ORDER BY timestamp DESC")
    fun getLocationsForChild(childId: String): Flow<List<ChildLocationEntity>>

    // Obtener la última ubicación conocida para un niño específico
    @Query("SELECT * FROM child_locations WHERE childId = :childId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLocationForChild(childId: String): ChildLocationEntity?

    // Obtener ubicaciones para un niño en un rango de tiempo
    @Query("SELECT * FROM child_locations WHERE childId = :childId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getLocationsInTimeRangeForChild(childId: String, startTime: Long, endTime: Long): Flow<List<ChildLocationEntity>>

    // Borrar todas las ubicaciones de un niño específico
    @Query("DELETE FROM child_locations WHERE childId = :childId")
    suspend fun deleteLocationsForChild(childId: String)

    // Borrar todas las ubicaciones (usar con precaución)
    @Query("DELETE FROM child_locations")
    suspend fun clearAllLocations()
}