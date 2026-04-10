package com.example.idledungeon.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.idledungeon.model.Item

/**
 * 道具店界面
 * - 每日随机消耗品列表 + 价格
 * - 投资升级按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemShopScreen(
    shopLevel: Int,
    gold: Int,
    availableItems: List<Item>,
    investCost: Int,
    onBuy: (String) -> Unit,
    onInvest: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Item Shop / 道具店  Lv.$shopLevel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onRefresh) { Text("Refresh\n[DEBUG]") }
                    TextButton(onClick = onInvest) { Text("Invest / 投资") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Gold / 金币: ${gold} G",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp))
            }
            if (availableItems.isEmpty()) {
                item {
                    Text("Sold out / 已售罄 — 点击 [DEBUG] Refresh 刷新",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp))
                }
            }
            items(availableItems) { item ->
                ShopItemCard(item, gold) { onBuy(item.id) }
            }
        }
    }
}