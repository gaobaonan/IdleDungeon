package com.gaotong.idledungeon.ui.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.gaotong.idledungeon.data.SpellRegistry
import com.gaotong.idledungeon.model.*
import com.gaotong.idledungeon.ui.component.HeroAvatar

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
    onRename: (String) -> Unit,
    onPortraitSelected: (String?) -> Unit,
    onUpgradeSpell: (String) -> Unit = {},
    onLearnSpell: (String) -> Unit = {},
    onEquip: (Item) -> Unit,
    onUnequip: (EquipSlot) -> Unit,
    meetsRequirements: (Hero, Item) -> Boolean,
    onAllocateStat: (StatType) -> Unit = {},
    onAllocateHp: () -> Unit = {},
    onAllocateMp: () -> Unit = {}
) {
    var selectedSlot by remember { mutableStateOf<EquipSlot?>(null) }
    var renameDialogOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // 取持久权限, 让 URI 在 App 重启后仍可读取
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* 某些 URI 不支持持久权限, 忽略 */ }
            onPortraitSelected(uri.toString())
        }
    }

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
                    IconButton(onClick = { renameDialogOpen = true }) {
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
            // 头像 + 基础信息
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 头像 (点击换图; 长按清空回默认)
                        HeroAvatar(
                            hero = hero,
                            size = 72.dp,
                            modifier = Modifier.clickable {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(hero.name, style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { renameDialogOpen = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename",
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                            Text(
                                hero.heroClass.name,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (hero.portraitUri != null) {
                                TextButton(
                                    onClick = { onPortraitSelected(null) },
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                                ) {
                                    Text("Remove portrait", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
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
                            "Unspent: ${hero.unspentStatPoints} stat pts, ${hero.unspentSkillPoints} skill pts",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 四维属性 (基础 + 装备加成) + 分配按钮
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Stats", style = MaterialTheme.typography.titleSmall)
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
                            FilledIconButton(
                                onClick = { onAllocateStat(stat) },
                                enabled = hasPoints,
                                modifier = Modifier.size(28.dp)
                            ) { Text("+", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    // HP / MP 分配 (常驻，无点数时按钮置灰)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAllocateHp,
                            enabled = hasPoints,
                            modifier = Modifier.weight(1f)
                        ) { Text("+50 HP") }
                        OutlinedButton(
                            onClick = onAllocateMp,
                            enabled = hasPoints,
                            modifier = Modifier.weight(1f)
                        ) { Text("+20 MP") }
                    }
                    Text("Remaining: ${hero.unspentStatPoints} pts",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasPoints) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 装备栏
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Equipment", style = MaterialTheme.typography.titleSmall)
                    EquipSlot.entries.forEach { slot ->
                        val item = hero.equipment[slot]
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${slot.name}: ", modifier = Modifier.width(80.dp))
                                if (item != null) {
                                    ItemIcon(item, modifier = Modifier.size(36.dp).clickable { selectedSlot = slot })
                                    Spacer(Modifier.width(6.dp))
                                    TextButton(onClick = { selectedSlot = slot }) {
                                        Text("${item.name}  L${item.equipLevel} ${item.rarity.displayName()}",
                                            color = rarityColor(item.rarity))
                                    }
                                    TextButton(onClick = { onUnequip(slot) }) {
                                        Text("Unequip",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                } else {
                                    TextButton(onClick = { selectedSlot = slot }) {
                                        Text("(Empty)")
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

            // 熟练度
            if (hero.proficiencyXp.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Proficiency", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        hero.proficiencyXp.forEach { (type, xp) ->
                            ProficiencyRow(type, xp, hero.proficiencyLevel(type))
                        }
                    }
                }
            }

            // 魔法 & 奇迹 (含技能树: 升级 / 学习新法术)
            val canCastClass = hero.heroClass == HeroClass.MAGE || hero.heroClass == HeroClass.CLERIC
            if (hero.spells.isNotEmpty() || canCastClass) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val magicSpells = hero.spells.filter {
                            SpellRegistry.get(it.templateId).type == SpellType.MAGIC
                        }
                        val miracleSpells = hero.spells.filter {
                            SpellRegistry.get(it.templateId).type == SpellType.MIRACLE
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Skill Tree", style = MaterialTheme.typography.titleSmall)
                            Text("SP: ${hero.unspentSkillPoints}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }

                        if (magicSpells.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Magic (INT)", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            magicSpells.forEach { spell ->
                                SpellRow(spell, hero.level, hero.unspentSkillPoints) {
                                    onUpgradeSpell(spell.templateId)
                                }
                            }
                        }
                        if (miracleSpells.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Miracles (FTH)", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            miracleSpells.forEach { spell ->
                                SpellRow(spell, hero.level, hero.unspentSkillPoints) {
                                    onUpgradeSpell(spell.templateId)
                                }
                            }
                        }

                        // 学习新法术 (仅法师/牧师)
                        if (canCastClass) {
                            val ownedIds = hero.spells.map { it.templateId }.toSet()
                            val type = if (hero.heroClass == HeroClass.MAGE) SpellType.MAGIC else SpellType.MIRACLE
                            val learnable = SpellRegistry.byType(type).filter { it.id !in ownedIds }
                            if (learnable.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text("Learnable", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                learnable.forEach { tmpl ->
                                    LearnableSpellRow(
                                        template = tmpl,
                                        canLearn = hero.unspentSkillPoints >= 1,
                                        onLearn = { onLearnSpell(tmpl.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 重命名对话框
    if (renameDialogOpen) {
        var input by remember { mutableStateOf(hero.name) }
        AlertDialog(
            onDismissRequest = { renameDialogOpen = false },
            title = { Text("Rename Hero") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(20) },
                    label = { Text("Name (max 20 chars)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRename(input)
                        renameDialogOpen = false
                    },
                    enabled = input.isNotBlank() && input.trim() != hero.name
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogOpen = false }) { Text("Cancel") }
            }
        )
    }

    // 装备选择弹窗
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
                EquipSlot.WEAPON -> "Weapon"
                EquipSlot.ARMOR -> "Armor"
                EquipSlot.ACCESSORY -> "Accessory"
            }
            Text("Select $slotName")
        },
        text = {
            if (candidates.isEmpty()) {
                Text("No available equipment in inventory.")
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ItemIcon(item, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall,
                        color = rarityColor(item.rarity))
                    Text(
                        "L${item.equipLevel}  ${item.rarity.displayName()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = rarityColor(item.rarity)
                    )
                }
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
            // 职业限制
            val requiredClass = item.weaponType?.requiredClass()
            if (requiredClass != null) {
                val classOk = requiredClass == hero.heroClass
                Text(
                    "Class: ${requiredClass.name}${if (classOk) " OK" else " X"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (classOk) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error
                )
            }
            // 属性需求
            if (item.statRequirements.isNotEmpty()) {
                val reqText = item.statRequirements.entries.joinToString("  ") { (stat, req) ->
                    val heroVal = hero.baseStats[stat] ?: 0
                    val ok = heroVal >= req
                    "${stat.name} $heroVal/$req${if (ok) " OK" else " X"}"
                }
                Text(
                    "Require: $reqText",
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
                val classMismatch = requiredClass != null && requiredClass != hero.heroClass
                Text(when {
                    meetsReq -> "Equip"
                    classMismatch -> "Wrong class"
                    else -> "Requirements not met"
                })
            }
        }
    }
}

@Composable
private fun ProficiencyRow(type: WeaponType, xp: Int, level: Int) {
    val maxLv = Hero.MAX_PROFICIENCY_LEVEL
    val nextThreshold = if (level < maxLv) Hero.proficiencyXpForLevel(level + 1) else xp
    val curThreshold = if (level > 0) Hero.proficiencyXpForLevel(level) else 0
    val progress = if (level >= maxLv) 1f
                   else (xp - curThreshold).toFloat() / (nextThreshold - curThreshold).coerceAtLeast(1)
    val bonusPct = (level * Hero.PROFICIENCY_BONUS_PER_LEVEL * 100).toInt()
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${type.displayName()}  Lv.$level / $maxLv",
                style = MaterialTheme.typography.bodyMedium)
            Text("+${bonusPct}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp)
        )
        Text(
            if (level >= maxLv) "MAX" else "XP $xp / $nextThreshold",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LearnableSpellRow(
    template: SpellTemplate,
    canLearn: Boolean,
    onLearn: () -> Unit
) {
    val firstTier = template.tiers[0]
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(firstTier.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${template.target.name}  Power ${firstTier.power}  MP ${firstTier.mpCost}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            onClick = onLearn,
            enabled = canLearn,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text("Learn (1 SP)", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SpellRow(
    spell: HeroSpell,
    heroLevel: Int,
    unspentSkillPoints: Int,
    onUpgrade: () -> Unit
) {
    val template = SpellRegistry.get(spell.templateId)
    val tier = template.tiers[spell.currentTier - 1]

    // 功能类型标签 & 颜色
    val (targetLabel, targetColor) = when (template.target) {
        SpellTarget.DAMAGE_SINGLE -> "Single DMG" to MaterialTheme.colorScheme.error
        SpellTarget.DAMAGE_AOE    -> "AOE DMG" to MaterialTheme.colorScheme.error
        SpellTarget.HEAL_SINGLE   -> "Single Heal" to MaterialTheme.colorScheme.primary
        SpellTarget.HEAL_AOE      -> "AOE Heal" to MaterialTheme.colorScheme.primary
        SpellTarget.BUFF_SINGLE   -> "Buff ${template.affectedStat?.name ?: ""}" to MaterialTheme.colorScheme.tertiary
        SpellTarget.DEBUFF_SINGLE -> "Debuff ${template.affectedStat?.name ?: ""}" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    // 数值标签根据类型变化
    val powerLabel = when (template.target) {
        SpellTarget.DAMAGE_SINGLE, SpellTarget.DAMAGE_AOE -> "DMG"
        SpellTarget.HEAL_SINGLE, SpellTarget.HEAL_AOE -> "Heal"
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
        // 下一阶级提示 + 升级按钮
        if (spell.currentTier < 4) {
            val nextTier = spell.currentTier + 1
            val reqLv = HeroSpell.requiredLevel(nextTier)
            val nextName = template.tiers[nextTier - 1].name
            val levelOk = heroLevel >= reqLv
            val canUpgrade = levelOk && unspentSkillPoints >= 1
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Next: $nextName (req Lv.$reqLv${if (!levelOk) " — level too low" else ""})",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (levelOk) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onUpgrade,
                    enabled = canUpgrade,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Upgrade (1 SP)", style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            Text("MAX", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}
