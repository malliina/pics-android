package com.skogberglabs.pics.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.skogberglabs.pics.PicsApp

open class AppViewModel(val app: Application): AndroidViewModel(app) {
    val picsApp: PicsApp get() = app as PicsApp
}
