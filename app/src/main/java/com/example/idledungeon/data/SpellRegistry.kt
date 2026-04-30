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
                SpellTier("火球",     "投掷一颗小火球",           12, 4),
                SpellTier("大火球",   "更大的火球，威力倍增",      28, 9),
                SpellTier("火焰射线", "喷射一道灼热的火焰射线",    50, 16),
                SpellTier("烈焰风暴", "召唤毁灭性的火焰风暴",     80, 26)
            )
        ),

        // ── 单体伤害: 雷 ──
        SpellTemplate(
            id = "lightning", type = SpellType.MAGIC,
            target = SpellTarget.DAMAGE_SINGLE,
            tiers = listOf(
                SpellTier("电火花",   "释放微弱的电流",           11, 4),
                SpellTier("闪电",     "一道精准的闪电劈下",        26, 9),
                SpellTier("雷暴",     "召唤雷云降下闪电",         48, 16),
                SpellTier("天罚",     "苍穹之上降下神雷",         85, 28)
            )
        ),

        // ── 群体伤害: 冰 ──
        SpellTemplate(
            id = "blizzard", type = SpellType.MAGIC,
            target = SpellTarget.DAMAGE_AOE,
            tiers = listOf(
                SpellTier("冰刺",     "射出尖锐的冰刺",          8,  4),
                SpellTier("冰锥",     "多根冰锥同时射出",         18, 8),
                SpellTier("暴风雪",   "在范围内降下暴风雪",       35, 15),
                SpellTier("绝对零度", "冻结一切的极寒领域",       58, 24)
            )
        ),

        // ── 群体伤害: 暗 ──
        SpellTemplate(
            id = "dark_wave", type = SpellType.MAGIC,
            target = SpellTarget.DAMAGE_AOE,
            tiers = listOf(
                SpellTier("暗影箭",   "一支由暗影凝聚的箭矢",     7,  4),
                SpellTier("暗影爆发", "暗影能量向外爆裂",         16, 9),
                SpellTier("暗影领域", "展开吞噬光明的领域",       32, 17),
                SpellTier("虚无吞噬", "召唤虚空吞噬一切",        55, 30)
            )
        ),

        // ── BUFF INT: 灵光 ──
        SpellTemplate(
            id = "mind_sharpen", type = SpellType.MAGIC,
            target = SpellTarget.BUFF_SINGLE, affectedStat = StatType.INT,
            tiers = listOf(
                SpellTier("灵光",       "微弱的智识之光笼罩目标",   2, 4),
                SpellTier("智慧之光",   "思维变得异常清晰",         4, 8),
                SpellTier("大智慧",     "大脑高速运转，洞察一切",   7, 14),
                SpellTier("全知之眼",   "获得接近全知的瞬间感悟",  10, 22)
            )
        ),

        // ── BUFF DEX: 迅捷 ──
        SpellTemplate(
            id = "haste", type = SpellType.MAGIC,
            target = SpellTarget.BUFF_SINGLE, affectedStat = StatType.DEX,
            tiers = listOf(
                SpellTier("迅捷术",   "目标的动作略微加速",        2, 3),
                SpellTier("疾风步",   "身形如风般迅捷",           4, 7),
                SpellTier("闪电反射", "反应速度提升到极限",        6, 12),
                SpellTier("时间加速", "扭曲时间流速，近乎瞬移",    9, 20)
            )
        ),

        // ── DEBUFF STR: 虚弱 ──
        SpellTemplate(
            id = "weaken", type = SpellType.MAGIC,
            target = SpellTarget.DEBUFF_SINGLE, affectedStat = StatType.STR,
            tiers = listOf(
                SpellTier("虚弱术",   "目标感到一阵无力",          2, 4),
                SpellTier("衰弱术",   "力量被大幅削弱",           4, 8),
                SpellTier("力竭诅咒", "诅咒侵蚀目标的肌肉",       7, 14),
                SpellTier("肌肉萎缩", "目标的力量几乎被完全夺走",  10, 22)
            )
        ),

        // ── DEBUFF DEX: 迟缓 ──
        SpellTemplate(
            id = "slow", type = SpellType.MAGIC,
            target = SpellTarget.DEBUFF_SINGLE, affectedStat = StatType.DEX,
            tiers = listOf(
                SpellTier("迟缓术",   "目标的行动变得迟钝",        2, 3),
                SpellTier("蛛网术",   "黏腻的丝线束缚目标",       4, 7),
                SpellTier("泥沼",     "脚下化为泥沼寸步难行",      6, 12),
                SpellTier("时间停滞", "目标被封锁在凝固的时间中",   9, 20)
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
                SpellTier("治愈术",   "温暖的光芒恢复少量生命",    15, 5),
                SpellTier("中级治愈", "治愈之光更加强烈",          35, 11),
                SpellTier("高级治愈", "强大的治愈魔法",           60, 18),
                SpellTier("圣光洗礼", "圣光倾泻，完全恢复目标",   100, 28)
            )
        ),

        // ── 群体治疗 ──
        SpellTemplate(
            id = "mass_heal", type = SpellType.MIRACLE,
            target = SpellTarget.HEAL_AOE,
            tiers = listOf(
                SpellTier("祈祷",     "轻声祈祷，队友获得微弱治愈",  8, 6),
                SpellTier("群体治愈", "治愈之光覆盖全体队友",       20, 13),
                SpellTier("圣域之光", "圣光笼罩整个战场",          40, 22),
                SpellTier("奇迹重生", "奇迹之力使全队满血复活",     70, 35)
            )
        ),

        // ── 单体伤害: 圣击 ──
        SpellTemplate(
            id = "smite", type = SpellType.MIRACLE,
            target = SpellTarget.DAMAGE_SINGLE,
            tiers = listOf(
                SpellTier("圣击",     "以圣光之力打击敌人",        13, 5),
                SpellTier("制裁之锤", "召唤光之锤击打目标",        30, 10),
                SpellTier("圣光冲击", "强烈的圣光贯穿目标",        55, 17),
                SpellTier("天使降临", "召唤天使之力降下审判",       82, 27)
            )
        ),

        // ── 群体伤害: 神圣新星 ──
        SpellTemplate(
            id = "holy_nova", type = SpellType.MIRACLE,
            target = SpellTarget.DAMAGE_AOE,
            tiers = listOf(
                SpellTier("圣光闪",   "释放一小圈圣光脉冲",        6,  4),
                SpellTier("圣光爆发", "圣光向四周猛烈扩散",        15, 9),
                SpellTier("神圣新星", "耀眼的圣光炸裂整个战场",    30, 16),
                SpellTier("神圣裁决", "以神之名义审判所有不洁之物",  50, 26)
            )
        ),

        // ── BUFF STR: 力量祝福 ──
        SpellTemplate(
            id = "war_blessing", type = SpellType.MIRACLE,
            target = SpellTarget.BUFF_SINGLE, affectedStat = StatType.STR,
            tiers = listOf(
                SpellTier("力量祝福", "赐予目标微弱的神圣力量",     2, 4),
                SpellTier("战神之力", "战神的力量灌注目标",         4, 8),
                SpellTier("巨人之力", "目标获得巨人般的怪力",       7, 14),
                SpellTier("泰坦之力", "以神之名赐予泰坦伟力",     10, 22)
            )
        ),

        // ── BUFF FTH: 虔诚 ──
        SpellTemplate(
            id = "devotion", type = SpellType.MIRACLE,
            target = SpellTarget.BUFF_SINGLE, affectedStat = StatType.FTH,
            tiers = listOf(
                SpellTier("祈福",     "增强目标的信仰之心",        2, 3),
                SpellTier("虔诚之光", "信仰之光照耀目标",          4, 7),
                SpellTier("圣盾",     "神圣之盾庇佑目标",         6, 12),
                SpellTier("神圣护甲", "全身被不可侵犯的圣光包裹",  9, 20)
            )
        ),

        // ── DEBUFF INT: 沉默 ──
        SpellTemplate(
            id = "silence", type = SpellType.MIRACLE,
            target = SpellTarget.DEBUFF_SINGLE, affectedStat = StatType.INT,
            tiers = listOf(
                SpellTier("静默",     "目标的思维变得模糊",        2, 4),
                SpellTier("沉默术",   "封锁目标的魔力回路",       4, 8),
                SpellTier("心智封印", "目标无法进行任何思考",      6, 13),
                SpellTier("灵魂禁锢", "将目标的灵魂彻底封印",     9, 20)
            )
        ),

        // ── DEBUFF FTH: 破信 ──
        SpellTemplate(
            id = "faithbreak", type = SpellType.MIRACLE,
            target = SpellTarget.DEBUFF_SINGLE, affectedStat = StatType.FTH,
            tiers = listOf(
                SpellTier("动摇",     "目标信念产生动摇",          2, 3),
                SpellTier("疑惑之雾", "迷雾笼罩目标的信仰之心",    4, 7),
                SpellTier("信仰崩塌", "目标的信仰根基被动摇",      6, 12),
                SpellTier("弃神",     "神之庇佑彻底离开目标",      9, 20)
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
