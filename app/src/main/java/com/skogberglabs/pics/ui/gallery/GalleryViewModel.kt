package com.skogberglabs.pics.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.UserState
import com.amazonaws.mobile.client.UserStateDetails
import com.skogberglabs.pics.backend.UserInfo
import timber.log.Timber

fun <T, U> MutableLiveData<T>.map(f: (t: T) -> U): LiveData<U> = Transformations.map(this, f)

class GalleryViewModel(val app: Application) : AndroidViewModel(app) {

}
