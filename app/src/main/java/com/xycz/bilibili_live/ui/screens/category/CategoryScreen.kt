package com.xycz.bilibili_live.ui.screens.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xycz.bilibili_live.domain.model.LiveRoomItem
import com.xycz.bilibili_live.ui.screens.home.LiveRoomCard
import com.xycz.bilibili_live.ui.viewmodel.CategoryItem
import com.xycz.bilibili_live.ui.viewmodel.CategoryUiState
import com.xycz.bilibili_live.ui.viewmodel.SubCategoryItem

/**
 * 分类页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    uiState: CategoryUiState,
    onSubCategoryClick: (SubCategoryItem, String) -> Unit,
    onBackToCategories: () -> Unit,
    onRoomClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.selectedSubCategory != null)
                            uiState.selectedSubCategory.name
                        else
                            "分类"
                    )
                },
                navigationIcon = {
                    if (uiState.selectedSubCategory != null) {
                        IconButton(onClick = onBackToCategories) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null && uiState.categories.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(uiState.error, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onRefresh) {
                            Text("重试")
                        }
                    }
                }
                // 显示子分类房间列表
                uiState.selectedSubCategory != null -> {
                    CategoryRoomList(
                        rooms = uiState.categoryRooms,
                        isLoading = uiState.isLoadingRooms,
                        hasMore = uiState.hasMore,
                        onRoomClick = onRoomClick,
                        onLoadMore = onLoadMore
                    )
                }
                // 显示分类列表
                else -> {
                    CategoryList(
                        categories = uiState.categories,
                        onSubCategoryClick = onSubCategoryClick
                    )
                }
            }
        }
    }
}

/**
 * 分类列表
 */
@Composable
private fun CategoryList(
    categories: List<CategoryItem>,
    onSubCategoryClick: (SubCategoryItem, String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it.id }) { category ->
            CategoryCard(
                category = category,
                onSubCategoryClick = onSubCategoryClick
            )
        }
    }
}

/**
 * 分类卡片
 */
@Composable
private fun CategoryCard(
    category: CategoryItem,
    onSubCategoryClick: (SubCategoryItem, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // 分类标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = category.pic,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = category.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 子分类网格
            if (category.subCategories.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    items(category.subCategories.take(8)) { subCategory ->
                        SubCategoryItem(
                            subCategory = subCategory,
                            onClick = { onSubCategoryClick(subCategory, category.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 子分类项
 */
@Composable
private fun SubCategoryItem(
    subCategory: SubCategoryItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = subCategory.pic,
            contentDescription = subCategory.name,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subCategory.name,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 72.dp)
        )
    }
}

/**
 * 分类房间列表
 */
@Composable
private fun CategoryRoomList(
    rooms: List<LiveRoomItem>,
    isLoading: Boolean,
    hasMore: Boolean,
    onRoomClick: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    if (rooms.isEmpty() && !isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("该分类暂无直播")
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(rooms, key = { it.roomId }) { room ->
            LiveRoomCard(
                room = room,
                onClick = { onRoomClick(room.roomId) }
            )
        }

        if (hasMore) {
            item {
                LaunchedEffect(Unit) {
                    onLoadMore()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
