package com.anibiar.app.social

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Public client config of the "kiro" Firebase project (the same values that live in google-services.json).
 * Access is enforced by the Realtime Database rules, not by hiding these.
 */
object FirebaseConfig {
    private const val API_KEY = "AIzaSyB4v23J2UbauJPfJ4joxpYW28rcarETr8Y"
    private const val APP_ID = "1:888661303175:android:cf1025f3468d8ec2af7d22"
    private const val PROJECT_ID = "kiro-c0fae"
    const val DB_URL = "https://kiro-c0fae-default-rtdb.firebaseio.com"

    fun init(context: Context) {
        if (FirebaseApp.getApps(context).isNotEmpty()) return
        FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApiKey(API_KEY)
                .setApplicationId(APP_ID)
                .setProjectId(PROJECT_ID)
                .setDatabaseUrl(DB_URL)
                .build(),
        )
    }
}
