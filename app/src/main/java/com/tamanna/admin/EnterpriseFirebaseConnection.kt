package com.tamanna.admin

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore

object EnterpriseFirebaseConnection {
    private const val APP_NAME = "tamanna-enterprise"

    private const val PROJECT_ID = "tamanna-enterprise"
    private const val APPLICATION_ID = "1:241311598063:android:2bb4814ada61a01b89221a"
    private const val API_KEY = "AIzaSyCf1KiISrf68kLyABkcptkpz47Yrwu8KEQ"
    private const val STORAGE_BUCKET = "tamanna-enterprise.firebasestorage.app"

    @Synchronized
    fun getFirestore(context: Context): FirebaseFirestore {
        val app = FirebaseApp.getApps(context)
            .firstOrNull { it.name == APP_NAME }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId(PROJECT_ID)
                    .setApplicationId(APPLICATION_ID)
                    .setApiKey(API_KEY)
                    .setStorageBucket(STORAGE_BUCKET)
                    .build(),
                APP_NAME
            )
            ?: throw IllegalStateException("Tamanna Enterprise Firebase App initialize করা যায়নি।")

        return FirebaseFirestore.getInstance(app)
    }
}
