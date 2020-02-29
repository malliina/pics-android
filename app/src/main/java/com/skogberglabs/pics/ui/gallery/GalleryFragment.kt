package com.skogberglabs.pics.ui.gallery

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import com.skogberglabs.pics.R
import com.skogberglabs.pics.auth.Cognito
import com.skogberglabs.pics.ui.ResourceFragment
import kotlinx.android.synthetic.main.gallery_fragment.view.*
import timber.log.Timber

class GalleryFragment : ResourceFragment(R.layout.gallery_fragment) {
    private lateinit var viewModel: GalleryViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(GalleryViewModel::class.java)
        viewModel.isSignedIn.observe(viewLifecycleOwner) { isSignedIn ->
            val res = if (isSignedIn) R.string.signed_in else R.string.not_signed_in
            view.message.text = getString(res)
            activity?.invalidateOptionsMenu()
        }
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        Timber.i("onCreateOptionsMenu ${viewModel.isSignedIn.value}")
        inflater.inflate(R.menu.profile_menu, menu)
        // public, login, private, logout
        val isSignedIn = viewModel.isSignedIn.value ?: false
        menu.getItem(1).isVisible = !isSignedIn
        menu.getItem(3).isVisible = isSignedIn
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.public_gallery_item -> {
            true
        }
        R.id.login_item -> {
            Cognito.instance.signIn(requireActivity())
            true
        }
        R.id.private_item -> {
//            Cognito.instance.signIn(requireActivity())
            true
        }
        R.id.logout_item -> {
//            Cognito.instance.signIn(requireActivity())
            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }


}
