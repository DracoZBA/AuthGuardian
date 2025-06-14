package com.example.authguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.authguardian.data.local.entities.HeartRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartRateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeartRate(heartRate: HeartRateEntity)

    @Query("SELECT * FROM heart_rate_data WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    fun getLatestHeartRates(userId: String, limit: Int): Flow<List<HeartRateEntity>>

    @Query("SELECT * FROM heart_rate_data WHERE userId = :userId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getHeartRatesInTimeRange(userId: String, startTime: Long, endTime: Long): Flow<List<HeartRateEntity>>
}