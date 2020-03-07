package com.skogberglabs.pics.ui.gallery

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.skogberglabs.pics.MainActivityViewModel
import com.skogberglabs.pics.R
import com.skogberglabs.pics.auth.Google
import com.skogberglabs.pics.backend.PicMeta
import com.skogberglabs.pics.backend.Status
import com.skogberglabs.pics.ui.Controls
import com.skogberglabs.pics.ui.ResourceFragment
import com.skogberglabs.pics.ui.init
import kotlinx.android.synthetic.main.gallery_fragment.view.*
import timber.log.Timber

class GalleryFragment : ResourceFragment(R.layout.gallery_fragment), PicDelegate {

    private lateinit var viewModel: GalleryViewModel
    private lateinit var mainViewModel: MainActivityViewModel

    private lateinit var viewAdapter: PicsAdapter
    private lateinit var viewManager: GridLayoutManager

    private lateinit var client: GoogleSignInClient

    override fun onPic(pic: PicMeta) {
        Timber.i("Clicked pic ${pic.key}")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.i("Gallery onViewCreated")
        viewManager = GridLayoutManager(context, 2)
        viewAdapter = PicsAdapter(emptyList(), app.applicationContext, this)
        view.gallery_view.init(viewManager, viewAdapter)
        viewModel = ViewModelProvider(this).get(GalleryViewModel::class.java)
        mainViewModel =
            activity?.run { ViewModelProvider(this).get(MainActivityViewModel::class.java) }!!
        mainViewModel.signedInUser.observe(viewLifecycleOwner) { userInfo ->
            Timber.i("Got $userInfo")
            val message =
                userInfo?.let { "Signed in as ${it.email}." } ?: getString(R.string.not_signed_in)
            Timber.i(message)
            view.message.text = message
            activity?.invalidateOptionsMenu()
            val token = if (isPrivate) userInfo?.idToken else null
            viewModel.http.updateToken(token)
            viewModel.loadPics(100, 0)
        }
//        viewModel.mode.observe(viewLifecycleOwner) { isPrivateMode ->
//
//        }
        val ctrl = Controls(null, view.gallery_view, view.message)
        viewModel.pics.observe(viewLifecycleOwner) { outcome ->
            when (outcome.status) {
                Status.Success -> {
                    outcome.data?.let { list ->
                        if (list.isEmpty()) {
                            ctrl.display(getString(R.string.no_pics))
                        } else {
                            ctrl.showList()
                            viewAdapter.list = list
                            viewAdapter.notifyDataSetChanged()
                        }
                    }
                }
                Status.Error -> {
                    ctrl.display(getString(R.string.error_generic))
                }
                Status.Loading -> {
                    ctrl.enableLoading()
                }
            }
        }
        setHasOptionsMenu(true)
        client = Google.instance.client(requireActivity())

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        Timber.i("onCreateOptionsMenu ${mainViewModel.signedInUser.value}")
        inflater.inflate(R.menu.profile_menu, menu)
        val activeUser = mainViewModel.signedInUser.value
        // public, login, private, logout
        val publicItem = menu.getItem(0)
        publicItem.isChecked = !isPrivate
        publicItem.isCheckable = !isPrivate
        val isSignedIn = activeUser != null
        val loginItem = menu.getItem(1)
        loginItem.isVisible = !isSignedIn
        val privateItem = menu.getItem(2)
        privateItem.isVisible = isSignedIn
        privateItem.isChecked = isPrivate && isSignedIn
        privateItem.isCheckable = isPrivate && isSignedIn
        activeUser?.let { user ->
            privateItem.title = user.email.value
        }
        val logoutItem = menu.getItem(3)
        logoutItem.isVisible = isSignedIn
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.public_gallery_item -> {
            adjustMode(false)
            true
        }
        R.id.login_item -> {
            val signInIntent = client.signInIntent
            requireActivity().startActivityForResult(signInIntent, 111)
            // This will recreate the activity, and the activity will launch sign in in onCreate
            //adjustMode(true)
            true
        }
        R.id.private_item -> {
            adjustMode(true)
            true
        }
        R.id.logout_item -> {
            client.signOut().addOnCompleteListener {
                Timber.i("Signed out.")
                adjustMode(false)
            }
            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    private fun adjustMode(isPrivate: Boolean) {
        app.settings.isPrivate = isPrivate
        if (isPrivate) {
            mainViewModel.signInSilently(app.applicationContext)
        } else {
            mainViewModel.updateUser(null)
        }
//        requireActivity().recreate()
    }
}
