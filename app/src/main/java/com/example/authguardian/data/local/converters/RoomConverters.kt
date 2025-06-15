package com.example.authguardian.data.local.converters

import androidx.room.TypeConverter
import com.google.firebase.Timestamp

class RoomConverters {
    @TypeConverter
    fun fromTimestamp(value: Timestamp?): Long? {
        return value?.seconds
    }

    @TypeConverter
    fun toTimestamp(value: Long?): Timestamp? {
        return value?.let { Timestamp(it, 0) }
    }
}