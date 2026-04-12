package com.xycz.bilibili_live.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xycz.bilibili_live.data.remote.api.BilibiliApi
import com.xycz.bilibili_live.data.remote.dto.CategoryDto
import com.xycz.bilibili_live.data.remote.dto.SubCategoryDto
import com.xycz.bilibili_live.domain.model.LiveRoomItem
import com.xycz.bilibili_live.util.WbiSigner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 分类ViewModel
 */
class CategoryViewModel(private val api: BilibiliApi) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    /**
     * 加载分类列表
     */
    fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val response = api.getCategories()
                if (response.isSuccessful) {
                    val categories = response.body()?.data?.map { it.toCategoryItem() } ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        categories = categories
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "加载失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "网络错误: ${e.message}"
                )
            }
        }
    }

    /**
     * 加载子分类房间列表
     */
    fun loadCategoryRooms(parentAreaId: String, areaId: String, page: Int = 1) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingRooms = true,
                error = null
            )

            try {
                val wRid = WbiSigner.generateWwebid()
                val response = api.getCategoryRooms(
                    parentAreaId = parentAreaId,
                    areaId = areaId,
                    page = page,
                    wWebid = wRid,
                    wRid = wRid,
                    wts = WbiSigner.getWts()
                )

                if (response.isSuccessful) {
                    val rooms = response.body()?.data?.list?.map {
                        LiveRoomItem(
                            roomId = it.roomidStr ?: it.roomid?.toString() ?: "",
                            title = it.title ?: "",
                            cover = it.cover ?: it.user_cover ?: "",
                            userName = it.uname ?: "",
                            online = it.online ?: 0
                        )
                    } ?: emptyList()

                    _uiState.value = _uiState.value.copy(
                        isLoadingRooms = false,
                        categoryRooms = if (page == 1) rooms else _uiState.value.categoryRooms + rooms,
                        currentPage = page,
                        hasMore = response.body()?.data?.hasMore == 1
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingRooms = false,
                        error = "加载失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingRooms = false,
                    error = "网络错误: ${e.message}"
                )
            }
        }
    }

    /**
     * 选择子分类
     */
    fun selectSubCategory(subCategory: SubCategoryItem, parentId: String) {
        _uiState.value = _uiState.value.copy(
            selectedParentId = parentId,
            selectedSubCategory = subCategory
        )
        loadCategoryRooms(subCategory.parentId, subCategory.id)
    }

    /**
     * 返回分类列表
     */
    fun backToCategories() {
        _uiState.value = _uiState.value.copy(
            selectedParentId = null,
            selectedSubCategory = null,
            categoryRooms = emptyList()
        )
    }

    /**
     * 加载更多
     */
    fun loadMore() {
        val sub = _uiState.value.selectedSubCategory ?: return
        val parentId = _uiState.value.selectedParentId ?: return
        if (_uiState.value.hasMore && !_uiState.value.isLoadingRooms) {
            loadCategoryRooms(parentId, sub.id, _uiState.value.currentPage + 1)
        }
    }
}

/**
 * 分类UI状态
 */
data class CategoryUiState(
    val isLoading: Boolean = false,
    val isLoadingRooms: Boolean = false,
    val categories: List<CategoryItem> = emptyList(),
    val categoryRooms: List<LiveRoomItem> = emptyList(),
    val selectedParentId: String? = null,
    val selectedSubCategory: SubCategoryItem? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null
)

/**
 * 分类项
 */
data class CategoryItem(
    val id: String,
    val name: String,
    val pic: String,
    val subCategories: List<SubCategoryItem>
)

/**
 * 子分类项
 */
data class SubCategoryItem(
    val id: String,
    val name: String,
    val pic: String,
    val parentId: String
)

/**
 * DTO转Domain
 */
fun CategoryDto.toCategoryItem() = CategoryItem(
    id = id.toString(),
    name = name,
    pic = pic ?: "",
    subCategories = list?.map { it.toSubCategoryItem() } ?: emptyList()
)

fun SubCategoryDto.toSubCategoryItem() = SubCategoryItem(
    id = id.toString(),
    name = name,
    pic = pic ?: "",
    parentId = parentId?.toString() ?: "0"
)
