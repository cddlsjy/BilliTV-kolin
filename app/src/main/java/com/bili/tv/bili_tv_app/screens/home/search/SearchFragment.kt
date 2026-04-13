package com.bili.tv.bili_tv_app.screens.home.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.bili.tv.bili_tv_app.R
import com.bili.tv.bili_tv_app.databinding.FragmentSearchBinding
import com.bili.tv.bili_tv_app.models.Video
import com.bili.tv.bili_tv_app.services.api.BilibiliApiService
import com.bili.tv.bili_tv_app.widgets.VideoAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var videoAdapter: VideoAdapter
    private val searchResults = mutableListOf<Video>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        // Back button
        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Search input
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        // Search button
        binding.searchButton.setOnClickListener {
            performSearch()
        }

        // Results list
        videoAdapter = VideoAdapter(searchResults) { video ->
            navigateToPlayer(video)
        }

        binding.resultsRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = videoAdapter
        }
    }

    private fun performSearch() {
        val keyword = binding.searchInput.text.toString().trim()
        if (keyword.isEmpty()) return

        lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE

            val results = withContext(Dispatchers.IO) {
                BilibiliApiService.getInstance().searchVideos(keyword)
            }

            searchResults.clear()
            searchResults.addAll(results)
            videoAdapter.notifyDataSetChanged()

            binding.loadingProgress.visibility = View.GONE

            if (searchResults.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
                binding.resultsRecyclerView.visibility = View.GONE
            } else {
                binding.emptyView.visibility = View.GONE
                binding.resultsRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun navigateToPlayer(video: Video) {
        val fragment = com.bili.tv.bili_tv_app.screens.player.PlayerFragment.newInstance(
            video.bvid,
            video.title,
            video.pic
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
