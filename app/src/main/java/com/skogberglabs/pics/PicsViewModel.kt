package com.skogberglabs.pics

import android.app.Application
import androidx.lifecycle.AndroidViewModel

abstract class PicsViewModel(private val picsApp: Application) : AndroidViewModel(picsApp) {
    val app: PicsApp get() = picsApp as PicsApp
    val settings: UserSettings = UserSettings.load(picsApp.applicationContext)
}
