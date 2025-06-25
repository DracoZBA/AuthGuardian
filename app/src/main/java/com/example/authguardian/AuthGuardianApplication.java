package com.example.authguardian;

import android.app.Application;
import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class AuthGuardianApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Aquí puedes realizar inicializaciones globales si es necesario.
    }
}