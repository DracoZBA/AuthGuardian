package com.example.authguardian.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.authguardian.data.remote.FirebaseDataSource
import com.example.authguardian.data.repository.AuthRepository
import com.example.authguardian.data.repository.DataRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.example.authguardian.util.GeofenceHelper

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
    fun provideFirebaseDataSource(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore
    ): FirebaseDataSource {
        return FirebaseDataSource(auth, firestore)
    }

    @Singleton
    @Provides
    fun provideAuthRepository(firebaseDataSource: FirebaseDataSource): AuthRepository {
        return AuthRepository(firebaseDataSource)
    }

    @Singleton
    @Provides
    fun provideDataRepository(
        @ApplicationContext context: Context,
        firestore: FirebaseFirestore,
        appDatabase: com.example.authguardian.data.local.AppDatabase,
        geofenceHelper: GeofenceHelper // <--- 1. AÑADE GeofenceHelper COMO PARÁMETRO AQUÍ
    ): DataRepository {
        return DataRepository(context, firestore, appDatabase, geofenceHelper) // <--- 2. PÁSALE geofenceHelper AL CONSTRUCTOR
    }

    @Singleton
    @Provides
    fun provideGeofenceHelper(@ApplicationContext context: Context): GeofenceHelper {
        return GeofenceHelper(context)
    }
}