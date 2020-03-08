package com.skogberglabs.pics

import android.app.Application
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.analytics.Analytics
import com.microsoft.appcenter.crashes.Crashes
import timber.log.Timber

class PicsApp : Application() {
    private lateinit var savedSettings: UserSettings
    val settings: UserSettings get() = savedSettings

    override fun onCreate() {
        super.onCreate()
        AppCenter.start(
            this,
            "38e20f92-99ef-43bd-8e2d-4616d3ed0e13",
            Analytics::class.java,
            Crashes::class.java
        )
        val tree = if (BuildConfig.DEBUG) Timber.DebugTree() else NoLogging()
        Timber.plant(tree)
        savedSettings = UserSettings.load(applicationContext)
    }

    class NoLogging : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {

        }
    }
}
