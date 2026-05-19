package com.example.idledungeon.data

import com.example.idledungeon.model.*

/**
 * 魔法 & 奇迹注册表
 *
 * 四种功能类型:
 *   DAMAGE  — 单体 / 群体伤害
 *   HEAL    — 单体 / 群体治疗
 *   BUFF    — 增益我方单体属性
 *   DEBUFF  — 减益敌方单体属性
 *
 * 每个模板 4 阶: 阶级 1 (初始) → 2 (Lv4) → 3 (Lv7) → 4 (Lv10)
 * MAGIC 由 INT 驱动, MIRACLE 由 FTH 驱动
 */
object SpellRegistry {

    private val templates = listOf(
        // ============================================================
        //  魔法 (MAGIC) - 智力系
        // ============================================================

        // ── 单体伤害: 火 ──
        SpellTemplate(
            id = "fire", type = SpellType.MAGIC,
            target = SpellTarget.DAMAGE_SINGLE,
            tiers = listOf(
                SpellTier("Fireball",     "Hurl a small fireball",                12, 4),
                SpellTier("Greater Fireball",   "A larger fireball with doubled power", 28, 9),
                SpellTier("Flame Ray", "Spray a searing ray of flame",         50, 16),
                SpellTier("Firestorm", "Summon a devastating storm of fire",   80, 26)
            )
        ),

        // ── 单体伤害: 雷 ──
        SpellTemplate(
            id = "lightning", type = SpellType.MAGIC,
            target = SpellTarget.DAMAGE_SINGLE,
            tiers = listOf(
                SpellTier("Spark",   "Release a faint electric current",    11, 4),
                SpellTier("Lightning",     "A precise bolt of lightning strikes down", 26, 9),
                SpellTier("Thunderstorm",     "Summon thunderclouds to rain down lightning", 48, 16),
                SpellTier("Divine Judgment",     "Divine thunder falls from the heavens", 85, 28)
            )
        ),

        // ── 群体伤害: 冰 ──
        SpellTemplate(
            id = "blizzard", type = SpellType.MAGIC,
            target = SpellTarget.DAMAGE_AOE,
            tiers = listOf(
                SpellTier("Ice Spike",     "Launch a sharp spike of ice",       8,  4),
                SpellTier("Ice Shards",     "Fire multiple ice shards at once",  18, 8),
                SpellTier("Blizzard",   "Bring down a blizzard over the area", 35, 15),
                SpellTier("Absolute Zero", "A field of extreme cold that freezes everything", 58, 24)
            )
        ),

        // ── 群体伤害: 暗 ──
        SpellTemplate(
            id = "dark_wave", type = SpellType.MAGIC,
            target = SpellTarget.DAMAGE_AOE,
            tiers = listOf(
                SpellTier("Shadow Bolt",   "An arrow forged from condensed shadow", 7,  4),
                SpellTier("Shadow Burst", "Shadow energy bursts outward",       16, 9),
                SpellTier("Shadow Realm", "Unfold a realm that devours light",  32, 17),
                SpellTier("Void Devour", "Summon the void to consume all",     55, 30)
            )
        ),

        // ── BUFF INT: 灵光 ──
        SpellTemplate(
            id = "mind_sharpen", type = SpellType.MAGIC,
            target = SpellTarget.BUFF_SINGLE, affectedStat = StatType.INT,
            tiers = listOf(
                SpellTier("Insight",       "A faint light of intellect envelops the target", 2, 4),
                SpellTier("Light of Wisdom",   "Thoughts become extraordinarily clear", 4, 8),
                SpellTier("Great Wisdom",     "The mind races, perceiving everything", 7, 14),
                SpellTier("Eye of Omniscience",   "Gain a fleeting near-omniscient revelation", 10, 22)
            )
        ),

        // ── BUFF DEX: 迅捷 ──
        SpellTemplate(
            id = "haste", type = SpellType.MAGIC,
            target = SpellTarget.BUFF_SINGLE, affectedStat = StatType.DEX,
            tiers = listOf(
                SpellTier("Haste",   "The target's movements slightly quicken", 2, 3),
                SpellTier("Gale Step",   "Move as swift as the wind",         4, 7),
                SpellTier("Lightning Reflexes", "Reaction speed pushed to the limit", 6, 12),
                SpellTier("Time Acceleration", "Warp the flow of time, near-teleportation", 9, 20)
            )
        ),

        // ── DEBUFF STR: 虚弱 ──
        SpellTemplate(
            id = "weaken", type = SpellType.MAGIC,
            target = SpellTarget.DEBUFF_SINGLE, affectedStat = StatType.STR,
            tiers = listOf(
                SpellTier("Weaken",   "The target feels a wave of weakness",  2, 4),
                SpellTier("Enfeeble",   "Strength is greatly diminished",     4, 8),
                SpellTier("Curse of Exhaustion", "A curse erodes the target's muscles", 7, 14),
                SpellTier("Muscle Atrophy", "The target's strength is almost entirely stripped away", 10, 22)
            )
        ),

        // ── DEBUFF DEX: 迟缓 ──
        SpellTemplate(
            id = "slow", type = SpellType.MAGIC,
            target = SpellTarget.DEBUFF_SINGLE, affectedStat = StatType.DEX,
            tiers = listOf(
                SpellTier("Slow",   "The target's actions become sluggish", 2, 3),
                SpellTier("Web",   "Sticky strands bind the target",       4, 7),
                SpellTier("Quagmire",     "The ground turns to mire, halting all movement", 6, 12),
                SpellTier("Time Stop", "The target is locked in frozen time",  9, 20)
            )
        ),

        // ============================================================
        //  奇迹 (MIRACLE) - 信仰系
        // ============================================================

        // ── 单体治疗 ──
        SpellTemplate(
            id = "heal", type = SpellType.MIRACLE,
            target = SpellTarget.HEAL_SINGLE,
            tiers = listOf(
                SpellTier("Heal",   "A warm light restores a little health", 15, 5),
                SpellTier("Greater Heal", "The healing light grows stronger",  35, 11),
                SpellTier("Superior Heal", "Powerful healing magic",           60, 18),
                SpellTier("Baptism of Light", "Holy light pours down, fully restoring the target", 100, 28)
            )
        ),

        // ── 群体治疗 ──
        SpellTemplate(
            id = "mass_heal", type = SpellType.MIRACLE,
            target = SpellTarget.HEAL_AOE,
            tiers = listOf(
                SpellTier("Prayer",     "A soft prayer grants allies faint healing", 8, 6),
                SpellTier("Mass Heal", "Healing light covers all allies",     20, 13),
                SpellTier("Sanctuary Light", "Holy light envelops the entire battlefield", 40, 22),
                SpellTier("Miracle of Rebirth", "Miraculous power revives the whole party at full health", 70, 35)
            )
        ),

        // ── 单体伤害: 圣击 ──
        SpellTemplate(
            id = "smite", type = SpellType.MIRACLE,
            target = SpellTarget.DAMAGE_SINGLE,
            tiers = listOf(
                SpellTier("Smite",     "Strike the enemy with the power of holy light", 13, 5),
                SpellTier("Hammer of Justice", "Summon a hammer of light to strike the target", 30, 10),
                SpellTier("Holy Impact", "Intense holy light pierces the target", 55, 17),
                SpellTier("Angelic Descent", "Summon angelic power to bring down judgment", 82, 27)
            )
        ),

        // ── 群体伤害: 神圣新星 ──
        SpellTemplate(
            id = "holy_nova", type = SpellType.MIRACLE,
            target = SpellTarget.DAMAGE_AOE,
            tiers = listOf(
                SpellTier("Holy Flash",   "Release a small pulse of holy light", 6,  4),
                SpellTier("Holy Burst", "Holy light spreads violently outward", 15, 9),
                SpellTier("Holy Nova", "Dazzling holy light erupts across the battlefield", 30, 16),
                SpellTier("Divine Verdict", "In the name of god, judge all that is impure", 50, 26)
            )
        ),

        // ── BUFF STR: 力量祝福 ──
        SpellTemplate(
            id = "war_blessing", type = SpellType.MIRACLE,
            target = SpellTarget.BUFF_SINGLE, affectedStat = StatType.STR,
            tiers = listOf(
                SpellTier("Strength Blessing", "Grant the target faint holy strength", 2, 4),
                SpellTier("War God's Might", "The war god's power infuses the target", 4, 8),
                SpellTier("Giant's Strength", "The target gains a giant's monstrous might", 7, 14),
                SpellTier("Titan's Might", "In the name of god, bestow titanic power", 10, 22)
            )
        ),

        // ── BUFF FTH: 虔诚 ──
        SpellTemplate(
            id = "devotion", type = SpellType.MIRACLE,
            target = SpellTarget.BUFF_SINGLE, affectedStat = StatType.FTH,
            tiers = listOf(
                SpellTier("Benediction",     "Strengthen the target's faithful heart", 2, 3),
                SpellTier("Light of Devotion", "The light of faith shines upon the target", 4, 7),
                SpellTier("Holy Shield",     "A sacred shield protects the target", 6, 12),
                SpellTier("Divine Armor", "The body is wrapped in inviolable holy light", 9, 20)
            )
        ),

        // ── DEBUFF INT: 沉默 ──
        SpellTemplate(
            id = "silence", type = SpellType.MIRACLE,
            target = SpellTarget.DEBUFF_SINGLE, affectedStat = StatType.INT,
            tiers = listOf(
                SpellTier("Quiet",     "The target's thoughts grow hazy",     2, 4),
                SpellTier("Silence",   "Seal off the target's magic circuits", 4, 8),
                SpellTier("Mind Seal", "The target can no longer think at all", 6, 13),
                SpellTier("Soul Imprisonment", "Completely seal away the target's soul", 9, 20)
            )
        ),

        // ── DEBUFF FTH: 破信 ──
        SpellTemplate(
            id = "faithbreak", type = SpellType.MIRACLE,
            target = SpellTarget.DEBUFF_SINGLE, affectedStat = StatType.FTH,
            tiers = listOf(
                SpellTier("Waver",     "The target's conviction begins to waver", 2, 3),
                SpellTier("Mist of Doubt", "Fog shrouds the target's faithful heart", 4, 7),
                SpellTier("Faith Collapse", "The target's foundation of faith is shaken", 6, 12),
                SpellTier("Godforsaken",     "Divine protection wholly abandons the target", 9, 20)
            )
        )
    ).associateBy { it.id }

