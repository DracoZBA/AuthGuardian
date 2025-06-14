package com.example.authguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.authguardian.data.local.entities.MeltdownEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeltdownDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeltdownEvent(meltdownEvent: MeltdownEntity): Long // Devolver el ID insertado

    @Update
    suspend fun updateMeltdownEvent(meltdownEvent: MeltdownEntity)

    // Obtener un evento específico por su ID
    @Query("SELECT * FROM meltdown_events WHERE id = :eventId")
    fun getMeltdownEventById(eventId: Long): Flow<MeltdownEntity?> // Puede ser nulo si no se encuentra

    // Obtener todos los eventos para un niño específico, ordenados por fecha (eventTimestamp)
    @Query("SELECT * FROM meltdown_events WHERE childId = :childId ORDER BY eventTimestamp DESC")
    fun getAllMeltdownEventsForChild(childId: String): Flow<List<MeltdownEntity>>

    // Obtener eventos para un niño en un rango de fechas
    @Query("SELECT * FROM meltdown_events WHERE childId = :childId AND eventTimestamp BETWEEN :startTime AND :endTime ORDER BY eventTimestamp DESC")
    fun getMeltdownEventsInTimeRangeForChild(childId: String, startTime: Long, endTime: Long): Flow<List<MeltdownEntity>>

    // Obtener los N eventos más recientes para un niño
    @Query("SELECT * FROM meltdown_events WHERE childId = :childId ORDER BY eventTimestamp DESC LIMIT :limit")
    fun getLatestMeltdownEventsForChild(childId: String, limit: Int): Flow<List<MeltdownEntity>>

    @Query("DELETE FROM meltdown_events WHERE id = :eventId")
    suspend fun deleteMeltdownEventById(eventId: Long)

    @Query("DELETE FROM meltdown_events WHERE childId = :childId")
    suspend fun deleteAllMeltdownEventsForChild(childId: String)
}