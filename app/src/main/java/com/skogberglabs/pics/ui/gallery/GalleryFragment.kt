package com.skogberglabs.pics.ui.gallery

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.skogberglabs.pics.MainActivityViewModel
import com.skogberglabs.pics.R
import com.skogberglabs.pics.auth.Google
import com.skogberglabs.pics.backend.Email
import com.skogberglabs.pics.backend.PicMeta
import com.skogberglabs.pics.backend.Status
import com.skogberglabs.pics.backend.UserInfo
import com.skogberglabs.pics.ui.Controls
import com.skogberglabs.pics.ui.ResourceFragment
import com.skogberglabs.pics.ui.distinctUntilChanged
import com.skogberglabs.pics.ui.init
import kotlinx.android.synthetic.main.gallery_fragment.view.*
import kotlinx.android.synthetic.main.main_activity.*
import timber.log.Timber
import java.io.File

data class PicOperation(val file: File, val uri: Uri, val user: UserInfo?) {
    val email: Email? get() = user?.email
}

class GalleryFragment : ResourceFragment(R.layout.gallery_fragment), PicClickDelegate {
    private val requestImageCapture = 1234

    private val viewModel: GalleryViewModel by activityViewModels()
    private val mainViewModel: MainActivityViewModel by activityViewModels()

    private lateinit var viewAdapter: PicsAdapter
    private lateinit var viewManager: GridLayoutManager

    private lateinit var client: GoogleSignInClient

    // Number of items left until more items are loaded
    private val loadMoreThreshold = 20
    private val itemsPerLoad = 50
    private val lastVisibleIndex = MutableLiveData<Int>()

    // Local state for the camera
    private var activePic: PicOperation? = null

    // Hack used to determine whether or not to animate collection updates
    private var initial = true

