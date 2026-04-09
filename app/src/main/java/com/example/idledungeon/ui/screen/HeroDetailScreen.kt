package com.example.idledungeon.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.idledungeon.model.*

/**
 * 英雄详情界面
 * - 名字 (可编辑) / 头像 (可从相册选择) / 职业 (可更换)
 * - 等级 / HP / MP / 攻击 / 防御
 * - 四维属性 (STR/DEX/INT/FTH)
 * - 装备栏 (点击可更换)
 * - 特质列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroDetailScreen(
    hero: Hero,
    onBack: () -> Unit,
    onEditName: () -> Unit,
    onEditPortrait: () -> Unit,
    onChangeClass: () -> Unit,
    onChangeEquipment: (EquipSlot) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(hero.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEditName) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Name")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 头像 + 基础信息 ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // TODO: 头像 (Coil AsyncImage) + 点击 onEditPortrait
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Class / 职业: ${hero.heroClass.name}")
                        TextButton(onClick = onChangeClass) { Text("Change / 更换") }
                    }
                    Text("Lv.${hero.level}  EXP: ${hero.exp}")
                    Spacer(Modifier.height(8.dp))
                    Text("HP: ${hero.currentHp}/${hero.maxHp}    MP: ${hero.currentMp}/${hero.maxMp}")
                    Text("ATK: ${hero.attack}    DEF: ${hero.defense}")
                }
            }

            // ── 四维属性 ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Stats / 属性", style = MaterialTheme.typography.titleSmall)
                    StatType.entries.forEach { stat ->
                        val value = hero.baseStats[stat] ?: 0
                        Text("${stat.name}: $value", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // ── 装备栏 ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Equipment / 装备", style = MaterialTheme.typography.titleSmall)
                    EquipSlot.entries.forEach { slot ->
                        val item = hero.equipment[slot]
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("${slot.name}: ", modifier = Modifier.width(100.dp))
                            TextButton(onClick = { onChangeEquipment(slot) }) {
                                Text(item?.name ?: "(Empty / 空)")
                            }
                        }
                    }
                }
            }

            // ── 特质 ──
            if (hero.traits.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Traits / 特质", style = MaterialTheme.typography.titleSmall)
                        hero.traits.forEach { trait ->
                            val prefix = if (trait.isPositive) "+" else "-"
                            Text("$prefix ${trait.name}: ${trait.description}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}