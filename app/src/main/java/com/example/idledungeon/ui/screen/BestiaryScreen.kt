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
import com.example.idledungeon.model.Monster
import com.example.idledungeon.model.MonsterCodexEntry

/**
 * 怪物图鉴界面
 * - 未解锁: 问号贴图 + ???
 * - 已解锁: 名字 + 贴图 + 点击查看详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BestiaryScreen(
    entries: List<Pair<MonsterCodexEntry, Monster?>>,
    onMonsterClick: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bestiary / 怪物图鉴") },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries) { (entry, monster) ->
                CodexCard(entry, monster) { onMonsterClick(entry.monsterId) }
            }
        }
    }
}

@Composable
private fun CodexCard(
    entry: MonsterCodexEntry,
    monster: Monster?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = entry.unlocked, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (entry.unlocked && monster != null) {
                // TODO: 怪物贴图
                Text("👹", modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(monster.name, style = MaterialTheme.typography.titleSmall)
                    Text("Lv.${monster.level}  Defeated: ${entry.killCount}x",
                        style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Icon(Icons.Default.Close, contentDescription = null,
                    modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Text("??? / 未发现", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}