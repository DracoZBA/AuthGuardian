package com.example.authguardian.di

import android.content.Context
import androidx.room.Room
import com.example.authguardian.data.local.AppDatabase
import com.example.authguardian.data.local.dao.HeartRateDao
import com.example.authguardian.data.local.dao.GyroscopeDao
import com.example.authguardian.data.local.dao.MeltdownDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.example.authguardian.data.local.dao.GeofenceDao

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "auth_guardian_db"
        ).fallbackToDestructiveMigration() // Solo para desarrollo, quitar en prod
            .build()
    }

    @Singleton
    @Provides
    fun provideHeartRateDao(database: AppDatabase): HeartRateDao {
        return database.heartRateDao()
    }

    @Singleton
    @Provides
    fun provideGyroscopeDao(database: AppDatabase): GyroscopeDao {
        return database.gyroscopeDao()
    }

    @Singleton
    @Provides
    fun provideMeltdownDao(database: AppDatabase): MeltdownDao {
        return database.meltdownDao()
    }

    @Singleton
    @Provides
    fun provideGeofenceDao(database: AppDatabase): GeofenceDao {
        return database.geofenceDao()
    }
}