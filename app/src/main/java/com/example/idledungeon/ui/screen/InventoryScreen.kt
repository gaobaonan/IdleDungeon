package com.example.idledungeon.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.idledungeon.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    items: List<Item>,
    gold: Int,
    onSellItem: (Item) -> Unit,
    onBack: () -> Unit
) {
    var selectedItem by remember { mutableStateOf<Item?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        "Equipment / 装备" to ItemType.EQUIPMENT,
        "Consumable / 消耗品" to ItemType.CONSUMABLE,
        "Material / 材料" to ItemType.MATERIAL
    )
    val filteredItems = items.filter { it.type == tabs[selectedTab].second }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory / 道具仓库  (${items.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, (label, type) ->
                    val count = items.count { it.type == type }
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text("$label ($count)") },
                        icon = {}
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                "Gold / 金币: ${gold} G",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Empty / 空", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems) { item ->
                        InventorySlot(item) { selectedItem = item }
                    }
                }
            }
        }
    }

    selectedItem?.let { item ->
        ItemDetailDialog(
            item = item,
            onSell = {
                onSellItem(item)
                selectedItem = null
            },
            onDismiss = { selectedItem = null }
        )
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = item.name.take(2),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center
                )
                if (item.rarity != Rarity.COMMON) {
                    Text(
                        text = item.rarity.name.take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = borderColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemDetailDialog(
    item: Item,
    onSell: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.name)
                Text(
                    item.rarity.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (item.rarity) {
                        Rarity.UNCOMMON -> MaterialTheme.colorScheme.primary
                        Rarity.RARE -> MaterialTheme.colorScheme.tertiary
                        Rarity.EPIC -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val typeText = when (item.type) {
                    ItemType.EQUIPMENT -> {
                        val slotName = when (item.equipSlot) {
                            EquipSlot.WEAPON -> "Weapon / 武器"
                            EquipSlot.ARMOR -> "Armor / 护甲"
                            EquipSlot.ACCESSORY -> "Accessory / 饰品"
                            null -> "Equipment / 装备"
                        }
                        val weaponName = item.weaponType?.let { " (${it.name})" } ?: ""
                        "$slotName$weaponName"
                    }
                    ItemType.CONSUMABLE -> "Consumable / 消耗品"
                    ItemType.MATERIAL -> "Material / 材料"
                }
                Text(typeText, style = MaterialTheme.typography.bodySmall)
                if (item.description.isNotEmpty()) {
                    Text(item.description, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
                if (item.baseValue > 0 && item.equipSlot != null) {
                    val label = when (item.equipSlot) {
                        EquipSlot.WEAPON -> "ATK / 攻击力"
                        EquipSlot.ARMOR -> "DEF / 防御力"
                        else -> "Value / 数值"
                    }
                    Text("$label: ${item.baseValue}", style = MaterialTheme.typography.bodyMedium)
                }
                if (item.statBonuses.isNotEmpty()) {
                    Text("Bonus / 加成:", style = MaterialTheme.typography.labelMedium)
                    item.statBonuses.forEach { (stat, value) ->
                        Text("  +$value ${stat.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (item.statRequirements.isNotEmpty()) {
                    Text("Requirements / 装备需求:", style = MaterialTheme.typography.labelMedium)
                    item.statRequirements.forEach { (stat, req) ->
                        Text("  ${stat.name} >= $req", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider()
                Text("Sell price / 售价: ${item.sellPrice} G",
                    style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Button(onClick = onSell, colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )) {
                Text("Sell / 出售 (${item.sellPrice} G)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close / 关闭") }
        }
    )
}
