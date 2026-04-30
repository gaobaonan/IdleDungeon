package com.example.idledungeon.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.idledungeon.model.Quest
import com.example.idledungeon.model.QuestType

/**
 * 公会界面
 * - 显示公会等级 (只读, 自动升级, 不再可投资)
 * - 主线任务列表 (当前任务高亮 + 进度, 已完成置灰打勾, 未来锁定)
 * - 进入此页面会自动清掉"任务更新"红感叹号
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuildScreen(
    guildLevel: Int,
    quests: List<Quest>,
    currentQuestIndex: Int,
    roomsExplored: Int,
    onMarkRead: () -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { onMarkRead() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guild / 公会  Lv.$guildLevel") },
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
                    Text(
                        "完成主线任务自动升级",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Main Quests / 主线任务", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(quests) { index, quest ->
                    QuestCard(
                        quest = quest,
                        isCurrent = index == currentQuestIndex,
                        isLocked = index > currentQuestIndex,
                        roomsExplored = roomsExplored
                    )
                }
                if (currentQuestIndex >= quests.size) {
                    item {
                        Text(
                            "★ 主线全部完成 ★",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestCard(
    quest: Quest,
    isCurrent: Boolean,
    isLocked: Boolean,
    roomsExplored: Int
) {
    val container = when {
        quest.isCompleted -> MaterialTheme.colorScheme.surfaceVariant
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val titlePrefix = when {
        quest.isCompleted -> "✓ "
        isLocked -> "🔒 "
        isCurrent -> "▶ "
        else -> ""
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = titlePrefix + quest.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isLocked) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                quest.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isLocked) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 当前任务展示进度
            if (isCurrent && !quest.isCompleted) {
                Spacer(Modifier.height(4.dp))
                when (quest.type) {
                    QuestType.EXPLORE_ROOMS -> {
                        val ratio = (roomsExplored.toFloat() / quest.targetCount).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                        Text(
                            "进度: $roomsExplored / ${quest.targetCount} 房间",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    QuestType.DEFEAT_BOSS -> {
                        Text(
                            "进度: 前往第 ${quest.targetFloor} 层击败 Boss",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    QuestType.FIND_ITEM -> {
                        Text(
                            "进度: 在地下城中获得指定物品",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
