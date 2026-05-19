package com.example.idledungeon.engine

import com.example.idledungeon.data.ItemRegistry
import com.example.idledungeon.data.MonsterRegistry
import com.example.idledungeon.data.SpellRegistry
import com.example.idledungeon.model.*
import com.example.idledungeon.util.L
import kotlin.random.Random

/**
 * 自动回合制战斗.
 * 一个 object 单例, 没有状态, 每场战斗各自独立: 输入英雄队 + 怪物 ID 列表, 输出 [CombatResult].
 *
 * 调用方一般是 ExplorationEngine — 踩到 ENCOUNTER / GUARDED_LOOT / BOSS 时调一次 [fight].
 *
 * 单回合内的执行顺序:
 *   1. 英雄方按 partySlots 顺序行动 (无先攻系统)
 *   2. 怪物方按列表顺序行动
 *   3. 任意一方全死或回合数超 100 就停
 *
 * 每个英雄行动按优先级挑一件事做 (4 选 1):
 *   ① HP < 40% 且背包有血瓶  → 喝血瓶 (用掉本回合, 不再攻击)
 *   ② MP < 40% 且背包有蓝瓶  → 喝蓝瓶
 *   ③ 怪物 CP ≥ 英雄 CP × 80%, 有职业匹配 + MP 够的法术  → 施法
 *   ④ 普攻
 *
 * 法术职业限制: MAGE 只用 MAGIC, CLERIC 只用 MIRACLE, 战士/游侠只能普攻.
 */
object CombatEngine {

    /** HP/MP 比例低于此值时英雄会优先喝药水 */
    private const val POTION_THRESHOLD = 0.4f

    /** 怪物 CP / 英雄 CP 大于等于此值时才值得施法 (省 MP) */
    private const val SPELL_CP_RATIO = 0.8f

    /** 怪物选攻击目标时的站位权重: 前排吸引火力, 后排相对安全. 比值 3:1 → 前排约 3 倍概率挨打. */
    private const val FRONT_AGGRO_WEIGHT = 3
    private const val BACK_AGGRO_WEIGHT = 1

    /**
     * 战斗结果. 调用方拿到后:
     *   - updatedHeroes 写回 ViewModel, 反映 HP/MP/EXP/等级变化
     *   - droppedItems 加入背包
     *   - remainingCarriedItems 替换原 carriedItems (战斗里喝过的药水已扣)
     *   - killedMonsterIds 累计到图鉴击杀计数
     */
    data class CombatResult(
        val victory: Boolean,                       // 是否打赢
        val updatedHeroes: List<Hero>,              // 战后英雄 (HP/MP/EXP/等级已更新)
        val log: List<String>,                      // 逐条战斗日志 (UI 显示)
        val droppedItems: List<String>,             // 掉落物品 ID
        val remainingCarriedItems: List<String>,    // 剩余消耗品 (战斗中喝掉的已扣)
        val killedMonsterIds: List<String> = emptyList()    // 被击杀的怪物 ID (图鉴累计用)
    )

