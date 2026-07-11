package com.minigum

import android.app.Application
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MinigumApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialized explicitly (rather than relying solely on the google-services plugin's
        // auto-init) so Auth + Firestore are guaranteed ready before any Hilt-injected
        // singleton touches them. Requires google-services.json on the active flavor.
        FirebaseApp.initializeApp(this)
    }
}
