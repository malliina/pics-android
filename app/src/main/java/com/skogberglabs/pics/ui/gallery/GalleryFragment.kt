package com.skogberglabs.pics.ui.gallery

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.skogberglabs.pics.MainActivityViewModel
import com.skogberglabs.pics.R
import com.skogberglabs.pics.auth.Google
import com.skogberglabs.pics.backend.PicMeta
import com.skogberglabs.pics.backend.Status
import com.skogberglabs.pics.backend.UploadService
import com.skogberglabs.pics.ui.Controls
import com.skogberglabs.pics.ui.ResourceFragment
import com.skogberglabs.pics.ui.init
import kotlinx.android.synthetic.main.gallery_fragment.view.*
import timber.log.Timber
import java.io.File

class GalleryFragment : ResourceFragment(R.layout.gallery_fragment), PicDelegate {
    private val requestImageCapture = 1234

    private val viewModel: GalleryViewModel by activityViewModels()
    private val mainViewModel: MainActivityViewModel by activityViewModels()

    private lateinit var viewAdapter: PicsAdapter
    private lateinit var viewManager: GridLayoutManager

    private lateinit var client: GoogleSignInClient
    private var activePic: File? = null

    override fun onPic(pic: PicMeta, position: Int) {
        val destination = GalleryFragmentDirections.galleryToPicPager(position, pic.key)
        findNavController().navigate(destination)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewManager = GridLayoutManager(context, 2)
        viewAdapter = PicsAdapter(emptyList(), app.applicationContext, this)
        view.gallery_view.init(viewManager, viewAdapter)
        mainViewModel.signedInUser.observe(viewLifecycleOwner) { userInfo ->
            val message =
                userInfo?.let { "Signed in as ${it.email}." } ?: getString(R.string.not_signed_in)
            Timber.i(message)
            view.message.text = message
            activity?.invalidateOptionsMenu()
            val token = if (isPrivate) userInfo?.idToken else null
            viewModel.http.updateToken(token)
            viewModel.loadPics(100, 0)
            viewModel.reconnect()
        }
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

        val hasCamera =
            requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        if (hasCamera) {
            view.floating_action_button.visibility = View.VISIBLE
            view.floating_action_button.setOnClickListener {
                launchCamera()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.reconnect()
    }

    override fun onStop() {
        super.onStop()
        viewModel.disconnect()
    }

    private fun launchCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(requireContext().packageManager)?.also {
                val file = app.camera.createImageFile(app.settings.activeUser)
                file?.let { destination ->
                    activePic = destination
                    val uri = FileProvider.getUriForFile(
                        requireContext(),
                        "com.skogberglabs.pics.fileprovider",
                        destination
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                    startActivityForResult(takePictureIntent, requestImageCapture)
                }
                if (file == null) {
                    Toast.makeText(
                            requireContext(),
                            "Failed to prepare camera.",
                            Toast.LENGTH_SHORT
                        )
                        .show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Timber.i("Got result with code $requestCode.")
        if (requestCode == requestImageCapture && resultCode == RESULT_OK) {
            val keys = data?.extras?.keySet()
            activePic?.let { file ->
                Timber.i("Got photo at $file of size ${file.length()} bytes. Uploading...")
                val uploadIntent = Intent().apply {
                    mainViewModel.signedInUser.value?.let { user ->
                        putExtra(UploadService.EmailKey, user.email.value)
                        putExtra(UploadService.TokenKey, user.idToken.value)
                    }
                }
                UploadService.enqueue(requireContext(), uploadIntent)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
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
    }
}
