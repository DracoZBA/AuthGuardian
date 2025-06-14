package com.example.authguardian;

import android.app.Application;
import dagger.hilt.android.HiltAndroidApp; // <-- IMPORTA ESTO

@HiltAndroidApp // <-- AÑADE ESTA ANOTACIÓN
public class AuthGuardianApplication extends Application {

    // Si no tienes un método onCreate, está bien.
    // Si lo tienes o lo necesitas para otra inicialización, mantenlo:
    // @Override
    // public void onCreate() {
    //     super.onCreate();
    //     // Otro código de inicialización si es necesario
    // }
}