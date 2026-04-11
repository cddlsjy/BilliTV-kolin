package com.bili.tv.bili_tv_app.screens.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.bili.tv.bili_tv_app.R
import com.bili.tv.bili_tv_app.databinding.FragmentHomeBinding
import com.bili.tv.bili_tv_app.models.Video
import com.bili.tv.bili_tv_app.screens.home.settings.SettingsFragment
import com.bili.tv.bili_tv_app.services.api.BilibiliApiService
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.SettingsService
import com.bili.tv.bili_tv_app.widgets.VideoAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var videoAdapter: VideoAdapter
    private val videoList = mutableListOf<Video>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("HomeFragment", "onViewCreated called")
        setupUI()
        loadContent()
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

        // Setup search button
        binding.searchButton.setOnClickListener {
            navigateToSearch()
        }

        // Setup settings button
        binding.settingsButton.setOnClickListener {
            navigateToSettings()
        }

        // Setup user button
        binding.userButton.setOnClickListener {
            if (AuthService.isLoggedIn) {
                showUserInfo()
            } else {
                navigateToLogin()
            }
        }

        // Setup refresh
        binding.swipeRefresh.setOnRefreshListener {
            loadContent()
        }
    }

    private fun loadContent() {
        lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true

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

            // Check and auto play last video
            checkAndAutoPlayLastVideo()
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

    private fun navigateToSearch() {
        val fragment = com.bili.tv.bili_tv_app.screens.home.search.SearchFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToSettings() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SettingsFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToLogin() {
        val fragment = com.bili.tv.bili_tv_app.screens.home.login.LoginFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showUserInfo() {
        AuthService.currentUser?.let { user ->
            Toast.makeText(
                requireContext(),
                "欢迎, ${user.uname}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun checkAndAutoPlayLastVideo() {
        if (SettingsService.autoPlayLastVideo && SettingsService.hasLastPlayedVideo()) {
            lifecycleScope.launch {
                // Delay to ensure UI is ready
                delay(500)

                val isLive = SettingsService.lastPlayedIsLive
                if (isLive) {
                    // Auto play last live
                    val roomId = SettingsService.lastPlayedRoomId
                    val title = SettingsService.lastPlayedTitle
                    if (roomId > 0 && title.isNotEmpty()) {
                        val fragment = com.bili.tv.bili_tv_app.screens.player.PlayerFragment.newInstance(
                            isLive = true,
                            roomId = roomId,
                            title = title
                        )

                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, fragment)
                            .addToBackStack(null)
                            .commit()
                    }
                } else {
                    // Auto play last video
                    val bvid = SettingsService.lastPlayedBvid
                    val title = SettingsService.lastPlayedTitle
                    val cover = SettingsService.lastPlayedCover
                    val cid = SettingsService.lastPlayedCid
                    val progress = SettingsService.lastPlayedProgress
                    if (bvid.isNotEmpty()) {
                        val fragment = com.bili.tv.bili_tv_app.screens.player.PlayerFragment.newInstance(
                            bvid = bvid,
                            title = title,
                            coverUrl = cover,
                            cid = cid,
                            seekTo = progress
                        )

                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, fragment)
                            .addToBackStack(null)
                            .commit()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
