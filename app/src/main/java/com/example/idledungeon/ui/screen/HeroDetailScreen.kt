package com.example.idledungeon.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.idledungeon.data.SpellRegistry
import com.example.idledungeon.model.*

/**
 * 英雄详情界面
 * - 名字 (可编辑) / 头像 (可从相册选择) / 职业 (可更换)
 * - 等级 / HP / MP / 攻击 / 防御
 * - 四维属性 (STR/DEX/INT/FTH)
 * - 装备栏 (点击可更换，带属性需求检查)
 * - 特质列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroDetailScreen(
    hero: Hero,
    inventory: List<Item>,
    onBack: () -> Unit,
    onEditName: () -> Unit,
    onEditPortrait: () -> Unit,
    onChangeClass: () -> Unit,
    onEquip: (Item) -> Unit,
    onUnequip: (EquipSlot) -> Unit,
    meetsRequirements: (Hero, Item) -> Boolean,
    onAllocateStat: (StatType) -> Unit = {},
    onAllocateHp: () -> Unit = {},
    onAllocateMp: () -> Unit = {}
) {
    var selectedSlot by remember { mutableStateOf<EquipSlot?>(null) }

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
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 头像 + 基础信息 ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Class / 职业: ${hero.heroClass.name}")
                        TextButton(onClick = onChangeClass) { Text("Change / 更换") }
                    }
                    Text("Lv.${hero.level}  CP:${hero.combatPower}")
                    // EXP 进度条
                    val expProgress = if (hero.expToNextLevel > 0) hero.exp.toFloat() / hero.expToNextLevel else 0f
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("EXP:", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(4.dp))
                        LinearProgressIndicator(
                            progress = { expProgress },
                            modifier = Modifier.weight(1f).height(8.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("${hero.exp}/${hero.expToNextLevel}",
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("HP: ${hero.currentHp}/${hero.maxHp}    MP: ${hero.currentMp}/${hero.maxMp}")
                    Text("ATK: ${hero.attack}    DEF: ${hero.defense}")
                    if (hero.unspentStatPoints > 0 || hero.unspentSkillPoints > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Unspent / 未分配: ${hero.unspentStatPoints} stat pts, ${hero.unspentSkillPoints} skill pts",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ── 四维属性 (基础 + 装备加成) + 分配按钮 ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Stats / 属性", style = MaterialTheme.typography.titleSmall)
                    val equipBonuses = mutableMapOf<StatType, Int>()
                    hero.equipment.values.forEach { equip ->
                        equip.statBonuses.forEach { (stat, v) ->
                            equipBonuses[stat] = (equipBonuses[stat] ?: 0) + v
                        }
                    }
                    val hasPoints = hero.unspentStatPoints > 0
                    StatType.entries.forEach { stat ->
                        val base = hero.baseStats[stat] ?: 0
                        val bonus = equipBonuses[stat] ?: 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val text = if (bonus > 0) "${stat.name}: $base + $bonus = ${base + bonus}"
                                       else "${stat.name}: $base"
                            Text(text, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            if (hasPoints) {
                                FilledIconButton(
                                    onClick = { onAllocateStat(stat) },
                                    modifier = Modifier.size(28.dp)
                                ) { Text("+", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                    // HP / MP 分配
                    if (hasPoints) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onAllocateHp,
                                modifier = Modifier.weight(1f)
                            ) { Text("+50 HP") }
                            OutlinedButton(
                                onClick = onAllocateMp,
                                modifier = Modifier.weight(1f)
                            ) { Text("+20 MP") }
                        }
                        Text("Remaining / 剩余: ${hero.unspentStatPoints} pts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ── 装备栏 ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Equipment / 装备", style = MaterialTheme.typography.titleSmall)
                    EquipSlot.entries.forEach { slot ->
                        val item = hero.equipment[slot]
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${slot.name}: ", modifier = Modifier.width(100.dp))
                                if (item != null) {
                                    TextButton(onClick = { selectedSlot = slot }) {
                                        Text(item.name)
                                    }
                                    TextButton(onClick = { onUnequip(slot) }) {
                                        Text("Unequip / 卸下",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                } else {
                                    TextButton(onClick = { selectedSlot = slot }) {
                                        Text("(Empty / 空)")
                                    }
                                }
                            }
                            // 显示装备提供的加成
                            if (item != null) {
                                val details = mutableListOf<String>()
                                if (item.baseValue > 0 && item.equipSlot == EquipSlot.WEAPON)
                                    details.add("ATK +${item.baseValue}")
                                if (item.baseValue > 0 && item.equipSlot == EquipSlot.ARMOR)
                                    details.add("DEF +${item.baseValue}")
                                item.statBonuses.forEach { (stat, v) ->
                                    details.add("${stat.name} +$v")
                                }
                                if (details.isNotEmpty()) {
                                    Text(
                                        "    ${details.joinToString("  ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 魔法 & 奇迹 ──
            if (hero.spells.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val magicSpells = hero.spells.filter {
                            SpellRegistry.get(it.templateId).type == SpellType.MAGIC
                        }
                        val miracleSpells = hero.spells.filter {
                            SpellRegistry.get(it.templateId).type == SpellType.MIRACLE
                        }

                        if (magicSpells.isNotEmpty()) {
                            Text("Magic / 魔法 (INT)", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            magicSpells.forEach { spell ->
                                SpellRow(spell, hero.level)
                            }
                        }
                        if (magicSpells.isNotEmpty() && miracleSpells.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                        }
                        if (miracleSpells.isNotEmpty()) {
                            Text("Miracles / 奇迹 (FTH)", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            miracleSpells.forEach { spell ->
                                SpellRow(spell, hero.level)
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

    // ── 装备选择弹窗 ──
    if (selectedSlot != null) {
        EquipmentPickerDialog(
            slot = selectedSlot!!,
            hero = hero,
            inventory = inventory,
            meetsRequirements = meetsRequirements,
            onSelect = { item ->
                onEquip(item)
                selectedSlot = null
            },
            onDismiss = { selectedSlot = null }
        )
    }
}

@Composable
private fun EquipmentPickerDialog(
    slot: EquipSlot,
    hero: Hero,
    inventory: List<Item>,
    meetsRequirements: (Hero, Item) -> Boolean,
    onSelect: (Item) -> Unit,
    onDismiss: () -> Unit
) {
    val candidates = inventory.filter { it.equipSlot == slot }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val slotName = when (slot) {
                EquipSlot.WEAPON -> "Weapon / 武器"
                EquipSlot.ARMOR -> "Armor / 护甲"
                EquipSlot.ACCESSORY -> "Accessory / 饰品"
            }
            Text("Select $slotName")
        },
        text = {
            if (candidates.isEmpty()) {
                Text("No available equipment in inventory.\n仓库中没有可装备的物品。")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(candidates) { item ->
                        val meets = meetsRequirements(hero, item)
                        EquipCandidateCard(item, hero, meets) { onSelect(item) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel / 取消") }
        }
    )
}

@Composable
private fun EquipCandidateCard(
    item: Item,
    hero: Hero,
    meetsReq: Boolean,
    onSelect: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    item.rarity.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (item.rarity) {
                        Rarity.UNCOMMON -> MaterialTheme.colorScheme.primary
                        Rarity.RARE -> MaterialTheme.colorScheme.tertiary
                        Rarity.EPIC -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            if (item.baseValue > 0 && item.equipSlot != null) {
                val label = when (item.equipSlot) {
                    EquipSlot.WEAPON -> "ATK"
                    EquipSlot.ARMOR -> "DEF"
                    else -> ""
                }
                if (label.isNotEmpty()) {
                    Text("$label: ${item.baseValue}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (item.statBonuses.isNotEmpty()) {
                val bonusText = item.statBonuses.entries.joinToString("  ") { "+${it.value} ${it.key}" }
                Text(bonusText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            // 需求显示
            if (item.statRequirements.isNotEmpty()) {
                val reqText = item.statRequirements.entries.joinToString("  ") { (stat, req) ->
                    val heroVal = hero.baseStats[stat] ?: 0
                    val ok = heroVal >= req
                    "${stat.name} $heroVal/$req${if (ok) " OK" else " X"}"
                }
                Text(
                    "Require / 需求: $reqText",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (meetsReq) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onSelect,
                enabled = meetsReq,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (meetsReq) "Equip / 装备" else "Requirements not met / 属性不足")
            }
        }
    }
}

@Composable
private fun SpellRow(spell: HeroSpell, heroLevel: Int) {
    val template = SpellRegistry.get(spell.templateId)
    val tier = template.tiers[spell.currentTier - 1]

    // 功能类型标签 & 颜色
    val (targetLabel, targetColor) = when (template.target) {
        SpellTarget.DAMAGE_SINGLE -> "Single DMG / 单体伤害" to MaterialTheme.colorScheme.error
        SpellTarget.DAMAGE_AOE    -> "AOE DMG / 群体伤害" to MaterialTheme.colorScheme.error
        SpellTarget.HEAL_SINGLE   -> "Single Heal / 单体治疗" to MaterialTheme.colorScheme.primary
        SpellTarget.HEAL_AOE      -> "AOE Heal / 群体治疗" to MaterialTheme.colorScheme.primary
        SpellTarget.BUFF_SINGLE   -> "Buff ${template.affectedStat?.name ?: ""}" to MaterialTheme.colorScheme.tertiary
        SpellTarget.DEBUFF_SINGLE -> "Debuff ${template.affectedStat?.name ?: ""}" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    // 数值标签根据类型变化
    val powerLabel = when (template.target) {
        SpellTarget.DAMAGE_SINGLE, SpellTarget.DAMAGE_AOE -> "DMG / 伤害"
        SpellTarget.HEAL_SINGLE, SpellTarget.HEAL_AOE -> "Heal / 治疗量"
        SpellTarget.BUFF_SINGLE -> "+${template.affectedStat?.name ?: ""}"
        SpellTarget.DEBUFF_SINGLE -> "-${template.affectedStat?.name ?: ""}"
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 名称 + 阶级星标
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tier.name, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${"★".repeat(spell.currentTier)}${"☆".repeat(4 - spell.currentTier)}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            // 功能类型标签
            Text(targetLabel, style = MaterialTheme.typography.labelSmall, color = targetColor)
        }
        // 数值 / MP / 描述
        Text(
            "$powerLabel: ${tier.power}    MP: ${tier.mpCost}",
            style = MaterialTheme.typography.labelSmall
        )
        Text(tier.description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 下一阶级提示
        if (spell.currentTier < 4) {
            val nextTier = spell.currentTier + 1
            val reqLv = HeroSpell.requiredLevel(nextTier)
            val nextName = template.tiers[nextTier - 1].name
            val canUpgrade = heroLevel >= reqLv
            Text(
                "Next / 下一阶: $nextName (Lv.$reqLv${if (canUpgrade) " OK" else ""})",
                style = MaterialTheme.typography.labelSmall,
                color = if (canUpgrade) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
            )
        } else {
            Text("MAX / 已满阶", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}