    /**
     * 跑一场战斗, 算到分胜负为止.
     *
     * @param heroes        出战英雄队 (1-4 人, 不可变, 函数内部用 copy() 工作副本)
     * @param monsterIds    怪物模板 ID 列表; ID 失效会被静默跳过 + 打 warning log
     * @param carriedItems  携带的消耗品 ID 列表; 战斗中喝掉的会从中移除
     * @param partyPositions 英雄ID → 前/后排站位, 决定挨打权重 (前排吸火力).
     *                       留空 = 全按前排 = 等概率, 与无站位系统时行为完全一致 (单测因此不受影响).
     */
    fun fight(
        heroes: List<Hero>,
        monsterIds: List<String>,
        carriedItems: List<String> = emptyList(),
        partyPositions: Map<String, Position> = emptyMap()
    ): CombatResult {
        L.i("Combat", "fight start: heroes=${heroes.size}, monsters=${monsterIds.size} (${monsterIds.joinToString()})")

        // ─── 边界保护 ─────────────────────────────────
        // 理论上调用方应该挡住空队伍 / 空怪物组, 但万一有 bug 这里也不能崩
        if (heroes.isEmpty()) {
            L.w("Combat", "fight called with empty party — auto-lose")
            return CombatResult(
                victory = false, updatedHeroes = emptyList(),
                log = listOf("=== Battle aborted: empty party ==="),
                droppedItems = emptyList(), remainingCarriedItems = carriedItems
            )
        }
        // 把怪物 ID 解成 Monster 实例; 失效 ID (拼错 / 注册表删过) 跳过即可
        val monsters = monsterIds.mapNotNull {
            try { MonsterRegistry.spawn(it) } catch (e: Exception) {
                L.w("Combat", "MonsterRegistry has no id='$it', skipping", e); null
            }
        }.toMutableList()
        if (monsters.isEmpty()) {
            L.w("Combat", "all monster ids invalid — auto-win")
            return CombatResult(
                victory = true, updatedHeroes = heroes,
                log = listOf("=== No valid monsters ==="),
                droppedItems = emptyList(), remainingCarriedItems = carriedItems
            )
        }

        // ─── 工作副本 ─────────────────────────────────
        // heroes 是不可变 List; 战斗里要改 HP/MP, 必须先 copy 一份当工作区
        // bag 同理 — 喝药水会从中删, 不能动调用方传进来的列表
        val log = mutableListOf<String>()       // 战斗日志, 最终随 CombatResult 返回
        val heroStates = heroes.map { it.copy() }.toMutableList()
        val bag = carriedItems.toMutableList()
        // 谁被眩晕几回合的临时表 (heroId → 剩余回合数). 怪物 STUN 命中 +1, 行动开始 -1.
        val stunnedTurns = mutableMapOf<String, Int>()

        // ─── 开战日志: 双方初始状态 ───────────────────
        log.add("=== Battle Start ===")
        log.add("  Party:")
        heroStates.forEach { h ->
            log.add("    ${h.name}  HP:${h.currentHp}/${h.maxHp}  MP:${h.currentMp}/${h.maxMp}  CP:${h.combatPower}")
        }
        log.add("  Enemies:")
        monsters.forEach { m ->
            log.add("    ${m.name}  HP:${m.currentHp}/${m.maxHp}  CP:${m.combatPower}")
        }

        // ═══ 主战斗循环 ═══════════════════════════════
        // 双方都至少有一个活的就继续打. 100 回合保险绳防死锁 (双方互戳但都打不动).
        var round = 0
        while (heroStates.any { it.isAlive } && monsters.any { it.isAlive }) {
            round++
            if (round > 100) {
                log.add("  Battle timeout")
                break
            }
            log.add("  --- Round $round ---")

            // ─── 英雄方行动 ───────────────────────────
            for (i in heroStates.indices) {
                val hero = heroStates[i]
                if (!hero.isAlive) continue
                if (monsters.none { it.isAlive }) break   // 打完GG

                // 眩晕中: 跳过本回合, 计数 -1
                val stunLeft = stunnedTurns[hero.id] ?: 0
                if (stunLeft > 0) {
                    log.add("  ${hero.name}: stunned, skips this turn (${stunLeft - 1} turn(s) left)")
                    stunnedTurns[hero.id] = stunLeft - 1
                    continue
                }

                // 优先级 ①: 血量危险 → 喝血瓶
                if (hero.currentHp.toFloat() / hero.maxHp < POTION_THRESHOLD) {
                    val potionUsed = tryUseHealthPotion(hero, bag, log)
                    if (potionUsed != null) {
                        heroStates[i] = potionUsed
                        continue   // 喝药占一回合
                    }
                }

                // 优先级 ②: 蓝量危险 → 喝蓝瓶 (没 MP 上限的英雄不需要)
                if (hero.maxMp > 0 && hero.currentMp.toFloat() / hero.maxMp < POTION_THRESHOLD) {
                    val potionUsed = tryUseManaPotion(hero, bag, log)
                    if (potionUsed != null) {
                        heroStates[i] = potionUsed
                        continue
                    }
                }

                // 随机选一个活着的怪物作为 CP 比较的参照 (并非真的攻击目标 — 实际目标在攻击/施法函数里再选)
                val target = monsters.filter { it.isAlive }.random()

                // 优先级 ③: 强敌 + 有可用法术 → 施法 (CP / 职业 / MP 三重判断)
                val spellAction = tryUseSpell(hero, target, log)
                if (spellAction != null) {
                    val (updatedHero, spellResult) = spellAction
                    // 施法也算"用了武器", 武器熟练度照涨
                    heroStates[i] = gainProficiency(updatedHero, EquipSlot.WEAPON, log)
                    applySpellResult(spellResult, heroStates, monsters, log)
                    continue
                }

                // 优先级 ④: 普攻
                heroStates[i] = gainProficiency(heroAttack(hero, monsters, log), EquipSlot.WEAPON, log)
            }

            // ─── 怪物方行动 ───────────────────────────
            for (j in monsters.indices) {
                val monster = monsters[j]
                if (!monster.isAlive) continue
                val alive = heroStates.filter { it.isAlive }
                if (alive.isEmpty()) break                                  // 己方GG了
                val target = pickTarget(alive, partyPositions)              // 按前/后排加权随机

                // 怪物有技能就 roll 一次, 命中就用技能伤害公式; 没命中走普攻
                val skill = monster.skills.firstOrNull { Random.nextInt(100) < it.triggerChance }       //好空安全
                val dmg: Int
                val actionText: String
                if (skill != null) {
                    dmg = ((monster.attack * skill.powerMultiplier).toInt() - target.defense / 2).coerceAtLeast(1)
                    actionText = "${monster.name} [${skill.name}] → ${target.name}"
                } else {
                    dmg = (monster.attack - target.defense / 2).coerceAtLeast(1)
                    actionText = "${monster.name} → ${target.name}"
                }

                val idx = heroStates.indexOf(target)
                val damaged = target.copy(currentHp = (target.currentHp - dmg).coerceAtLeast(0))
                // 挨打涨护甲熟练度
                heroStates[idx] = gainProficiency(damaged, EquipSlot.ARMOR, log)
                log.add("  $actionText: $dmg damage (HP:${heroStates[idx].currentHp}/${target.maxHp})")

                // 命中带 STUN 效果 → 给受击英雄加一层眩晕 (多次命中可叠加)
                if (skill != null && skill.effectType == SkillEffect.STUN) {
                    val curStun = stunnedTurns[target.id] ?: 0
                    stunnedTurns[target.id] = curStun + 1
                    log.add("    ${target.name} is stunned by ${skill.name}! Skips next turn")
                }
            }
        }

        // ═══ 结算 ═════════════════════════════════════
        val victory = heroStates.any { it.isAlive }     // 还有人活着即判胜
        log.add(if (victory) "  === Victory! ===" else "  === All Injured... ===")

        // 经验 & 升级
        if (victory) {
            val totalExp = monsters.sumOf { it.expReward }      // 所有怪经验之和
            val aliveCount = heroStates.count { it.isAlive }     // 经验按活人均分, 这是分母
            val expEach = if (aliveCount > 0) totalExp / aliveCount else 0
            log.add("  EXP +$expEach each (total $totalExp / $aliveCount alive)")

            for (i in heroStates.indices) {         //升级
                val hero = heroStates[i]
                if (!hero.isAlive) continue
                var h = hero.copy(exp = hero.exp + expEach)
                // 一次战斗经验巨多可能跨多级！！
                while (h.exp >= h.expToNextLevel) {
                    val newLevel = h.level + 1
                    val statGain: Int       //属性点和技能点
                    val skillGain: Int
                    when (h.heroClass) {
                        HeroClass.WARRIOR, HeroClass.RANGER -> { statGain = 5; skillGain = 0 }
                        HeroClass.MAGE, HeroClass.CLERIC -> { statGain = 3; skillGain = 1 }
                    }
                    h = h.copy(
                        level = newLevel,
                        exp = h.exp - h.expToNextLevel,
                        unspentStatPoints = h.unspentStatPoints + statGain,
                        unspentSkillPoints = h.unspentSkillPoints + skillGain
                    )
                    log.add("  ★ ${h.name} Level Up! → Lv.$newLevel ★")
                }
                heroStates[i] = h
            }
        }

        val drops = if (victory) {      //掉落
            monsters.flatMap { m ->             //因为每个怪可能不止掉一个
                m.dropChances.filter { Random.nextInt(100) < it.value }.map { it.key }
            }
        } else emptyList()

        if (drops.isNotEmpty()) {
            log.add("  Loot: ${itemNames(drops)}")
        }

        val killedMonsters = monsters.filter { it.currentHp <= 0 }.map { it.id }        //击杀怪物

        L.i("Combat", "fight end: victory=$victory, rounds=$round, " +
            "killed=${killedMonsters.size}, drops=${drops.size}")

        return CombatResult(
            victory = victory,
            updatedHeroes = heroStates,
            log = log,
            droppedItems = drops,
            remainingCarriedItems = bag,
            killedMonsterIds = killedMonsters
        )
    }

