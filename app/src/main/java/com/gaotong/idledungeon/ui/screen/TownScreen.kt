package com.gaotong.idledungeon.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 城镇主界面
 * 上方: 玩家头像 + 名字 + 金币
 * 中间: 城镇贴图 + 4个建筑图标 + 城门
 * 底部: 3个导航按钮 (冒险者名录 / 道具仓库 / 怪物图鉴)
 */
@Composable
fun TownScreen(
    playerName: String,
    gold: Int,
    guildHasUnreadQuest: Boolean = false,
    onGuildClick: () -> Unit,
    onTavernClick: () -> Unit,
    onBlacksmithClick: () -> Unit,
    onItemShopClick: () -> Unit,
    onGateClick: () -> Unit,
    onRosterClick: () -> Unit,
    onInventoryClick: () -> Unit,
    onBestiaryClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部: 玩家信息栏
        PlayerInfoBar(playerName = playerName, gold = gold)

        // 中间: 城镇区域 (建筑 + 城门)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BuildingIcon(
                    label = "Guild",
                    icon = Icons.Default.AccountBalance,
                    showBadge = guildHasUnreadQuest,
                    onClick = onGuildClick
                )
                BuildingIcon("Tavern", Icons.Default.LocalBar, onClick = onTavernClick)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BuildingIcon("Smith", Icons.Default.Build, onClick = onBlacksmithClick)
                BuildingIcon("Shop", Icons.Default.ShoppingCart, onClick = onItemShopClick)
            }
            // 城门
            FilledTonalButton(onClick = onGateClick) {
                Icon(Icons.Default.MeetingRoom, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Enter Dungeon")
            }
        }

        // 底部: 三个导航按钮
        NavigationBar {
            NavigationBarItem(
                icon = { Icon(Icons.Default.People, contentDescription = null) },
                label = { Text("Roster") },
                selected = false,
                onClick = onRosterClick
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.Inventory, contentDescription = null) },
                label = { Text("Items") },
                selected = false,
                onClick = onInventoryClick
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                label = { Text("Codex") },
                selected = false,
                onClick = onBestiaryClick
            )
        }
    }
}

@Composable
private fun PlayerInfoBar(playerName: String, gold: Int) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text(playerName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("$gold G", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BuildingIcon(
    label: String,
    icon: ImageVector,
    showBadge: Boolean = false,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.size(100.dp).clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
        // 红色感叹号 (任务更新提示)
        if (showBadge) {
            Surface(
                color = androidx.compose.ui.graphics.Color(0xFFE53935),
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .padding(2.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "!",
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        }
    }
}