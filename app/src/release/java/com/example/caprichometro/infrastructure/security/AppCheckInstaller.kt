package com.example.caprichometro.infrastructure.security

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Instala Play Integrity como proveedor de App Check en compilaciones release.
 */
internal object AppCheckInstaller {
    fun install(context: Context) {
        FirebaseApp.initializeApp(context)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
