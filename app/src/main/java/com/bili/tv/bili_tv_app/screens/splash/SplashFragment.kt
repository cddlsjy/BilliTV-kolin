package com.bili.tv.bili_tv_app.screens.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bili.tv.bili_tv_app.databinding.FragmentSplashBinding
import com.bili.tv.bili_tv_app.screens.home.HomeFragment
import com.bili.tv.bili_tv_app.services.api.BilibiliApiService
import com.bili.tv.bili_tv_app.services.SettingsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private var videoInitialized = false
    private var dataLoaded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initialize()
    }

    private fun initialize() {
        lifecycleScope.launch {
            // Initialize in parallel
            val tasks = mutableListOf<Pair<suspend () -> Unit, String>>()

            // 1. Try to initialize video (optional)
            if (SettingsService.splashAnimationEnabled) {
                tasks.add(Pair({
                    // Video initialization would go here
                    // For simplicity, just mark as done
                    videoInitialized = true
                }, "video_init"))
            }

            // 2. Preload recommended videos
            tasks.add(Pair({
                try {
                    BilibiliApiService.getInstance().getRecommendVideos(0)
                    dataLoaded = true
                } catch (e: Exception) {
                    e.printStackTrace()
                    dataLoaded = true // Continue even if failed
                }
            }, "data_load"))

            // Execute all tasks in parallel
            tasks.forEach { (task, _) ->
                launch(Dispatchers.IO) {
                    try {
                        task()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Wait for minimum splash duration
            delay(1500)

            // Wait for video if enabled
            if (!SettingsService.splashAnimationEnabled) {
                videoInitialized = true
            }

            // Wait a bit more for data
            while (!dataLoaded) {
                delay(100)
            }

            // Navigate to home
            withContext(Dispatchers.Main) {
                navigateToHome()
            }
        }
    }

    private fun navigateToHome() {
        // Add fade out animation
        val fadeOut = AlphaAnimation(1f, 0f).apply {
            duration = 400
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    parentFragmentManager.beginTransaction()
                        .replace(com.bili.tv.bili_tv_app.R.id.fragment_container, HomeFragment())
                        .commit()
                }
            })
        }
        binding.splashContainer.startAnimation(fadeOut)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
