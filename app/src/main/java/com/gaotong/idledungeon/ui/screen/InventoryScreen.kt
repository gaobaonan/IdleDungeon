package com.gaotong.idledungeon.ui.screen

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gaotong.idledungeon.R
import com.gaotong.idledungeon.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    items: List<Item>,
    gold: Int,
    onSellOne: (String) -> Unit,
    onSellAll: (String) -> Unit,
    onBack: () -> Unit
) {
    // 存 ID 而不是 Item 实例, 卖完最后一个时仓库里再没这个 ID 也不会留下"幽灵"对话框引用
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        "Equipment" to ItemType.EQUIPMENT,
        "Consumable" to ItemType.CONSUMABLE,
        "Material" to ItemType.MATERIAL
    )
    val filteredItems = items.filter { it.type == tabs[selectedTab].second }
    // 同 ID 物品堆叠成一格
    val stacks = filteredItems.groupBy { it.id }.map { (_, list) -> list.first() to list.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory  (${items.size})") },
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
                "Gold: $gold G",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (stacks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Empty", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(stacks, key = { it.first.id }) { (item, count) ->
                        InventorySlot(item, count) { selectedId = item.id }
                    }
                }
            }
        }
    }

    // 通过 ID 查询当前堆叠 (count 实时反映, 卖出后会立刻刷新)
    val selectedStack = selectedId?.let { id ->
        val matching = items.filter { it.id == id }
        if (matching.isEmpty()) null else matching.first() to matching.size
    }
    if (selectedStack == null && selectedId != null) {
        // 卖完了, 自动关
        LaunchedEffect(selectedId) { selectedId = null }
    }
    selectedStack?.let { (item, count) ->
        ItemDetailDialog(
            item = item,
            count = count,
            onSellOne = { onSellOne(item.id) },
            onSellAll = {
                onSellAll(item.id)
                selectedId = null
            },
            onDismiss = { selectedId = null }
        )
    }
}

/** 稀有度对应的颜色 (重用于多个屏幕) */
@Composable
fun rarityColor(rarity: Rarity): Color = when (rarity) {
    Rarity.COMMON -> MaterialTheme.colorScheme.outline
    Rarity.UNCOMMON -> MaterialTheme.colorScheme.primary
    Rarity.RARE -> MaterialTheme.colorScheme.tertiary
    Rarity.EPIC -> MaterialTheme.colorScheme.error
    Rarity.LEGENDARY -> Color(0xFFFFB300)  // 琥珀金
}

/** 通用物品图标，无图就用 empty 占位 */
@Composable
fun ItemIcon(item: Item, modifier: Modifier = Modifier) {
    val resId = if (item.iconRes != 0) item.iconRes else R.drawable.empty
    Image(
        painter = painterResource(resId),
        contentDescription = item.name,
        modifier = modifier
    )
}

@Composable
private fun InventorySlot(item: Item, count: Int, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.aspectRatio(1f).clickable(onClick = onClick),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 2.dp,
            brush = androidx.compose.ui.graphics.SolidColor(rarityColor(item.rarity))
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ItemIcon(item, modifier = Modifier.fillMaxSize().padding(4.dp))
            // 等级角标 (左上)
            Text(
                text = "L${item.equipLevel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.TopStart).padding(2.dp)
            )
            // 名称占底部
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = rarityColor(item.rarity),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            )
            // 数量角标 (右下), 仅在 >1 时显示
            if (count > 1) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp)
                ) {
                    Text(
                        "×$count",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemDetailDialog(
    item: Item,
    count: Int,
    onSellOne: () -> Unit,
    onSellAll: () -> Unit,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemIcon(item, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(item.name)
                        if (count > 1) {
                            Text("Held ×$count",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    "${item.rarity.displayName()} L${item.equipLevel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = rarityColor(item.rarity)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val typeText = when (item.type) {
                    ItemType.EQUIPMENT -> {
                        val slotName = when (item.equipSlot) {
                            EquipSlot.WEAPON -> "Weapon"
                            EquipSlot.ARMOR -> "Armor"
                            EquipSlot.ACCESSORY -> "Accessory"
                            null -> "Equipment"
                        }
                        val weaponName = item.weaponType?.let { " (${it.displayName()})" } ?: ""
                        "$slotName$weaponName"
                    }
                    ItemType.CONSUMABLE -> "Consumable"
                    ItemType.MATERIAL -> "Material"
                }
                Text(typeText, style = MaterialTheme.typography.bodySmall)
                if (item.description.isNotEmpty()) {
                    Text(item.description, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
                if (item.baseValue > 0 && item.equipSlot != null) {
                    val label = when (item.equipSlot) {
                        EquipSlot.WEAPON -> "ATK"
                        EquipSlot.ARMOR -> "DEF"
                        else -> "Value"
                    }
                    Text("$label: ${item.baseValue}", style = MaterialTheme.typography.bodyMedium)
                }
                if (item.statBonuses.isNotEmpty()) {
                    Text("Bonus:", style = MaterialTheme.typography.labelMedium)
                    item.statBonuses.forEach { (stat, value) ->
                        Text("  +$value ${stat.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (item.statRequirements.isNotEmpty()) {
                    Text("Requirements:", style = MaterialTheme.typography.labelMedium)
                    item.statRequirements.forEach { (stat, req) ->
                        Text("  ${stat.name} >= $req", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider()
                Text("Unit price: ${item.sellPrice} G",
                    style = MaterialTheme.typography.bodyMedium)
                if (count > 1) {
                    Text("Total: ${item.sellPrice * count} G  (×$count)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(4.dp))
                // 出售按钮放在 text 区, confirmButton 留给 Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSellOne,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Sell One (${item.sellPrice} G)") }
                    if (count > 1) {
                        Button(
                            onClick = onSellAll,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Sell All ×$count") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
