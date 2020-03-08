package com.skogberglabs.pics

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.skogberglabs.pics.auth.Google
import com.skogberglabs.pics.backend.UserInfo
import com.skogberglabs.pics.ui.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivityViewModel(app: Application) : PicsViewModel(app) {
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val google = Google.instance

    private val activeUserData = MutableLiveData<UserInfo?>()
    val signedInUser: LiveData<UserInfo?> = activeUserData
    val mode: LiveData<AppMode> =
        activeUserData.map { if (settings.isPrivate) AppMode.Private else AppMode.Public }

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
}
