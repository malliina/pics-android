package com.skogberglabs.pics.ui.pic

import android.os.Bundle
import android.view.View
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
        galleryViewModel.pics.observe(viewLifecycleOwner, Observer { pics ->
            adapter.pics = pics.data?.pics ?: emptyList()
            pager.setCurrentItem(currentPosition, false)
            adapter.notifyDataSetChanged()
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(PicFragment.positionKey, currentPosition)
    }

    override fun onDestroyView() {
        view?.pic_pager?.unregisterOnPageChangeCallback(pageChangeCallback)
        SystemUI.modifyStatusVisibility(true, requireActivity() as MainActivity)
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

class PicFragment : ResourceFragment(R.layout.pic_fragment) {
    companion object {
        const val positionKey = "position"
    }

    private val viewModel: PicViewModel by viewModels()
    private val galleryViewModel: GalleryViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        galleryViewModel.pics.observe(viewLifecycleOwner, Observer { outcome ->
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
        })
        viewModel.pic.observe(viewLifecycleOwner, Observer { pic ->
            view.pic_view.setImageBitmap(pic)
        })
        modifyStatusVisibility(false)
        view.setOnClickListener {
            val showStatus = (requireActivity() as MainActivity).supportActionBar?.isShowing ?: false
            modifyStatusVisibility(!showStatus)
        }
    }

    private fun modifyStatusVisibility(visible: Boolean) {
        SystemUI.modifyStatusVisibility(visible, requireActivity() as MainActivity)
    }
}
