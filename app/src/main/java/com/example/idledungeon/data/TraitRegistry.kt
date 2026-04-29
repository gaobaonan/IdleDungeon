package com.example.idledungeon.data

import com.example.idledungeon.model.*
import kotlin.random.Random

/**
 * 特质注册表
 *
 * 每条特质独一无二, 单一固定效果. 概率规则:
 *   - 招募时正面: 50%(0) / 45%(1) / 5%(2)
 *   - 招募时负面: 60%(0) / 40%(1)
 *   - 升级后: 10% 概率获得未持有的正面
 *   - HP 归零结算: 必得一条未持有的负面
 *
 * 数值字段说明见 TraitTemplate doc.
 */
object TraitRegistry {

    private val templates: Map<String, TraitTemplate> = listOf(
        // ============================================================
        //  正面 - 输出系
        // ============================================================
        TraitTemplate("trait_berserker", "狂战", "造成伤害 +25%",
            isPositive = true, damageDealtMul = 1.25f),
        TraitTemplate("trait_overpower", "压制", "造成伤害 +15%",
            isPositive = true, damageDealtMul = 1.15f),
        TraitTemplate("trait_crit_master", "暴击专精", "20% 几率暴击 (2.0×)",
            isPositive = true, critChance = 20),
        TraitTemplate("trait_lethal_strike", "致命一击", "10% 暴击 + 暴击伤害提升至 3.0×",
            isPositive = true, critChance = 10, critMultiplier = 3.0f),
        TraitTemplate("trait_wind_blade", "疾风之刃", "15% 几率追加一次普攻",
            isPositive = true, doubleStrikeChance = 15),
        TraitTemplate("trait_blade_dancer", "刃舞者", "25% 二连击 + 造成伤害 +5%",
            isPositive = true, doubleStrikeChance = 25, damageDealtMul = 1.05f),
        TraitTemplate("trait_vampiric", "嗜血", "造成伤害 15% 转化为 HP",
            isPositive = true, lifestealRatio = 0.15f),
        TraitTemplate("trait_executioner", "处刑者", "暴击伤害提升至 2.5×, 造成伤害 +10%",
            isPositive = true, critChance = 5, critMultiplier = 2.5f, damageDealtMul = 1.10f),

        // ============================================================
        //  正面 - 防御系
        // ============================================================
        TraitTemplate("trait_iron_skin", "铁皮", "受到伤害 -20%",
            isPositive = true, damageTakenMul = 0.80f),
        TraitTemplate("trait_stone_will", "石之意志", "受到伤害 -10%",
            isPositive = true, damageTakenMul = 0.90f),
        TraitTemplate("trait_shadow_step", "影遁", "20% 几率闪避攻击",
            isPositive = true, evasionChance = 20),
        TraitTemplate("trait_lightfoot", "轻足", "10% 闪避 + DEX +2",
            isPositive = true, evasionChance = 10, statBonuses = mapOf(StatType.DEX to 2)),
        TraitTemplate("trait_regen", "再生", "每回合回复 10 HP",
            isPositive = true, regenPerTurn = 10),
        TraitTemplate("trait_undying", "不死之躯", "每回合回复 5 HP, 受到伤害 -10%",
            isPositive = true, regenPerTurn = 5, damageTakenMul = 0.90f),
        TraitTemplate("trait_blessed", "受祝福", "受到伤害 -15%, FTH +2",
            isPositive = true, damageTakenMul = 0.85f, statBonuses = mapOf(StatType.FTH to 2)),

        // ============================================================
        //  正面 - 属性系
        // ============================================================
        TraitTemplate("trait_strong",  "强壮", "STR +5",
            isPositive = true, statBonuses = mapOf(StatType.STR to 5)),
        TraitTemplate("trait_agile",   "敏捷", "DEX +5",
            isPositive = true, statBonuses = mapOf(StatType.DEX to 5)),
        TraitTemplate("trait_wise",    "睿智", "INT +5",
            isPositive = true, statBonuses = mapOf(StatType.INT to 5)),
        TraitTemplate("trait_devout",  "虔诚", "FTH +5",
            isPositive = true, statBonuses = mapOf(StatType.FTH to 5)),
        TraitTemplate("trait_hardy",   "健壮", "HP 上限 +50",
            isPositive = true, hpBonus = 50),
        TraitTemplate("trait_deep_well", "深邃", "MP 上限 +30",
            isPositive = true, mpBonus = 30),
        TraitTemplate("trait_valiant", "武勋", "STR +3, DEX +3",
            isPositive = true, statBonuses = mapOf(StatType.STR to 3, StatType.DEX to 3)),
        TraitTemplate("trait_holy_sage", "圣师", "INT +3, FTH +3",
            isPositive = true, statBonuses = mapOf(StatType.INT to 3, StatType.FTH to 3)),
        TraitTemplate("trait_prodigy", "神童", "全属性 +2",
            isPositive = true, statBonuses = StatType.entries.associateWith { 2 }),
        TraitTemplate("trait_titan",   "壮硕", "HP +30, STR +2",
            isPositive = true, hpBonus = 30, statBonuses = mapOf(StatType.STR to 2)),

        // ============================================================
        //  负面 - 输出系
        // ============================================================
        TraitTemplate("trait_weak_strike", "无力", "造成伤害 -20%",
            isPositive = false, damageDealtMul = 0.80f),
        TraitTemplate("trait_clumsy_hands", "笨手", "造成伤害 -15%",
            isPositive = false, damageDealtMul = 0.85f),
        TraitTemplate("trait_bloodlust", "嗜血失控", "造成伤害 +10% 但受到伤害 +20%",
            isPositive = false, damageDealtMul = 1.10f, damageTakenMul = 1.20f),

        // ============================================================
        //  负面 - 防御系
        // ============================================================
        TraitTemplate("trait_glass_skin", "脆皮", "受到伤害 +25%",
            isPositive = false, damageTakenMul = 1.25f),
        TraitTemplate("trait_paper_armor", "纸甲", "受到伤害 +15%",
            isPositive = false, damageTakenMul = 1.15f),
        TraitTemplate("trait_cursed_body", "诅咒之躯", "每回合 -3 HP",
            isPositive = false, regenPerTurn = -3),
        TraitTemplate("trait_dying_curse", "枯萎", "每回合 -5 HP, HP -20",
            isPositive = false, regenPerTurn = -5, hpBonus = -20),
        TraitTemplate("trait_easy_target", "醒目", "受到伤害 +10%, DEX -2",
            isPositive = false, damageTakenMul = 1.10f, statBonuses = mapOf(StatType.DEX to -2)),

        // ============================================================
        //  负面 - 属性系
        // ============================================================
        TraitTemplate("trait_weak_str", "虚弱", "STR -5",
            isPositive = false, statBonuses = mapOf(StatType.STR to -5)),
        TraitTemplate("trait_clumsy",   "笨拙", "DEX -5",
            isPositive = false, statBonuses = mapOf(StatType.DEX to -5)),
        TraitTemplate("trait_dull",     "愚钝", "INT -5",
            isPositive = false, statBonuses = mapOf(StatType.INT to -5)),
        TraitTemplate("trait_skeptic",  "怀疑", "FTH -5",
            isPositive = false, statBonuses = mapOf(StatType.FTH to -5)),
        TraitTemplate("trait_sickly",   "病弱", "HP 上限 -30",
            isPositive = false, hpBonus = -30),
        TraitTemplate("trait_hollow",   "空虚", "MP 上限 -30",
            isPositive = false, mpBonus = -30),
        TraitTemplate("trait_crippled", "残躯", "STR -3, DEX -3",
            isPositive = false, statBonuses = mapOf(StatType.STR to -3, StatType.DEX to -3)),
        TraitTemplate("trait_blind_heart", "心盲", "INT -3, FTH -3",
            isPositive = false, statBonuses = mapOf(StatType.INT to -3, StatType.FTH to -3)),
        TraitTemplate("trait_doomed",   "宿命", "全属性 -2",
            isPositive = false, statBonuses = StatType.entries.associateWith { -2 })
    ).associateBy { it.id }

