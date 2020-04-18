package com.skogberglabs.pics.ui.pic

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.skogberglabs.pics.R
import com.skogberglabs.pics.backend.PicSize
import com.skogberglabs.pics.backend.Status
import com.skogberglabs.pics.ui.ResourceFragment
import com.skogberglabs.pics.ui.gallery.GalleryViewModel
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.android.synthetic.main.pic_fragment.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class PicFragment : ResourceFragment(R.layout.pic_fragment) {
    companion object {
        const val positionKey = "position"
        private val mainScope = CoroutineScope(Dispatchers.Main)
    }

    private val viewModel: PicViewModel by viewModels()
    private val galleryViewModel: GalleryViewModel by activityViewModels()
    private lateinit var gestureDetector: GestureDetector

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val appBar = requireActivity().appBarLayout
        appBar.visibility = View.GONE
        gestureDetector = GestureDetector(requireContext(), object : SwipeUpGestureListener() {
            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                val current = appBar.visibility
                appBar.visibility = if (current == View.GONE) View.VISIBLE else View.GONE
                return true
            }

            override fun onSwipeUp(velocityY: Float) {
                activity?.onBackPressed()
            }
        })
        view.pic_view.setOnTouchListener { _, e ->
            gestureDetector.onTouchEvent(e)
        }
        galleryViewModel.pics.observe(viewLifecycleOwner) { outcome ->
            when (outcome.status) {
                Status.Success -> {
                    arguments?.getInt(positionKey)?.let { pos ->
                        outcome.data?.let { pics ->
                            viewModel.load(pics.pics[pos], PicSize.Large)
                        }
                    }
                }
                else -> {
                }
            }
        }
        viewModel.pic.observe(viewLifecycleOwner) { pic ->
            view.pic_view.setImageBitmap(pic.bitmap)
        }
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.pic_actions_menu, menu)
//        val shareItem = menu.getItem(0)
        val deletionItem = menu.getItem(1)
        deletionItem.isVisible = isPrivate
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = onMenuItemSelected(item)

    private fun onMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.share_pic_item -> {
            viewModel.pic.value?.let { pic ->
                val uri = app.files.uriForfile(pic.file)
                val shareIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = "image/jpeg"
                }
                startActivity(
                    Intent.createChooser(shareIntent, resources.getText(R.string.send_to))
                )
            }
            true
        }
        R.id.delete_pic_item -> {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete_dialog_title)
                .setMessage(R.string.delete_dialog_message)
                .setPositiveButton(R.string.delete_dialog_yes) { dialog, idx ->
                    deleteImage()
                }
                .setNegativeButton(R.string.delete_dialog_no) { dialog, idx ->
                }
                .show()
            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    private fun deleteImage() {
        try {
            mainScope.launch {
                viewModel.pic.value?.pic?.key?.let { key ->
                    val code = viewModel.delete(key)
                    activity?.onBackPressed()
                }
            }
        } catch (e: Exception) {
            val msg = "Failed to delete image."
            Timber.e(e, msg)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }
}
