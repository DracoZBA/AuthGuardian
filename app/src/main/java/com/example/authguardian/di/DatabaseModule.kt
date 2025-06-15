package com.example.authguardian.di

import android.content.Context
import androidx.room.Room
import com.example.authguardian.data.local.AppDatabase
import com.example.authguardian.data.local.dao.ChildLocationDao
import com.example.authguardian.data.local.dao.GeofenceDao
import com.example.authguardian.data.local.dao.GyroscopeDao
import com.example.authguardian.data.local.dao.HeartRateDao
import com.example.authguardian.data.local.dao.MeltdownDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "aura_guardian_db"
        )
            // Solo para desarrollo. En producción, gestiona las migraciones correctamente.
            // .fallbackToDestructiveMigration()
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
    fun provideChildLocationDao(database: AppDatabase): ChildLocationDao {
        return database.childLocationDao()
    }

    @Singleton
    @Provides
    fun provideGeofenceDao(database: AppDatabase): GeofenceDao {
        return database.geofenceDao()
    }
}