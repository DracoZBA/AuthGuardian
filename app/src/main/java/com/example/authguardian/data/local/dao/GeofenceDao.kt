package com.example.authguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.example.authguardian.data.local.entities.GeofenceEntity

@Dao
interface GeofenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeofence(geofence: GeofenceEntity)

    @Query("SELECT * FROM geofences WHERE userId = :userId")
    fun getGeofencesForUser(userId: String): Flow<List<GeofenceEntity>>

    @Query("DELETE FROM geofences WHERE id = :geofenceId")
    suspend fun deleteGeofence(geofenceId: String)
}