    fun get(id: String): TraitTemplate = templates[id] ?: error("Unknown trait: $id")
    fun all(): List<TraitTemplate> = templates.values.toList()
    fun byPolarity(positive: Boolean): List<TraitTemplate> =
        templates.values.filter { it.isPositive == positive }

    // ============================================================
    //  滚动 + 应用
    // ============================================================

    /** 招募时滚动初始特质 (返回 ID 列表, 已确保不重复) */
    fun rollRecruitTraits(): List<String> {
        val result = mutableListOf<String>()
        val posCount = when (Random.nextInt(100)) {
            in 0..49 -> 0
            in 50..94 -> 1
            else -> 2
        }
        val posPool = byPolarity(true).shuffled()
        repeat(minOf(posCount, posPool.size)) { result.add(posPool[it].id) }
        if (Random.nextInt(100) < 40) {
            byPolarity(false).randomOrNull()?.let { result.add(it.id) }
        }
        return result
    }

    /** 升级 10% 滚一条未持有的正面特质 */
    fun rollLevelUpTrait(currentTraits: List<String>): String? {
        if (Random.nextInt(100) >= 10) return null
        val owned = currentTraits.toSet()
        return byPolarity(true).filter { it.id !in owned }.randomOrNull()?.id
    }

    /** 倒下后必得一条未持有的负面特质 */
    fun rollDownedTrait(currentTraits: List<String>): String? {
        val owned = currentTraits.toSet()
        return byPolarity(false).filter { it.id !in owned }.randomOrNull()?.id
    }

    /**
     * 给英雄添加一条特质, 同时把 hpBonus / mpBonus 立刻结算到 max/current 上.
     * 如果已经持有该特质则原样返回.
     */
    fun applyTrait(hero: Hero, traitId: String): Hero {
        if (traitId in hero.traits) return hero
        val t = templates[traitId] ?: error("Unknown trait: $traitId")
        val newMaxHp = (hero.maxHp + t.hpBonus).coerceAtLeast(1)
        val newMaxMp = (hero.maxMp + t.mpBonus).coerceAtLeast(0)
        val newCurHp = if (t.hpBonus >= 0) hero.currentHp + t.hpBonus
                       else hero.currentHp.coerceAtMost(newMaxHp)
        val newCurMp = if (t.mpBonus >= 0) hero.currentMp + t.mpBonus
                       else hero.currentMp.coerceAtMost(newMaxMp)
        return hero.copy(
            traits = hero.traits + traitId,
            maxHp = newMaxHp, currentHp = newCurHp,
            maxMp = newMaxMp, currentMp = newCurMp
        )
    }
}
