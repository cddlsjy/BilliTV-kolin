package com.bili.tv.bili_tv_app

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.bili.tv.bili_tv_app.core.plugin.PluginManager
import com.bili.tv.bili_tv_app.databinding.ActivityMainBinding
import com.bili.tv.bili_tv_app.plugins.AdFilterPlugin
import com.bili.tv.bili_tv_app.plugins.DanmakuEnhancePlugin
import com.bili.tv.bili_tv_app.plugins.SponsorBlockPlugin
import com.bili.tv.bili_tv_app.screens.splash.SplashFragment
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.SettingsService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock screen orientation to landscape
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Set immersive mode
        setupImmersiveMode()

        // Initialize application
        initializeApp()
    }

    private fun setupImmersiveMode() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.apply {
            // Hide both status bar and navigation bar
            hide(WindowInsetsCompat.Type.systemBars())
            // Allow swipe to temporarily reveal system bars
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun initializeApp() {
        lifecycleScope.launch {
            try {
                // Initialize services
                AuthService.init(applicationContext)
                SettingsService.init(applicationContext)

                // Show splash screen
                showSplashScreen()

            } catch (e: Exception) {
                e.printStackTrace()
                // Continue to splash even if initialization fails
                showSplashScreen()
            }
        }
    }

    private fun showSplashScreen() {
        // Show HomeFragment with video list
        val fragment = com.bili.tv.bili_tv_app.screens.home.HomeFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveMode()
        }
    }
}
