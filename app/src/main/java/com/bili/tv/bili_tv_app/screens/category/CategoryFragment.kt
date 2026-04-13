package com.bili.tv.bili_tv_app.screens.category

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bili.tv.bili_tv_app.R
import com.bili.tv.bili_tv_app.databinding.FragmentCategoryBinding
import com.bili.tv.bili_tv_app.models.Video
import com.bili.tv.bili_tv_app.screens.player.PlayerFragment
import com.bili.tv.bili_tv_app.services.api.BilibiliApiService
import com.bili.tv.bili_tv_app.widgets.VideoAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 分类页面 - 显示哔哩哔哩分区
 */
class CategoryFragment : Fragment() {

    private var _binding: FragmentCategoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var videoAdapter: VideoAdapter

    private var currentRid: Int = 1  // 默认动画分区
    private var currentPage: Int = 1
    private var isLoading = false
    private var hasMoreData = true

    companion object {
        // 分类数据
        data class CategoryZone(
            val name: String,
            val rid: Int,
            val icon: String = ""
        )

        object CategoryZones {
            val defaultZones = listOf(
                CategoryZone("动画", 1),
                CategoryZone("音乐", 3),
                CategoryZone("舞蹈", 129),
                CategoryZone("游戏", 4),
                CategoryZone("知识", 36),
                CategoryZone("科技", 188),
                CategoryZone("生活", 160),
                CategoryZone("美食", 211),
                CategoryZone("动物圈", 223),
                CategoryZone("汽车", 223),  // 汽车在动物圈后面
                CategoryZone("运动", 234),
                CategoryZone("影视", 181),
                CategoryZone("娱乐", 5),
                CategoryZone("时尚", 155),
                CategoryZone("国创", 167),
                CategoryZone("纪录片", 177),
                CategoryZone("电影", 23),
                CategoryZone("电视剧", 11)
            )
        }

        fun newInstance(): CategoryFragment {
            return CategoryFragment()
        }

        fun newRegion(rid: Int): CategoryFragment {
            return CategoryFragment().apply {
                arguments = Bundle().apply {
                    putInt("rid", rid)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentRid = it.getInt("rid", 1)
        }
    }

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

        setupCategoryList()
        setupVideoList()
        setupKeyListener()

        // 加载分类列表
        loadCategories()

        // 加载视频
        loadVideos()
    }

    private fun setupCategoryList() {
        categoryAdapter = CategoryAdapter { category ->
            // 点击分类
            if (category.rid != currentRid) {
                currentRid = category.rid
                currentPage = 1
                hasMoreData = true
                updateCategorySelection()
                loadVideos()
            }
        }

        binding.categoryList.apply {
            layoutManager = GridLayoutManager(requireContext(), 1, GridLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }
    }

    private fun setupVideoList() {
        videoAdapter = VideoAdapter { video ->
            // 点击视频，打开播放器
            openPlayer(video)
        }

        binding.videoGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = videoAdapter

            // 监听滚动到底部，加载更多
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as GridLayoutManager
                    val totalItemCount = layoutManager.itemCount
                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                    if (totalItemCount <= lastVisibleItem + 5 && !isLoading && hasMoreData) {
                        currentPage++
                        loadMoreVideos()
                    }
                }
            })
        }
    }

    private fun setupKeyListener() {
        binding.root.isFocusable = true
        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()

        binding.root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        // 如果在视频列表顶部，切换到分类列表
                        val layoutManager = binding.videoGrid.layoutManager as GridLayoutManager
                        if (layoutManager.findFirstVisibleItemPosition() == 0) {
                            binding.categoryList.requestFocus()
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // 如果在分类列表，切换到视频列表
                        if (binding.categoryList.hasFocus()) {
                            binding.videoGrid.requestFocus()
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        if (parentFragmentManager.backStackEntryCount > 0) {
                            parentFragmentManager.popBackStack()
                            true
                        } else false
                    }
                    else -> false
                }
            } else false
        }
    }

    private fun loadCategories() {
        categoryAdapter.submitList(CategoryZones.defaultZones)
        updateCategorySelection()
    }

    private fun updateCategorySelection() {
        categoryAdapter.setSelectedRid(currentRid)
        binding.categoryTitle.text = CategoryZones.defaultZones.find { it.rid == currentRid }?.name ?: "分类"
    }

    private fun loadVideos() {
        if (isLoading) return
        isLoading = true
        binding.loadingProgress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val videos = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getRegionVideos(currentRid, currentPage)
                }

                if (videos.isNotEmpty()) {
                    videoAdapter.submitList(videos)
                    binding.emptyView.visibility = View.GONE
                    binding.videoGrid.visibility = View.VISIBLE
                } else {
                    binding.emptyView.visibility = View.VISIBLE
                    binding.videoGrid.visibility = View.GONE
                    hasMoreData = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.loadingProgress.visibility = View.GONE
                isLoading = false
            }
        }
    }

    private fun loadMoreVideos() {
        if (isLoading || !hasMoreData) return
        isLoading = true

        lifecycleScope.launch {
            try {
                val moreVideos = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getRegionVideos(currentRid, currentPage)
                }

                if (moreVideos.isNotEmpty()) {
                    val currentList = videoAdapter.currentList.toMutableList()
                    currentList.addAll(moreVideos)
                    videoAdapter.submitList(currentList)
                } else {
                    hasMoreData = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                currentPage-- // 恢复页码
            } finally {
                isLoading = false
            }
        }
    }

    private fun openPlayer(video: Video) {
        val playerFragment = PlayerFragment.newInstance(
            bvid = video.bvid,
            title = video.title,
            coverUrl = video.pic,
            cid = video.cid,
            aid = video.aid,
            categoryVideoList = videoAdapter.currentList
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, playerFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
