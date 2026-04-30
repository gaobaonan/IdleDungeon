package com.example.idledungeon.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import com.example.idledungeon.model.Item
import com.example.idledungeon.model.SecondaryTactic
import com.example.idledungeon.model.TacticType

/**
 * 战术选择 + 道具选择界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TacticSetupScreen(
    selectedTactic: TacticType,
    selectedSecondaryTactic: SecondaryTactic,
    consumables: List<Item>,
    carriedItemIds: List<String>,
    maxCarry: Int,
    defaultSupply: Int,           // 出战默认补给 (酒馆等级决定)
    purchasedSupply: Int,         // 战术界面已加购的额外补给
    gold: Int,                    // 当前金币 (限制能否再买)
    onTacticChange: (TacticType) -> Unit,
    onSecondaryTacticChange: (SecondaryTactic) -> Unit,
    onAddItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onBuySupply: () -> Unit,      // +5 补给, -1 金币
    onRefundSupply: () -> Unit,   // -5 补给, +1 金币
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preparation / 出发准备") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) { Text("Depart! / 出发！") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            // ── 主战术 (撤退条件) ──
            Text("Primary Tactic / 主战术", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TacticType.entries.forEach { tactic ->
                    val label = when (tactic) {
                        TacticType.SAFE -> "Safe\n稳扎稳打"
                        TacticType.BALANCED -> "Balanced\n平衡"
                        TacticType.RISKY -> "Risky\n险中求胜"
                        TacticType.AGGRESSIVE -> "Aggro\n孤注一掷"
                    }
                    FilterChip(
                        selected = tactic == selectedTactic,
                        onClick = { onTacticChange(tactic) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── 副战术 (探索策略) ──
            Text("Secondary Tactic / 副战术", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SecondaryTactic.entries.forEach { tactic ->
                    val label = when (tactic) {
                        SecondaryTactic.STRATEGY -> "Strategy\n攻略优先"
                        SecondaryTactic.SCAVENGE -> "Scavenge\n搜刮优先"
                        SecondaryTactic.BOSS_RUSH -> "Boss Rush\nBoss优先"
                    }
                    FilterChip(
                        selected = tactic == selectedSecondaryTactic,
                        onClick = { onSecondaryTacticChange(tactic) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── 补给购买 ──
            Text(
                "Supplies / 补给",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("默认补给", style = MaterialTheme.typography.bodyMedium)
                        Text("$defaultSupply", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("加购补给", style = MaterialTheme.typography.bodyMedium)
                        Text("+$purchasedSupply  (花费 ${purchasedSupply / 5} G)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("出战总补给",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text("${defaultSupply + purchasedSupply}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledIconButton(
                            onClick = onRefundSupply,
                            enabled = purchasedSupply >= 5,
                            modifier = Modifier.size(36.dp)
                        ) { Text("-") }
                        Text(
                            "1G = +5 补给  (持有 $gold G)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        FilledIconButton(
                            onClick = onBuySupply,
                            enabled = gold >= 1,
                            modifier = Modifier.size(36.dp)
                        ) { Text("+") }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── 携带道具 ──
            Text(
                "Carry Items / 携带道具 (${carriedItemIds.size}/$maxCarry)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))

            // 按种类合并仓库中的消耗品
            val grouped = consumables.groupBy { it.id }

            if (grouped.isEmpty()) {
                Text("No consumables / 仓库中没有消耗品",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline)
            } else {
                // 不能用 LazyColumn: 父级是 verticalScroll, 嵌套两个无限高滚动容器会闪退
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    grouped.entries.forEach { (itemId, itemList) ->
                        val item = itemList.first()
                        val ownedCount = itemList.size
                        val carriedCount = carriedItemIds.count { it == itemId }
                        val totalCarried = carriedItemIds.size

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                                    Text(item.description, style = MaterialTheme.typography.bodySmall)
                                    Text("Owned / 持有: $ownedCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                // -/数量/+ 控件
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledIconButton(
                                        onClick = { onRemoveItem(itemId) },
                                        enabled = carriedCount > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) { Text("-") }

                                    Text(
                                        "$carriedCount",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )

                                    FilledIconButton(
                                        onClick = { onAddItem(itemId) },
                                        enabled = carriedCount < ownedCount && totalCarried < maxCarry,
                                        modifier = Modifier.size(32.dp)
                                    ) { Text("+") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
