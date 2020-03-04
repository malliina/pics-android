package com.skogberglabs.pics

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.UserState
import com.amazonaws.mobile.client.UserStateDetails
import com.skogberglabs.pics.backend.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivityViewModel(app: Application): PicsViewModel(app) {
    private val client: AWSMobileClient get() = AWSMobileClient.getInstance()
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val authStates = MutableLiveData<UserStateDetails>()
    private val activeUserData = MutableLiveData<UserInfo?>()
    val activeUser: LiveData<UserInfo?> = activeUserData

    init {
        client.addUserStateListener { state ->
            authStates.postValue(state)
            val stateName = when (state.userState) {
                UserState.GUEST -> {
                    activeUserData.postValue(null)
                    "Guest"
                }
                UserState.SIGNED_IN -> {
                    ioScope.launch {
                        val tokens = client.tokens
                        activeUserData.postValue(UserInfo.fromAws(tokens.idToken))
                    }
                    "Signed in"
                }
                UserState.SIGNED_OUT -> {
                    activeUserData.postValue(null)
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
