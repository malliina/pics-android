package com.skogberglabs.pics.ui

import android.view.View
import com.skogberglabs.pics.MainActivity

class SystemUI {
    companion object {
        fun modifyStatusVisibility(visible: Boolean, activity: MainActivity) {
            val statusVisibility =
                if (visible) View.SYSTEM_UI_FLAG_VISIBLE else View.SYSTEM_UI_FLAG_FULLSCREEN
            activity.window.decorView.systemUiVisibility = statusVisibility
            val actionBar = activity.supportActionBar
            if (visible) actionBar?.show() else actionBar?.hide()
        }
    }
}
