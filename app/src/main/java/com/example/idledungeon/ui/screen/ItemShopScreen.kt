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
    availableItems: List<Item>,
    investCost: Int,
    onBuy: (String) -> Unit,
    onInvest: () -> Unit,
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
            items(availableItems) { item ->
                ShopItemCard(item) { onBuy(item.id) }
            }
        }
    }
}