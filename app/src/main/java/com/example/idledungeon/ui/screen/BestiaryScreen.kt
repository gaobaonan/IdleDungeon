package com.example.idledungeon.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.idledungeon.data.MonsterRegistry
import com.example.idledungeon.model.Monster

/**
 * 怪物图鉴
 * - 已击败过的: 显示完整数据 (HP/ATK/DEF/技能/掉落), 击败次数
 * - 未击败的: ??? 占位, 仅显示等级
 *
 * 数据源: 从 [MonsterRegistry] 取所有模板, 配 [monsterKills] 检查解锁状态.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BestiaryScreen(
    monsterKills: Map<String, Int>,
    onBack: () -> Unit
) {
    // 按等级排序, Boss 放最后
    val all = MonsterRegistry.all().sortedWith(
        compareBy<Monster> { it.id.startsWith("boss_") }
            .thenBy { it.level }
            .thenBy { it.name }
    )
    val unlockedCount = monsterKills.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bestiary / 怪物图鉴 ($unlockedCount/${all.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(all, key = { it.id }) { monster ->
                val killCount = monsterKills[monster.id] ?: 0
                CodexCard(monster, killCount)
            }
        }
    }
}

@Composable
private fun CodexCard(monster: Monster, killCount: Int) {
    val unlocked = killCount > 0
    val isBoss = monster.id.startsWith("boss_")
    val container = when {
        !unlocked -> MaterialTheme.colorScheme.surfaceVariant
        isBoss -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (unlocked) "👹" else "❓",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    if (unlocked) {
                        Text(
                            (if (isBoss) "★ " else "") + monster.name,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Lv.${monster.level}  CP:${monster.combatPower}  击杀 ×$killCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text("??? / 未发现", style = MaterialTheme.typography.titleSmall)
                        Text("Lv.${monster.level}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            if (unlocked) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text(
                    "HP ${monster.maxHp}    ATK ${monster.attack}    DEF ${monster.defense}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (monster.skills.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("技能:", style = MaterialTheme.typography.labelMedium)
                    monster.skills.forEach { sk ->
                        Text(
                            "  • ${sk.name} [${sk.effectType.name}] x${sk.powerMultiplier}, ${sk.triggerChance}% 触发",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                if (monster.dropChances.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("掉落:", style = MaterialTheme.typography.labelMedium)
                    monster.dropChances.forEach { (id, chance) ->
                        Text(
                            "  • $id ($chance%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