    override fun onPicClicked(pic: PicMeta, position: Int) {
        val destination = GalleryFragmentDirections.galleryToPicPager(position, pic.key)
        findNavController().navigate(destination)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val spanCount = 2
        viewManager = GridLayoutManager(context, spanCount)
        viewAdapter = PicsAdapter(emptyList(), app, this)
        view.gallery_view.init(viewManager, viewAdapter)
        view.gallery_view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0) {
                    val lastVisible = viewManager.findLastVisibleItemPosition()
                    lastVisibleIndex.postValue(lastVisible)
                }
            }
        })
        lastVisibleIndex.distinctUntilChanged().observe(viewLifecycleOwner) { lastPos ->
            val itemCount = viewAdapter.itemCount
            // If spanCount is 2 and itemsPerLoad is an even number, then lastPos is always uneven
            // if there are more items. So, we add +1 to lastPos which is an even number, to make
            // this condition work. Remove this hack if possible.
            if (lastPos + 1 + loadMoreThreshold == itemCount) {
                viewModel.loadPics(itemsPerLoad, itemCount)
            }
        }
        mainViewModel.effectiveUser.observe(viewLifecycleOwner) { userInfo ->
            val message =
                userInfo?.let { "Signed in as ${it.email}." } ?: getString(R.string.not_signed_in)
            activity?.invalidateOptionsMenu()
            Timber.i(message)
            view.message.text = message
            val user = if (isPrivate) userInfo else null
            viewModel.updateUser(user)
            viewModel.reconnect()
        }
        val ctrl = Controls(view.gallery_loading, view.gallery_view, view.message)
        viewModel.pics.observe(viewLifecycleOwner) { outcome ->
            when (outcome.status) {
                Status.Success -> {
                    outcome.data?.let { list ->
                        val pics = list.pics
                        if (pics.isEmpty()) {
                            ctrl.display(getString(R.string.no_pics))
                        } else {
                            ctrl.showList()
                            val previousSize = viewAdapter.list.size
                            viewAdapter.list = pics
                            // Animates collection updates
                            val removed = list.removedIndices
                            val inserted = list.insertedIndices
                            if (!initial) {
                                if (list.prependedCount > 0 || list.appendedCount > 0 || removed.isNotEmpty() || inserted.isNotEmpty()) {
                                    val isScrolledToTop =
                                        viewManager.findFirstVisibleItemPosition() == 0
                                    Timber.i("Animated update")
                                    viewAdapter.notifyItemRangeInserted(0, list.prependedCount)
                                    removed.forEach { idx ->
                                        viewAdapter.notifyItemRemoved(idx)
                                    }
                                    inserted.forEach { idx ->
                                        viewAdapter.notifyItemInserted(idx)
                                    }
                                    viewAdapter.notifyItemRangeInserted(
                                        previousSize,
                                        list.appendedCount
                                    )
                                    if (isScrolledToTop) {
                                        viewManager.scrollToPosition(0)
                                    }
                                } else if (list.backgroundUpdate) {
                                    Timber.i("Background update.")
                                } else {
                                    Timber.i("Reloading list")
                                    viewAdapter.notifyDataSetChanged()
                                }
                            } else {
                                initial = false
                                Timber.i("Reloading all")
                                viewAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
                Status.Error -> {
                    ctrl.display(getString(R.string.error_loading_pics))
                }
                Status.Loading -> {
                    ctrl.enableLoading()
                }
            }
        }
        setHasOptionsMenu(true)
        client = Google.instance.client(requireActivity())

        val hasCamera =
            requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        if (hasCamera) {
            view.floating_action_button.visibility = View.VISIBLE
            view.floating_action_button.setOnClickListener {
                launchCamera()
            }
        }

        requireActivity().appBarLayout.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.disconnect()
    }

    private fun launchCamera() {
        val maybeUser = mainViewModel.effectiveUser.value
        val maybeEmail = maybeUser?.email
        if (isPrivate && maybeEmail == null) {
            showToast("Unable to take pictures now. Try again later.")
        } else {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                takePictureIntent.resolveActivity(requireContext().packageManager)?.also {
                    val file = app.camera.createImageFile(maybeEmail)
                    file?.let { destination ->
                        val uri = app.files.uriForfile(destination)
                        activePic = PicOperation(destination, uri, maybeUser)
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                        startActivityForResult(takePictureIntent, requestImageCapture)
                    }
                    if (file == null) {
                        showToast("Failed to prepare camera.")
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Timber.i("Got result with code $requestCode.")
        if (requestCode == requestImageCapture && resultCode == RESULT_OK) {
            activePic?.let { operation ->
                viewModel.onPicTaken(operation)
                activePic = null
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val signedInEmail = app.settings.privateEmail
        // public, login, private, about, logout
        val publicItem = menu.getItem(0)
        publicItem.isChecked = !isPrivate
        publicItem.isCheckable = !isPrivate
        val isSignedIn = signedInEmail != null
        val loginItem = menu.getItem(1)
        loginItem.isVisible = !isSignedIn
        val privateItem = menu.getItem(2)
        privateItem.isVisible = isSignedIn
        privateItem.isChecked = isPrivate && isSignedIn
        privateItem.isCheckable = isPrivate && isSignedIn
        signedInEmail?.let { email ->
            privateItem.title = email.email
        }
        val logoutItem = menu.getItem(4)
        logoutItem.isVisible = isSignedIn
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.profile_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = onMenuItemSelected(item)

    private fun onMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.public_gallery_item -> {
            adjustMode(false)
            true
        }
        R.id.login_item -> {
            val signInIntent = client.signInIntent
            requireActivity().startActivityForResult(signInIntent, 111)
            true
        }
        R.id.private_item -> {
            adjustMode(true)
            true
        }
        R.id.logout_item -> {
            client.signOut().addOnCompleteListener {
                Timber.i("Signed out.")
                app.settings.privateEmail = null
                adjustMode(false)
            }
            true
        }
        R.id.about_item -> {
            val destination = GalleryFragmentDirections.galleryToAbout()
            findNavController().navigate(destination)
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
        requireActivity().recreate()
    }

    private fun showToast(message: String) =
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
}