    // ═══ 目标选取 ═══════════════════════════════════════

    /**
     * 怪物从存活英雄里挑一个攻击目标: 前排权重 [FRONT_AGGRO_WEIGHT], 后排 [BACK_AGGRO_WEIGHT].
     * 加权随机而非"必打前排": 前排吸引大部分火力, 但后排并非绝对安全; 前排全灭后,
     * 候选里只剩后排, 自然就轮到后排挨打 (无需特判). positions 查不到的英雄按前排处理.
     */
    private fun pickTarget(alive: List<Hero>, positions: Map<String, Position>): Hero {
        val weights = alive.map {
            if (positions[it.id] == Position.BACK) BACK_AGGRO_WEIGHT else FRONT_AGGRO_WEIGHT
        }
        var roll = Random.nextInt(weights.sum())                            // [0, 总权重)
        for (i in alive.indices) {
            roll -= weights[i]
            if (roll < 0) return alive[i]                                   // 落在该英雄的权重区间内
        }
        return alive.last()                                                 // 边界兜底, 正常到不了这
    }

    // ═══ 普攻 ═══════════════════════════════════════════

    /**
     * 英雄一次普攻: 随机选一只活着的怪物, 算伤害, 写日志.
     * 怪物列表是 MutableList, 这里原地改 HP. 返回 hero 是为了和 [tryUseSpell] 保持签名一致.
     */
    private fun heroAttack(hero: Hero, monsters: MutableList<Monster>, log: MutableList<String>): Hero {
        val target = monsters.filter { it.isAlive }.randomOrNull() ?: return hero   // 没活怪可打 → 空手而归
        val dmg = (hero.attack - target.defense / 2).coerceAtLeast(1)   // 至少打 1 点, 不会出现 0 伤害
        val targetIdx = monsters.indexOf(target)
        val newTargetHp = (target.currentHp - dmg).coerceAtLeast(0)
        monsters[targetIdx] = target.copy(currentHp = newTargetHp)
        log.add("  ${hero.name} → ${target.name}: $dmg dmg (HP:$newTargetHp/${target.maxHp})")
        return hero
    }

