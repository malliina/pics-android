package com.skogberglabs.pics.auth

import android.app.Activity
import android.content.Context
import com.amazonaws.mobile.client.*
import com.amazonaws.mobile.client.results.Tokens
import com.amazonaws.mobileconnectors.cognitoauth.exceptions.AuthNavigationException
import com.skogberglabs.pics.backend.IdToken
import com.skogberglabs.pics.backend.UserInfo
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Seems like it's not possible to change Google accounts when signed in via Cognito, so Cognito is
 * not used. Instead, login is done via Google directly.
 */
class Cognito {
    companion object {
        private val instance = Cognito()
        private val client: AWSMobileClient get() = AWSMobileClient.getInstance()
    }

    //    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val ioScope = CoroutineScope(Dispatchers.IO)

    // Launches Google sign in directly
    private val google = HostedUIOptions.builder()
        .signInQueryParameters(mapOf("prompt" to "select_account"))
        .scopes("openid email")
        .disableFederation(true)
        .identityProvider("Google")
        .build()

    private val options = SignInUIOptions.builder()
        .hostedUIOptions(google)
        .build()

    fun signIn(callingActivity: Activity) {
        Timber.i("Signing in...")
        ioScope.launch {
            initialize(callingActivity.applicationContext)
            Timber.i("Initialized.")
            try {
                val signIn = socialSignIn(callingActivity, options)
                Timber.i("Signed in: ${signIn.details}")
            } catch (e: AuthNavigationException) {
                Timber.e("Auth failed...")
            }
        }
    }

    suspend fun socialSignIn(
        callingActivity: Activity,
        options: SignInUIOptions
    ): UserStateDetails = suspendCancellableCoroutine { cont ->
        client.showSignIn(
            callingActivity,
            options,
            AuthCallback(cont)
        )
    }

    fun launchInitialize(appContext: Context) = ioScope.launch { initialize(appContext) }

    suspend fun initialize(appContext: Context): UserStateDetails =
        suspendCancellableCoroutine { cont ->
            client.initialize(appContext, AuthCallback(cont))
        }

    suspend fun tokens(): Tokens = suspendCancellableCoroutine { cont ->
        client.getTokens(AuthCallback(cont))
    }

    suspend fun user(): UserInfo = UserInfo.fromAws(tokens().idToken)

//    fun signOutLocally() = client.signOut()

    fun logout() = ioScope.launch { signOut() }

    suspend fun signOut(): Void = suspendCancellableCoroutine { cont ->
        client.signOut(
            SignOutOptions.Builder().invalidateTokens(true).build(),
            AuthCallback<Void>(cont)
        )
    }

    class AuthCallback<T>(private val cont: CancellableContinuation<T>) : Callback<T> {
        override fun onResult(result: T) {
            cont.resume(result)
        }

        override fun onError(e: Exception) {
            cont.resumeWithException(e)
        }
    }
}
