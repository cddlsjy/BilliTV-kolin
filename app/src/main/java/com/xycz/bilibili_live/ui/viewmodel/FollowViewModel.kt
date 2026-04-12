package com.xycz.bilibili_live.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xycz.bilibili_live.data.local.AppDatabase
import com.xycz.bilibili_live.data.local.entity.FollowUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 关注ViewModel
 */
class FollowViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)

    val followedList: StateFlow<List<FollowUser>> = database.followUserDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(FollowUiState())
    val uiState: StateFlow<FollowUiState> = _uiState

    /**
     * 取消关注
     */
    fun unfollow(user: FollowUser) {
        viewModelScope.launch {
            database.followUserDao().delete(user)
        }
    }

    /**
     * 刷新
     */
    fun refresh() {
        // Room会自动更新，无需额外操作
    }
}

/**
 * 关注UI状态
 */
data class FollowUiState(
    val isLoading: Boolean = false
)
