package com.example.idledungeon.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.idledungeon.model.*

/**
 * 铁匠铺界面
 * - 每日随机装备列表 + 价格
 * - 投资升级按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacksmithScreen(
    shopLevel: Int,
    gold: Int,
    availableItems: List<Item>,
    investCost: Int,
    onBuy: (String) -> Unit,
    onInvest: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blacksmith  Lv.$shopLevel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onInvest) { Text("Invest") }
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
                Text("Gold: ${gold} G",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp))
            }
            if (availableItems.isEmpty()) {
                item {
                    Text("Sold out — restocks at midnight",
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

/** 商店物品卡片 - 铁匠铺 & 道具店共用 */
@Composable
fun ShopItemCard(item: Item, gold: Int, onBuy: () -> Unit) {
    val price = item.goldCost
    val canAfford = gold >= price
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ItemIcon(item, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall,
                        color = rarityColor(item.rarity))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "L${item.equipLevel} ${item.rarity.displayName()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = rarityColor(item.rarity)
                    )
                }
                if (item.description.isNotEmpty()) {
                    Text(item.description, style = MaterialTheme.typography.bodySmall)
                }
                if (item.baseValue > 0 && item.equipSlot != null) {
                    val label = when (item.equipSlot) {
                        EquipSlot.WEAPON -> "ATK"
                        EquipSlot.ARMOR -> "DEF"
                        else -> ""
                    }
                    if (label.isNotEmpty()) {
                        Text("$label: ${item.baseValue}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (item.statBonuses.isNotEmpty()) {
                    val bonusText = item.statBonuses.entries.joinToString("  ") { "+${it.value} ${it.key}" }
                    Text(bonusText, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Button(onClick = onBuy, enabled = canAfford) {
                Text("$price G")
            }
        }
    }
}