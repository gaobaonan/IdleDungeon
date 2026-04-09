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
 * 铁匠铺界面
 * - 每日随机装备列表 + 价格
 * - 投资升级按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacksmithScreen(
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
                title = { Text("Blacksmith / 铁匠铺  Lv.$shopLevel") },
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

/** 商店物品卡片 - 铁匠铺 & 道具店共用 */
@Composable
fun ShopItemCard(item: Item, onBuy: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                Text(item.description, style = MaterialTheme.typography.bodySmall)
                if (item.baseValue > 0) {
                    Text(
                        "${if (item.equipSlot?.name == "WEAPON") "ATK" else "DEF"}: ${item.baseValue}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Button(onClick = onBuy) {
                Text("${item.sellPrice * 2} G")
            }
        }
    }
}