    // ═══ 物品名渲染 ═════════════════════════════════════
    /** 把物品 ID 列表渲染成展示名 (查不到就退回 ID), 避免日志里出现下划线裸 ID. */
    private fun itemNames(ids: List<String>): String =
        ids.joinToString { id -> try { ItemRegistry.get(id).name } catch (_: Exception) { id } }

    // ═══ 熟练度 ═════════════════════════════════════════

    /**
     * 用一次装备就涨 1 点熟练度 XP. 跨级时写日志. 已满级 / 装备无 weaponType (饰品) 时跳过.
     *
     * 调用时机:
     *   - 普攻 / 施法 → WEAPON 槽
     *   - 挨打       → ARMOR 槽
     */
    private fun gainProficiency(hero: Hero, slot: EquipSlot, log: MutableList<String>, amount: Int = 1): Hero {
        val type = hero.equipment[slot]?.weaponType ?: return hero
        val oldLevel = hero.proficiencyLevel(type)
        if (oldLevel >= Hero.MAX_PROFICIENCY_LEVEL) return hero
        val newXp = (hero.proficiencyXp[type] ?: 0) + amount

        val updated = hero.copy(proficiencyXp = hero.proficiencyXp + (type to newXp))   // 写回累计 XP (等级由 proficiencyLevel() 派生, 不直接存)
        val newLevel = updated.proficiencyLevel(type)
        if (newLevel > oldLevel) {
            log.add("  ★ ${hero.name} ${type.displayName()} proficiency level up! Lv.$newLevel " +
                "(proficiency bonus +${(newLevel * Hero.PROFICIENCY_BONUS_PER_LEVEL * 100).toInt()}%)")
        }
        return updated
    }

    // ═══ 战斗中喝药 (应急) ══════════════════════════════
    // 注: 战斗中是"火烧眉毛"逻辑 — 直接喝最大瓶子. 战后恢复在 ExplorationEngine,
    // 那边会挑刚好够补满的最小瓶, 省着喝.

