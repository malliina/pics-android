package com.skogberglabs.pics.ui.gallery

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import com.skogberglabs.pics.MainActivityViewModel
import com.skogberglabs.pics.R
import com.skogberglabs.pics.auth.Cognito
import com.skogberglabs.pics.ui.ResourceFragment
import kotlinx.android.synthetic.main.gallery_fragment.view.*
import timber.log.Timber

class GalleryFragment : ResourceFragment(R.layout.gallery_fragment) {
    private lateinit var viewModel: GalleryViewModel
    private lateinit var mainViewModel: MainActivityViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(GalleryViewModel::class.java)
        mainViewModel =
            activity?.run { ViewModelProvider(this).get(MainActivityViewModel::class.java) }!!
        mainViewModel.activeUser.observe(viewLifecycleOwner) { userInfo ->
            val message =
                userInfo?.let { "Signed in as ${it.email}." } ?: getString(R.string.not_signed_in)
            Timber.i(message)
            view.message.text = message
            activity?.invalidateOptionsMenu()
        }
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        Timber.i("onCreateOptionsMenu ${mainViewModel.activeUser.value}")
        inflater.inflate(R.menu.profile_menu, menu)
        val activeUser = mainViewModel.activeUser.value
        // public, login, private, logout
        menu.getItem(1).isVisible = activeUser == null
        val privateItem = menu.getItem(2)
        privateItem.isVisible = activeUser != null
        activeUser?.let { user ->
            privateItem.title = user.email.value
        }
        menu.getItem(3).isVisible = activeUser != null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.public_gallery_item -> {
            adjustMode(false)
            true
        }
        R.id.login_item -> {
            // This will recreate the activity, and the activity will launch sign in in onCreate
            adjustMode(true)
            true
        }
        R.id.private_item -> {
            adjustMode(true)
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

    private fun adjustMode(isPrivate: Boolean)  {
        app.settings.isPrivate = isPrivate
        requireActivity().recreate()
    }
}
