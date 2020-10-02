package com.skogberglabs.pics.ui.about

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import com.skogberglabs.pics.ui.ResourceFragment
import com.skogberglabs.pics.R
import kotlinx.android.synthetic.main.about_fragment.view.*

class AboutFragment : ResourceFragment(R.layout.about_fragment) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val a = requireActivity()
        val versionName = a.packageManager.getPackageInfo(a.packageName, PackageManager.GET_ACTIVITIES).versionName
        view.version_text.text = getString(R.string.version, versionName)
    }
}
