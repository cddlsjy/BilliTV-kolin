package com.bili.tv.bili_tv_app.screens.home.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bili.tv.bili_tv_app.databinding.FragmentSettingsBinding
import com.bili.tv.bili_tv_app.services.SettingsService

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
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

        // Load current settings
        binding.splashAnimationSwitch.isChecked = SettingsService.splashAnimationEnabled
        binding.danmakuSwitch.isChecked = SettingsService.danmakuEnabled
        binding.adFilterSwitch.isChecked = SettingsService.adFilterEnabled
        binding.sponsorBlockSwitch.isChecked = SettingsService.sponsorBlockEnabled
        binding.autoPlaySwitch.isChecked = SettingsService.autoPlay
        binding.autoPlayLastVideoSwitch.isChecked = SettingsService.autoPlayLastVideo

        // Save switch states
        binding.splashAnimationSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.splashAnimationEnabled = isChecked
        }

        binding.danmakuSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.danmakuEnabled = isChecked
        }

        binding.adFilterSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.adFilterEnabled = isChecked
        }

        binding.sponsorBlockSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.sponsorBlockEnabled = isChecked
        }

        binding.autoPlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.autoPlay = isChecked
        }

        binding.autoPlayLastVideoSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.autoPlayLastVideo = isChecked
            if (!isChecked) {
                SettingsService.clearLastPlayedVideo()
            }
        }

        // Quality spinner
        val qualities = arrayOf("360P", "480P", "720P", "1080P", "1080P+")
        binding.qualitySpinner.setSelection(getCurrentQualityIndex())

        binding.qualitySpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val quality = when (position) {
                    0 -> 16   // 360P
                    1 -> 32   // 480P
                    2 -> 64   // 720P
                    3 -> 80   // 1080P
                    4 -> 112  // 1080P+
                    else -> 80
                }
                SettingsService.defaultQuality = quality
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })

        // About section
        binding.aboutText.text = "BiliTV v1.2.2\n哔哩哔哩电视版客户端\n\n基于Flutter源码转换"
    }

    private fun getCurrentQualityIndex(): Int {
        return when (SettingsService.defaultQuality) {
            16 -> 0
            32 -> 1
            64 -> 2
            80 -> 3
            112 -> 4
            else -> 3
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
