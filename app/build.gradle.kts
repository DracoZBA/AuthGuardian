plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("androidx.navigation.safeargs.kotlin")
}

android {
    namespace = "com.example.authguardian"
    compileSdk = 34 // Recomendado usar la última versión estable (34 en lugar de 35)

    defaultConfig {
        applicationId = "com.example.authguardian"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["runnerBuilder"] = "de.mannodermaus.junit5.AndroidJUnit5Builder"
        buildConfigField("String", "GEMINI_API_KEY", "\"AIzaSyDApRKXP8o9BodUCIkV7Ijbdcp7u23ado4\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // Actualizado a Java 17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }


}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    // Google Play Services
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // MQTT
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1") {
        exclude(group = "com.android.support", module = "support-v4")
    }

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")

    // MPAndroidChart (requiere JitPack)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

        // Jetpack Compose - Añade estas líneas
    val composeBomVersion = "2024.05.00" // Revisa la última versión estable del BOM aquí: https://developer.android.com/jetpack/compose/bom/versioning
    implementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    androidTestImplementation(platform("androidx.compose:compose-bom:$composeBomVersion")) // Para pruebas de Compose

        // Elige UNO de los siguientes para Material Design (Material 3 es el recomendado para nuevos proyectos)
    implementation("androidx.compose.material3:material3")
        // O si prefieres Material Design 2 (menos común para nuevos proyectos)
        // implementation("androidx.compose.material:material")

        // Necesario para @Composable, Layouts, Gráficos, etc.
    implementation("androidx.compose.ui:ui")

        // Herramientas para previsualizaciones de Compose en Android Studio
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

        // Integración de Compose con Activities (si usas setContent { } en una Activity)
    implementation("androidx.activity:activity-compose:1.9.0") // Revisa la última versión

        // Integración de Compose con ViewModels (opcional, pero común)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0") // Revisa la última versióN

    implementation("com.patrykandpatrick.vico:compose:1.13.1") // Reemplaza con la última versión
    implementation("com.patrykandpatrick.vico:core:1.13.1")    // Reemplaza con la última versión
    // Si necesitas diferentes renderers (e.g., para Material Design 3)
    implementation("com.patrykandpatrick.vico:compose-m3:1.13.1") // Reemplaza con la última versión
    //IA
    implementation("com.google.ai.client.generativeai:generativeai:0.8.0") // O la versión más reciente


    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.navigation:navigation-testing:2.7.7")
    debugImplementation("androidx.fragment:fragment-testing:1.6.2")
}

kapt {
    correctErrorTypes = true
}