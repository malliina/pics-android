package com.skogberglabs.pics.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.UserState
import com.amazonaws.mobile.client.UserStateDetails
import com.skogberglabs.pics.backend.IdToken
import timber.log.Timber

fun <T, U> MutableLiveData<T>.map(f: (t: T) -> U): LiveData<U> = Transformations.map(this, f)

class GalleryViewModel(val app: Application) : AndroidViewModel(app) {
    private val client: AWSMobileClient get() = AWSMobileClient.getInstance()

    private val authStates = MutableLiveData<UserStateDetails>()
    private val idTokens = MutableLiveData<IdToken>()
    val tokens: LiveData<IdToken> = idTokens
    val isSignedIn: LiveData<Boolean> = authStates.map { state ->
        when (state.userState) {
            UserState.SIGNED_IN -> true
            else -> false
        }
    }

    init {
        client.addUserStateListener { state ->
            authStates.postValue(state)
            val stateName = when (state.userState) {
                UserState.GUEST -> "Guest"
                UserState.SIGNED_IN -> {
                    // TODO dark mode
                    idTokens.postValue(IdToken(client.tokens.idToken.tokenString))
                    "Signed in"
                }
                UserState.SIGNED_OUT -> {
                    // TODO light mode
                    "Signed out"
                }
                UserState.SIGNED_OUT_FEDERATED_TOKENS_INVALID -> "Federated tokens invalid"
                UserState.SIGNED_OUT_USER_POOLS_TOKENS_INVALID -> {
//                    uiScope.launch {
//                        socialSignIn(options)
//                    }
                    "User pool tokens invalid"
                }
                UserState.UNKNOWN -> "Unknown"
                else -> "Other state"
            }
            Timber.i("State changed to $stateName")
        }
    }
}
