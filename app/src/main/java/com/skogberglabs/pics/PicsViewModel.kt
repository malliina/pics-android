package com.skogberglabs.pics

import android.app.Application
import androidx.lifecycle.AndroidViewModel

abstract class PicsViewModel(app: Application) : AndroidViewModel(app) {
    val settings: UserSettings = UserSettings.load(app.applicationContext)
}
