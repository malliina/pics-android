package com.skogberglabs.pics

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.amazonaws.mobile.client.UserStateDetails
import com.skogberglabs.pics.auth.Google
import com.skogberglabs.pics.backend.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivityViewModel(app: Application): PicsViewModel(app) {
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val google = Google.instance

//    private val authStates = MutableLiveData<UserStateDetails>()
    private val activeUserData = MutableLiveData<UserInfo?>()
    val signedInUser: LiveData<UserInfo?> = activeUserData

    fun signInSilently(ctx: Context) {
        uiScope.launch {
            try {
                val user = google.signInSilently(ctx)
                Timber.i("Hello, '${user.email}'!")
                updateUser(user)
            } catch (e: Exception) {
                Timber.w(e, "No authenticated profile.")
                updateUser(null)
            }
        }
    }

    fun updateUser(user: UserInfo?) {
        activeUserData.postValue(user)
    }

//    init {
//        client.addUserStateListener { state ->
//            authStates.postValue(state)
//            val stateName = when (state.userState) {
//                UserState.GUEST -> {
//                    activeUserData.postValue(null)
//                    "Guest"
//                }
//                UserState.SIGNED_IN -> {
//                    ioScope.launch {
//                        val tokens = client.tokens
//                        activeUserData.postValue(UserInfo.fromAws(tokens.idToken))
//                    }
//                    "Signed in"
//                }
//                UserState.SIGNED_OUT -> {
//                    activeUserData.postValue(null)
//                    "Signed out"
//                }
//                UserState.SIGNED_OUT_FEDERATED_TOKENS_INVALID -> "Federated tokens invalid"
//                UserState.SIGNED_OUT_USER_POOLS_TOKENS_INVALID -> {
////                    uiScope.launch {
////                        socialSignIn(options)
////                    }
//                    "User pool tokens invalid"
//                }
//                UserState.UNKNOWN -> "Unknown"
//                else -> "Other state"
//            }
//            Timber.i("State changed to $stateName")
//        }
//    }
}
