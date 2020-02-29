package com.skogberglabs.pics.auth

import android.app.Activity
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

    private val uiScope = CoroutineScope(Dispatchers.Main)

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
        uiScope.launch {
            val userStateDetails = initialize(callingActivity)
            Timber.i("Initialized.")
            try {
                val signIn = socialSignIn(callingActivity, options)
                Timber.i("Signed in: ${signIn.details}")
            } catch (e: AuthNavigationException) {
                Timber.e("Auth failed...")
            }
        }
    }

    private suspend fun socialSignIn(
        callingActivity: Activity,
        options: SignInUIOptions
    ): UserStateDetails {
        return suspendCancellableCoroutine { cont ->
            client.showSignIn(
                callingActivity,
                options,
                AuthCallback(cont)
            )
        }
    }

    private suspend fun initialize(callingActivity: Activity): UserStateDetails {
        return suspendCancellableCoroutine { cont ->
            client.initialize(callingActivity, AuthCallback(cont))
        }
    }

    class AuthCallback(private val cont: CancellableContinuation<UserStateDetails>) :
        Callback<UserStateDetails> {
        override fun onResult(result: UserStateDetails) {
            cont.resume(result)
        }

        override fun onError(e: Exception) {
            Timber.e(e, "Auth error.")
            cont.resumeWithException(e)
        }
    }
}
