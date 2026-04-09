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
import androidx.compose.ui.unit.dp
import com.example.idledungeon.model.Item
import com.example.idledungeon.model.TacticType

/**
 * 战术选择 + 道具选择界面
 * - 上半: 选择战术倾向 (SAFE / BALANCED / AGGRESSIVE)
 * - 下半: 网格选择要携带的消耗品
 * - 底部: 完成按钮 → 开始探险
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TacticSetupScreen(
    selectedTactic: TacticType,
    consumables: List<Item>,
    carriedItemIds: List<String>,
    maxCarry: Int,
    onTacticChange: (TacticType) -> Unit,
    onToggleItem: (String) -> Unit,
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
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // ── 战术选择 ──
            Text("Tactic / 战术", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TacticType.entries.forEach { tactic ->
                    val label = when (tactic) {
                        TacticType.SAFE -> "Safe\n稳扎稳打"
                        TacticType.BALANCED -> "Balanced\n平衡"
                        TacticType.AGGRESSIVE -> "Aggressive\n孤注一掷"
                    }
                    FilterChip(
                        selected = tactic == selectedTactic,
                        onClick = { onTacticChange(tactic) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── 携带道具 ──
            Text(
                "Carry Items / 携带道具 (${carriedItemIds.size}/$maxCarry)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(consumables) { item ->
                    val isSelected = item.id in carriedItemIds
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleItem(item.id) },
                        label = { Text(item.name) }
                    )
                }
            }
        }
    }
}