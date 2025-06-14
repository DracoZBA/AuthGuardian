package com.example.authguardian.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.authguardian.data.local.dao.GyroscopeDao
import com.example.authguardian.data.local.dao.HeartRateDao
import com.example.authguardian.data.local.dao.MeltdownDao
import com.example.authguardian.data.local.dao.ChildLocationDao
import com.example.authguardian.data.local.dao.GeofenceDao
import com.example.authguardian.data.local.entities.ChildLocationEntity
import com.example.authguardian.data.local.entities.GyroscopeEntity
import com.example.authguardian.data.local.entities.HeartRateEntity
import com.example.authguardian.data.local.entities.MeltdownEntity
import com.example.authguardian.data.local.entities.GeofenceEntity
@Database(
    entities = [
        HeartRateEntity::class,
        GyroscopeEntity::class,
        MeltdownEntity::class,
        GeofenceEntity::class,
        ChildLocationEntity::class
    ],
    version = 1, // Incrementa la versión si cambias el esquema
    exportSchema = false
)
// @TypeConverters(DateConverter::class) // Ejemplo si usas Date o tipos complejos
abstract class AppDatabase : RoomDatabase() {
    abstract fun heartRateDao(): HeartRateDao
    abstract fun gyroscopeDao(): GyroscopeDao
    abstract fun meltdownDao(): MeltdownDao
    abstract fun childLocationDao(): ChildLocationDao
    abstract fun geofenceDao(): GeofenceDao
}