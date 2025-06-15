package com.example.authguardian.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.authguardian.data.repository.AuthRepository
import com.example.authguardian.data.repository.DataRepository
import com.example.authguardian.data.remote.FirebaseDataSource // Importa FirebaseDataSource
import com.example.authguardian.util.GeofenceHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Singleton
    @Provides
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Singleton
    @Provides
    fun provideFirebaseDataSource(firestore: FirebaseFirestore): FirebaseDataSource {
        return FirebaseDataSource(firestore)
    }

    @Singleton
    @Provides
    fun provideAuthRepository(
        firebaseAuth: FirebaseAuth,
        firebaseDataSource: FirebaseDataSource // Ahora usa FirebaseDataSource
    ): AuthRepository {
        return AuthRepository(firebaseAuth, firebaseDataSource)
    }

    @Singleton
    @Provides
    fun provideGeofenceHelper(@ApplicationContext context: Context): GeofenceHelper {
        return GeofenceHelper(context)
    }

    @Singleton
    @Provides
    fun provideDataRepository(
        @ApplicationContext context: Context,
        firebaseDataSource: FirebaseDataSource, // Inyecta el DataSource
        appDatabase: com.example.authguardian.data.local.AppDatabase,
        geofenceHelper: GeofenceHelper,
        heartRateDao: com.example.authguardian.data.local.dao.HeartRateDao,
        gyroscopeDao: com.example.authguardian.data.local.dao.GyroscopeDao,
        meltdownDao: com.example.authguardian.data.local.dao.MeltdownDao,
        childLocationDao: com.example.authguardian.data.local.dao.ChildLocationDao,
        geofenceDao: com.example.authguardian.data.local.dao.GeofenceDao
    ): DataRepository {
        return DataRepository(
            context,
            firebaseDataSource, // Pasa el DataSource
            appDatabase,
            geofenceHelper,
            heartRateDao,
            gyroscopeDao,
            meltdownDao,
            childLocationDao,
            geofenceDao
        )
    }
}