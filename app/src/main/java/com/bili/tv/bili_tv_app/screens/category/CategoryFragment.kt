package com.bili.tv.bili_tv_app.screens.category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.bili.tv.bili_tv_app.R
import com.bili.tv.bili_tv_app.databinding.FragmentCategoryBinding
import com.bili.tv.bili_tv_app.models.Video
import com.bili.tv.bili_tv_app.services.api.BilibiliApiService
import com.bili.tv.bili_tv_app.widgets.VideoAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

class CategoryFragment : Fragment() {

    private var _binding: FragmentCategoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var videoAdapter: VideoAdapter
    private val videoList = mutableListOf<Video>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        loadCategories()
    }

    private fun setupUI() {
        // Setup video grid
        videoAdapter = VideoAdapter(videoList) { video ->
            navigateToPlayer(video)
        }

        binding.videosRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = videoAdapter
        }

        // Setup back button
        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Setup refresh
        binding.swipeRefresh.setOnRefreshListener {
            loadCategories()
        }
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true

            // 这里可以加载分类列表，暂时使用推荐视频作为示例
            val videos = withContext(Dispatchers.IO) {
                BilibiliApiService.getInstance().getRecommendVideos(0)
            }

            videoList.clear()
            videoList.addAll(videos)
            videoAdapter.notifyDataSetChanged()

            binding.swipeRefresh.isRefreshing = false

            if (videoList.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
                binding.videosRecyclerView.visibility = View.GONE
            } else {
                binding.emptyView.visibility = View.GONE
                binding.videosRecyclerView.visibility = View.VISIBLE
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