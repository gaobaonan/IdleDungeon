package com.example.idledungeon.ui.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.idledungeon.model.Hero
import com.example.idledungeon.model.PartySlot
import com.example.idledungeon.model.Position

/**
 * 队伍编成界面
 * 上半部分: 4个站位槽 (前排2 + 后排2)
 * 下半部分: 可选冒险者列表
 * 点击冒险者 → 加入队伍; 点击已编入的 → 移除
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartySetupScreen(
    party: List<PartySlot>,
    availableHeroes: List<Hero>,
    allHeroes: Map<String, Hero>,
    onAddToParty: (heroId: String, position: Position) -> Unit,
    onRemoveFromParty: (heroId: String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Party / 队伍编成") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = party.isNotEmpty()
            ) { Text("Next / 下一步") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // ── 前排 ──
            Text("Front Row / 前排", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val frontSlots = party.filter { it.position == Position.FRONT }
                repeat(2) { i ->
                    val slot = frontSlots.getOrNull(i)
                    PartySlotBox(
                        hero = slot?.let { allHeroes[it.heroId] },
                        onRemove = { slot?.let { onRemoveFromParty(it.heroId) } }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 后排 ──
            Text("Back Row / 后排", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val backSlots = party.filter { it.position == Position.BACK }
                repeat(2) { i ->
                    val slot = backSlots.getOrNull(i)
                    PartySlotBox(
                        hero = slot?.let { allHeroes[it.heroId] },
                        onRemove = { slot?.let { onRemoveFromParty(it.heroId) } }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Available Heroes / 可选冒险者", style = MaterialTheme.typography.titleSmall)

            // ── 可选列表 ──
            val frontCount = party.count { it.position == Position.FRONT }
            val backCount = party.count { it.position == Position.BACK }

            LazyColumn {
                items(availableHeroes) { hero ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${hero.name}  Lv.${hero.level}  ATK:${hero.attack}",
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = { onAddToParty(hero.id, Position.FRONT) },
                                enabled = frontCount < 2,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(32.dp)
                            ) { Text("Front/前排") }
                            Spacer(Modifier.width(4.dp))
                            FilledTonalButton(
                                onClick = { onAddToParty(hero.id, Position.BACK) },
                                enabled = backCount < 2,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(32.dp)
                            ) { Text("Back/后排") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartySlotBox(hero: Hero?, onRemove: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.size(100.dp, 80.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (hero != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(hero.name, style = MaterialTheme.typography.labelMedium)
                    Text("Lv.${hero.level}", style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                Text("Empty\n空位", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}