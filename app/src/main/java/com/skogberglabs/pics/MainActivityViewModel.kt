package com.skogberglabs.pics

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.skogberglabs.pics.auth.Google
import com.skogberglabs.pics.backend.UserInfo
import com.skogberglabs.pics.ui.distinctUntilChanged
import com.skogberglabs.pics.ui.map
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivityViewModel(app: Application) : PicsViewModel(app) {
    private val google = Google.instance

    private val activeUserData = MutableLiveData<UserInfo?>()
    val effectiveUser: LiveData<UserInfo?> =
        activeUserData.map { u -> if (settings.isPrivate) u else null }.distinctUntilChanged()

    fun signInSilently(ctx: Context) {
        viewModelScope.launch {
            try {
                val user = google.signInSilently(ctx)
                Timber.i("Hello, '${user.email}'!")
                updateSignedInUser(user)
            } catch (e: Exception) {
                Timber.w(e, "No authenticated profile.")
                updateSignedInUser(null)
            }
        }
    }

    fun updateSignedInUser(user: UserInfo?) {
        settings.privateEmail = user?.email
        activeUserData.postValue(user)
    }

    fun updateUser(user: UserInfo?) {
        activeUserData.postValue(user)
    }
}
