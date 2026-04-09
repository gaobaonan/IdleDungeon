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
import com.example.idledungeon.model.Quest

/**
 * 公会界面
 * - 显示公会等级 / 补给上限
 * - 主线任务列表
 * - 投资升级按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuildScreen(
    guildRank: String,
    guildLevel: Int,
    maxSupplies: Int,
    quests: List<Quest>,
    investCost: Int,
    onInvest: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guild / 公会  Rank: $guildRank") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // 公会状态
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Level / 等级: $guildLevel", style = MaterialTheme.typography.titleMedium)
                    Text("Supply Limit / 补给上限: $maxSupplies")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onInvest) {
                        Text("Invest / 投资 ($investCost G)")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Quests / 主线任务", style = MaterialTheme.typography.titleMedium)

            // 任务列表
            LazyColumn {
                items(quests) { quest ->
                    QuestCard(quest)
                }
            }
        }
    }
}

@Composable
private fun QuestCard(quest: Quest) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = if (quest.isCompleted) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = quest.title + if (quest.isCompleted) " ✓" else "",
                style = MaterialTheme.typography.titleSmall
            )
            Text(quest.description, style = MaterialTheme.typography.bodySmall)
            Text("Reward / 奖励: ${quest.goldReward} G", style = MaterialTheme.typography.labelSmall)
        }
    }
}