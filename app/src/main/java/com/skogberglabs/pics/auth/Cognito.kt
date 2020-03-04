package com.skogberglabs.pics.auth

import android.app.Activity
import android.content.Context
import com.amazonaws.mobile.client.*
import com.amazonaws.mobileconnectors.cognitoauth.exceptions.AuthNavigationException
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class Cognito {
    companion object {
        val instance = Cognito()
        val client: AWSMobileClient get() = AWSMobileClient.getInstance()
    }

//    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val ioScope = CoroutineScope(Dispatchers.IO)

    // Launches Google sign in directly
    private val google = HostedUIOptions.builder()
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
    ): UserStateDetails {
        Timber.i("Social sign in...")
        return suspendCancellableCoroutine { cont ->
            client.showSignIn(
                callingActivity,
                options,
                AuthCallback(cont)
            )
        }
    }

    fun launchInitialize(appContext: Context) = ioScope.launch { initialize(appContext) }

    suspend fun initialize(appContext: Context): UserStateDetails {
        Timber.i("Initializing...")
        return suspendCancellableCoroutine { cont ->
            client.initialize(appContext, AuthCallback(cont))
        }
    }

    class AuthCallback(private val cont: CancellableContinuation<UserStateDetails>) :
        Callback<UserStateDetails> {
        override fun onResult(result: UserStateDetails) {
            Timber.i("Auth result $result ${result.userState}.")
            cont.resume(result)
        }

        override fun onError(e: Exception) {
            Timber.e(e, "Auth error.")
            cont.resumeWithException(e)
        }
    }
}
