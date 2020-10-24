package com.skogberglabs.pics.ui.about

import android.os.Bundle
import android.view.View
import com.skogberglabs.pics.BuildConfig
import com.skogberglabs.pics.R
import com.skogberglabs.pics.ui.ResourceFragment
import kotlinx.android.synthetic.main.about_fragment.view.*

class AboutFragment : ResourceFragment(R.layout.about_fragment) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.version_text.text = getString(R.string.version, BuildConfig.VERSION_NAME, BuildConfig.GitHash)
    }
}
