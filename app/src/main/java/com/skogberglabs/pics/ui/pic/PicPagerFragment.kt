package com.skogberglabs.pics.ui.pic

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.skogberglabs.pics.MainActivity
import com.skogberglabs.pics.R
import com.skogberglabs.pics.backend.PicMeta
import com.skogberglabs.pics.backend.PicSize
import com.skogberglabs.pics.backend.Status
import com.skogberglabs.pics.ui.ResourceFragment
import com.skogberglabs.pics.ui.SystemUI
import com.skogberglabs.pics.ui.gallery.GalleryViewModel
import kotlinx.android.synthetic.main.pic_fragment.view.*
import kotlinx.android.synthetic.main.pic_pager_fragment.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.Exception
import androidx.lifecycle.observe

class PicPagerFragment : ResourceFragment(R.layout.pic_pager_fragment) {
    private val args: PicPagerFragmentArgs by navArgs()
    private val galleryViewModel: GalleryViewModel by activityViewModels()

    private var currentPosition: Int = -1

    // Retains the page after orientation changes
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            currentPosition = position
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pager = view.pic_pager
        val adapter = PicFragmentAdapter(emptyList(), this)
        pager.adapter = adapter
        currentPosition = savedInstanceState?.getInt(PicFragment.positionKey) ?: args.position
        pager.registerOnPageChangeCallback(pageChangeCallback)
        galleryViewModel.pics.observe(viewLifecycleOwner) { pics ->
            adapter.pics = pics.data?.pics ?: emptyList()
            pager.setCurrentItem(currentPosition, false)
            adapter.notifyDataSetChanged()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(PicFragment.positionKey, currentPosition)
    }

    override fun onDestroyView() {
        view?.pic_pager?.unregisterOnPageChangeCallback(pageChangeCallback)
//        SystemUI.modifyStatusVisibility(true, requireActivity() as MainActivity)
        super.onDestroyView()
    }
}

class PicFragmentAdapter(var pics: List<PicMeta>, pager: Fragment) :
    FragmentStateAdapter(pager) {

    override fun getItemCount(): Int = pics.size

    override fun createFragment(position: Int): Fragment {
        val picFragment = PicFragment()
        picFragment.arguments = Bundle().apply {
            putInt(PicFragment.positionKey, position)
        }
        return picFragment
    }
}

abstract class SwipeUpGestureListener : GestureDetector.OnGestureListener {
    abstract fun onSwipeUp(velocityY: Float)
    override fun onShowPress(e: MotionEvent?) {
    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean = true

    override fun onDown(e: MotionEvent?): Boolean = true

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (velocityY < 0) {
            onSwipeUp(velocityY)
        }
        return true
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent?,
        distanceX: Float,
        distanceY: Float
    ): Boolean = true

    override fun onLongPress(e: MotionEvent?) {
    }
}

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
        gestureDetector = GestureDetector(requireContext(), object : SwipeUpGestureListener() {
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
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.delete_pic_item -> {
            try {
                mainScope.launch {
                    viewModel.pic.value?.pic?.key?.let { key ->
                        val code = viewModel.delete(key)
                        activity?.onBackPressed()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete image.")
                Toast.makeText(requireContext(), "Failed to delete image.", Toast.LENGTH_SHORT)
                    .show()
            }
            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }
}