    fun get(id: String): SpellTemplate = templates[id] ?: error("Unknown spell: $id")
    fun all(): List<SpellTemplate> = templates.values.toList()
    fun byType(type: SpellType): List<SpellTemplate> = templates.values.filter { it.type == type }

    /**
     * 根据英雄的基础属性、等级、职业计算初始法术列表.
     *
     * - MAGE: 仅按 INT/5 分配 MAGIC 法术
     * - CLERIC: 仅按 FTH/5 分配 MIRACLE 法术
     * - 其他职业: 不分配任何法术 (无法施法)
     *
     * 每个槽位优先升级已有法术 (需等级达标 = 3*tier - 2), 否则学新.
     */
    fun computeSpells(
        baseStats: Map<StatType, Int>,
        heroLevel: Int,
        heroClass: HeroClass
    ): List<HeroSpell> {
        val (slots, pool) = when (heroClass) {
            HeroClass.MAGE -> (baseStats[StatType.INT] ?: 0) / 5 to byType(SpellType.MAGIC)
            HeroClass.CLERIC -> (baseStats[StatType.FTH] ?: 0) / 5 to byType(SpellType.MIRACLE)
            else -> return emptyList()
        }
        if (slots == 0 || pool.isEmpty()) return emptyList()
        val result = mutableListOf<HeroSpell>()
        allocateSlots(result, slots, pool.shuffled(), heroLevel)
        return result
    }

    private fun allocateSlots(
        result: MutableList<HeroSpell>,
        slots: Int,
        pool: List<SpellTemplate>,
        heroLevel: Int
    ) {
        var poolIndex = 0
        repeat(slots) {
            // 尝试升级已有法术中阶级最低的
            val upgradeable = result
                .filter { spell -> pool.any { it.id == spell.templateId } }
                .filter { it.currentTier < 4 && heroLevel >= HeroSpell.requiredLevel(it.currentTier + 1) }
                .minByOrNull { it.currentTier }

            if (upgradeable != null) {
                val idx = result.indexOf(upgradeable)
                result[idx] = upgradeable.copy(currentTier = upgradeable.currentTier + 1)
            } else {
                // 学习新法术 (从池中按顺序取，避免重复)
                while (poolIndex < pool.size) {
                    val template = pool[poolIndex++]
                    if (result.none { it.templateId == template.id }) {
                        result.add(HeroSpell(templateId = template.id, currentTier = 1))
                        break
                    }
                }
            }
        }
    }
}
