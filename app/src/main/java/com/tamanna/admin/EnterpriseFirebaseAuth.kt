package com.tamanna.admin

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

object EnterpriseFirebaseAuth {

    fun authorizeAdmin(
        context: Context,
        idToken: String,
        email: String
    ): Task<Boolean> {
        return try {
            val app = EnterpriseFirebaseConnection.getFirestore(context).app
            val auth = FirebaseAuth.getInstance(app)
            val credential = GoogleAuthProvider.getCredential(idToken, null)

            auth.signInWithCredential(credential).continueWithTask { signInTask ->
                if (!signInTask.isSuccessful) {
                    return@continueWithTask Tasks.forResult(false)
                }

                val user = auth.currentUser
                    ?: return@continueWithTask Tasks.forResult(false)

                if (user.email?.trim()?.equals(email.trim(), ignoreCase = true) != true) {
                    auth.signOut()
                    return@continueWithTask Tasks.forResult(false)
                }

                ensureAdminRegistry(auth, user.uid, email)
            }
        } catch (e: Exception) {
            Tasks.forResult(false)
        }
    }

    private fun ensureAdminRegistry(
        auth: FirebaseAuth,
        uid: String,
        email: String
    ): Task<Boolean> {
        val firestore = FirebaseFirestore.getInstance(auth.app)
        val adminConfig = firestore.collection("appConfig").document("admin")
        val accessUser = firestore.collection("accessUsers").document(uid)

        return adminConfig.get(Source.SERVER).continueWithTask { configTask ->
            if (!configTask.isSuccessful) {
                return@continueWithTask Tasks.forResult(false)
            }

            val config = configTask.result
            if (config.exists()) {
                val serverUid = config.getString("uid").orEmpty()
                val serverEmail = config.getString("email").orEmpty()
                val role = config.getString("role").orEmpty()

                return@continueWithTask Tasks.forResult(
                    serverUid == uid &&
                        serverEmail.equals(email, ignoreCase = true) &&
                        role == "ADMIN"
                )
            }

            val adminData = hashMapOf(
                "uid" to uid,
                "email" to email,
                "role" to "ADMIN"
            )

            accessUser.set(
                hashMapOf(
                    "uid" to uid,
                    "email" to email,
                    "role" to "ADMIN",
                    "approved" to true,
                    "blocked" to false
                )
            ).continueWithTask { accessTask ->
                if (!accessTask.isSuccessful) {
                    return@continueWithTask Tasks.forResult(false)
                }

                adminConfig.set(adminData).continueWithTask { configSetTask ->
                    Tasks.forResult(configSetTask.isSuccessful)
                }
            }
        }
    }
}
