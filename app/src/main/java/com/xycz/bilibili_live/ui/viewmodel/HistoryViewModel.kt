package com.xycz.bilibili_live.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xycz.bilibili_live.data.local.AppDatabase
import com.xycz.bilibili_live.data.local.entity.History
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 历史记录ViewModel
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)

    val historyList: StateFlow<List<History>> = database.historyDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<HistoryUiState> = historyList.map { list ->
        HistoryUiState(historyList = list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    /**
     * 清空历史记录
     */
    fun clearHistory() {
        viewModelScope.launch {
            database.historyDao().deleteAll()
        }
    }

    /**
     * 删除单条历史
     */
    fun deleteHistory(history: History) {
        viewModelScope.launch {
            database.historyDao().delete(history)
        }
    }
}

/**
 * 历史记录UI状态
 */
data class HistoryUiState(
    val isLoading: Boolean = false,
    val historyList: List<History> = emptyList()
)
