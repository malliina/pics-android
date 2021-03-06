package com.skogberglabs.pics.auth

import android.app.Activity
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.skogberglabs.pics.backend.Email
import com.skogberglabs.pics.backend.IdToken
import com.skogberglabs.pics.backend.UserInfo
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class Google {
    companion object {
        val instance = Google()

        fun readUser(account: GoogleSignInAccount): UserInfo? {
            val idToken = account.idToken
            val email = account.email
            return idToken?.let { token ->
                email?.let { email ->
                    UserInfo(Email(email), IdToken(token))
                }
            }
        }
    }

    private val webClientId = "469087885456-hol73l5j9tur3oq9fb4c07hr0m4dibge.apps.googleusercontent.com"

    fun client(activity: Activity): GoogleSignInClient = GoogleSignIn.getClient(activity, options())

    fun client(ctx: Context): GoogleSignInClient = GoogleSignIn.getClient(ctx, options())

    private fun options() = GoogleSignInOptions.Builder()
        .requestIdToken(webClientId)
        .requestEmail()
        .build()

    suspend fun signInSilently(ctx: Context) = signInSilently(client(ctx))

    suspend fun signInSilently(c: GoogleSignInClient): UserInfo {
        val user = c.silentSignIn().await()
        readUser(user)?.let {
            return it
        }
        Timber.w("Unable to read user info from account.")
        throw Exception("No user.")
    }
}

suspend fun <T> Task<T>.await(): T {
    return suspendCoroutine { cont ->
        addOnCompleteListener { task ->
            try {
                val t = task.getResult(ApiException::class.java)
                if (t != null) {
                    cont.resume(t)
                } else {
                    cont.resumeWithException(Exception("No result in task."))
                }
            } catch (e: ApiException) {
                cont.resumeWithException(e)
            }
        }
    }
}