    /** 选背包里恢复量最大的血瓶喝下. 没血瓶返回 null. */
    private fun tryUseHealthPotion(hero: Hero, bag: MutableList<String>, log: MutableList<String>): Hero? {
        val potion = bag.distinct().mapNotNull {        //找最大回复量的药水！
            try { ItemRegistry.get(it).takeIf { i -> i.restoreHp > 0 } } catch (_: Exception) { null }
        }.maxByOrNull { it.restoreHp } ?: return null       //最大

        bag.remove(potion.id)
        val newHp = (hero.currentHp + potion.restoreHp).coerceAtMost(hero.maxHp)
        log.add("  ${hero.name} used ${potion.name}: HP ${hero.currentHp}→$newHp")
        return hero.copy(currentHp = newHp)
    }

    /** 选背包里恢复量最大的蓝瓶喝下. 没蓝瓶返回 null. */
    private fun tryUseManaPotion(hero: Hero, bag: MutableList<String>, log: MutableList<String>): Hero? {
        val potion = bag.distinct().mapNotNull {        //内容同上
            try { ItemRegistry.get(it).takeIf { i -> i.restoreMp > 0 } } catch (_: Exception) { null }
        }.maxByOrNull { it.restoreMp } ?: return null
        bag.remove(potion.id)
        val newMp = (hero.currentMp + potion.restoreMp).coerceAtMost(hero.maxMp)
        log.add("  ${hero.name} used ${potion.name}: MP ${hero.currentMp}→$newMp")
        return hero.copy(currentMp = newMp)
    }

    // ═══ 法术 ═══════════════════════════════════════════

    /**
     * 决定本回合要不要施法, 选哪个法术. 三个条件全过才施:
     *   1. 怪物够强 (CP ≥ 英雄 CP × 80%) — 不强不浪费蓝
     *   2. 至少有一个法术匹配职业 (MAGE/MAGIC, CLERIC/MIRACLE)
     *   3. 该法术当前 tier 的 mpCost ≤ 英雄 currentMp
     *
     * 返回值是 (扣完蓝的英雄, SpellAction). 调用方需要先把扣蓝后的英雄写回 list,
     * 再调 [applySpellResult] 把法术效果落到目标身上 — 因为法术效果可能波及多个对象,
     * 会原地改 heroStates 和 monsters 列表.
     */
    private fun tryUseSpell(hero: Hero, target: Monster, log: MutableList<String>): Pair<Hero, SpellAction>? {
        if (target.combatPower < hero.combatPower * SPELL_CP_RATIO) return null

        // 按职业筛掉学了但用不了的法术
        val usableSpells = hero.spells.filter { spell ->
            val template = SpellRegistry.get(spell.templateId)
            when (template.type) {
                SpellType.MAGIC -> hero.heroClass == HeroClass.MAGE
                SpellType.MIRACLE -> hero.heroClass == HeroClass.CLERIC
            }
        }
        if (usableSpells.isEmpty()) return null

        // 在 MP 够的法术里挑威力最高的; 一个都不够就 return null
        val bestSpell = usableSpells
            .mapNotNull { spell ->
                val template = SpellRegistry.get(spell.templateId)
                val tier = template.tiers[spell.currentTier - 1]            //序号！-1！
                if (hero.currentMp >= tier.mpCost) template to tier else null
            }
            .maxByOrNull { (_, tier) -> tier.power }
            ?: return null

        val (template, tier) = bestSpell
        val newMp = hero.currentMp - tier.mpCost        // 施法扣蓝
        val updatedHero = hero.copy(currentMp = newMp)
        log.add("  ${hero.name} casts [${tier.name}] (MP:${hero.currentMp}→$newMp)")
        return updatedHero to SpellAction(template, tier, hero)
    }

    /** 一次施法的"指令包". caster 是扣蓝前的英雄 (用来读 INT/FTH 算加成). */
    private data class SpellAction(
        val template: SpellTemplate,
        val tier: SpellTier,
        val caster: Hero
    )

