package com.example.idledungeon.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.idledungeon.model.Item
import com.example.idledungeon.model.Rarity

/**
 * 道具仓库界面
 * - 网格式展示所有装备和材料
 * - 筛选 / 排序
 * - 点击 → 物品详情弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    items: List<Item>,
    onItemClick: (Item) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory / 道具仓库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                // TODO: 筛选/排序按钮
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items) { item ->
                InventorySlot(item) { onItemClick(item) }
            }
        }
    }
}

@Composable
private fun InventorySlot(item: Item, onClick: () -> Unit) {
    val borderColor = when (item.rarity) {
        Rarity.COMMON -> MaterialTheme.colorScheme.outline
        Rarity.UNCOMMON -> MaterialTheme.colorScheme.primary
        Rarity.RARE -> MaterialTheme.colorScheme.tertiary
        Rarity.EPIC -> MaterialTheme.colorScheme.error
    }
    OutlinedCard(
        modifier = Modifier.aspectRatio(1f).clickable(onClick = onClick),
        border = CardDefaults.outlinedCardBorder().copy(width = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = item.name.take(2), // 显示前两个字作为图标占位
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}