    /**
     * 把法术效果落到目标身上. 6 种 SpellTarget 各有公式:
     *
     *   伤害类:  power + INT/2 (魔法) 或 FTH/2 (奇迹) - 怪物防御/3, 至少 1
     *   治疗类:  power + INT/2 / FTH/2 (不减目标防御)
     *   BUFF:    给随机英雄某属性 +power (注: 直接改 baseStats, 战斗后不会消除)
     *   DEBUFF:  按属性类型降怪物的攻击/防御
     *
     * 单体伤害随机选一个, 单体治疗优先血量百分比最低的, BUFF/DEBUFF 随机.
     */
    private fun applySpellResult(
        action: SpellAction,
        heroStates: MutableList<Hero>,
        monsters: MutableList<Monster>,
        log: MutableList<String>
    ) {
        val (template, tier, caster) = action   // 拆开施法指令包
        val power = tier.power
        val intBonus = caster.effectiveStats[StatType.INT] ?: 0
        val fthBonus = caster.effectiveStats[StatType.FTH] ?: 0
        val statBonus = if (template.type == SpellType.MAGIC) intBonus / 2 else fthBonus / 2

        when (template.target) {
            SpellTarget.DAMAGE_SINGLE -> {
                val target = monsters.filter { it.isAlive }.randomOrNull() ?: return
                val dmg = (power + statBonus - target.defense / 3).coerceAtLeast(1)
                val idx = monsters.indexOf(target)
                monsters[idx] = target.copy(currentHp = (target.currentHp - dmg).coerceAtLeast(0))
                log.add("    → ${target.name}: $dmg magic dmg (HP:${monsters[idx].currentHp}/${target.maxHp})")
            }
            SpellTarget.DAMAGE_AOE -> {
                // 注: 在循环开始前 snapshot alive, 因为打死一个后下一个 indexOf 还能用.
                val alive = monsters.filter { it.isAlive }
                for (target in alive) {
                    val dmg = (power + statBonus - target.defense / 3).coerceAtLeast(1)
                    val idx = monsters.indexOf(target)
                    monsters[idx] = target.copy(currentHp = (target.currentHp - dmg).coerceAtLeast(0))
                    log.add("    → ${target.name}: $dmg magic dmg (HP:${monsters[idx].currentHp}/${target.maxHp})")
                }
            }
            SpellTarget.HEAL_SINGLE -> {
                // 找血量百分比最低的活英雄 (最危险的优先治)
                val target = heroStates.filter { it.isAlive }
                    .minByOrNull { it.currentHp.toFloat() / it.maxHp } ?: return
                val heal = power + statBonus
                val idx = heroStates.indexOf(target)
                val newHp = (target.currentHp + heal).coerceAtMost(target.maxHp)
                heroStates[idx] = target.copy(currentHp = newHp)
                log.add("    → ${target.name}: +$heal HP (HP:$newHp/${target.maxHp})")
            }
            SpellTarget.HEAL_AOE -> {
                val alive = heroStates.filter { it.isAlive }
                for (target in alive) {
                    val heal = power + statBonus
                    val idx = heroStates.indexOf(target)
                    val newHp = (target.currentHp + heal).coerceAtMost(target.maxHp)
                    heroStates[idx] = target.copy(currentHp = newHp)
                    log.add("    → ${target.name}: +$heal HP (HP:$newHp/${target.maxHp})")
                }
            }
            SpellTarget.BUFF_SINGLE -> {
                val stat = template.affectedStat ?: return
                val target = heroStates.filter { it.isAlive }.randomOrNull() ?: return
                val idx = heroStates.indexOf(target)
                val newStats = target.baseStats.toMutableMap()
                newStats[stat] = (newStats[stat] ?: 0) + power
                heroStates[idx] = target.copy(baseStats = newStats)
                log.add("    → ${target.name}: ${stat.name} +$power")
            }
            SpellTarget.DEBUFF_SINGLE -> {
                // 按 affectedStat 决定降怪物的 ATK 还是 DEF (或者两者都降)
                //   STR/DEX → 降 ATK (近战相关)
                //   DEX/FTH → 降 DEF
                // (DEX 同时影响两边是设计决定, 让游侠的 debuff 更全能)
                val stat = template.affectedStat ?: return
                val target = monsters.filter { it.isAlive }.randomOrNull() ?: return
                val idx = monsters.indexOf(target)
                val newAtk = if (stat == StatType.STR || stat == StatType.DEX)
                    (target.attack - power).coerceAtLeast(0) else target.attack
                val newDef = if (stat == StatType.DEX || stat == StatType.FTH)
                    (target.defense - power).coerceAtLeast(0) else target.defense
                monsters[idx] = target.copy(attack = newAtk, defense = newDef)
                log.add("    → ${target.name}: ${stat.name} -$power (ATK:$newAtk DEF:$newDef)")
            }
        }
    }
}
