package com.example.idledungeon.engine

import com.example.idledungeon.data.ItemRegistry
import com.example.idledungeon.data.MonsterRegistry
import com.example.idledungeon.data.SpellRegistry
import com.example.idledungeon.data.TraitRegistry
import com.example.idledungeon.model.*
import kotlin.random.Random

/**
 * 战斗引擎 - 自动回合制战斗
 *
 * 英雄行动优先级:
 *   1. HP < 40% && 背包有生命药水 → 使用药水 (消耗回合)
 *   2. MP < 40% && 背包有魔力药水 → 使用药水 (消耗回合)
 *   3. 目标怪物 CP >= 英雄 CP * 80% && 有可用法术 → 施放法术
 *   4. 普通攻击
 *
 * 法术限制: MAGE 才能用 MAGIC, CLERIC 才能用 MIRACLE
 */
object CombatEngine {

    /** HP/MP 低于此比例时自动使用药水 */
    private const val POTION_THRESHOLD = 0.4f
    /** 怪物 CP 达到英雄 CP 的此比例时触发法术 */
    private const val SPELL_CP_RATIO = 0.8f

    data class CombatResult(
        val victory: Boolean,
        val updatedHeroes: List<Hero>,
        val log: List<String>,
        val droppedItems: List<String>,
        val remainingCarriedItems: List<String>,
        /** 本场战斗中曾倒下 (HP=0) 的英雄 ID */
        val downedHeroIds: Set<String> = emptySet()
    )

    /**
     * 执行一场战斗
     * @param carriedItems 携带的消耗品ID列表 (会被消耗)
     */
    fun fight(
        heroes: List<Hero>,
        monsterIds: List<String>,
        carriedItems: List<String> = emptyList()
    ): CombatResult {
        val log = mutableListOf<String>()
        val monsters = monsterIds.map { MonsterRegistry.spawn(it) }.toMutableList()
        val heroStates = heroes.map { it.copy() }.toMutableList()
        val bag = carriedItems.toMutableList()

        log.add("=== Battle Start / 战斗开始 ===")
        log.add("  Party / 队伍: ${heroStates.joinToString { "${it.name}(HP:${it.currentHp} MP:${it.currentMp} CP:${it.combatPower})" }}")
        log.add("  Enemies / 敌方: ${monsters.joinToString { "${it.name}(HP:${it.currentHp} CP:${it.combatPower})" }}")

        var round = 0
        while (heroStates.any { it.isAlive } && monsters.any { it.isAlive }) {
            round++
            if (round > 50) {
                log.add("  Battle timeout / 战斗超时")
                break
            }
            log.add("  --- Round $round / 第${round}回合 ---")

            // ── 回合开始: 特质再生/枯萎 ──
            for (i in heroStates.indices) {
                val h = heroStates[i]
                if (!h.isAlive) continue
                val regenSources = traitsTriggering(h) { it.regenPerTurn != 0 }
                val regen = h.regenPerTurn
                if (regen != 0 && regenSources.isNotEmpty()) {
                    val newHp = (h.currentHp + regen).coerceIn(0, h.maxHp)
                    if (newHp != h.currentHp) {
                        heroStates[i] = h.copy(currentHp = newHp)
                        val sign = if (regen > 0) "+" else ""
                        val parts = regenSources.joinToString("/") {
                            val v = it.regenPerTurn; "${it.name}(${if (v > 0) "+" else ""}$v)"
                        }
                        log.add("  ${h.name} 触发 [$parts]: 总 ${sign}${regen} HP (HP:$newHp/${h.maxHp})")
                    }
                }
            }

            // ── 英雄行动 ──
            for (i in heroStates.indices) {
                val hero = heroStates[i]
                if (!hero.isAlive) continue
                if (monsters.none { it.isAlive }) break

                // 优先级 1: 生命药水
                if (hero.currentHp.toFloat() / hero.maxHp < POTION_THRESHOLD) {
                    val potionUsed = tryUseHealthPotion(hero, bag, log)
                    if (potionUsed != null) {
                        heroStates[i] = potionUsed
                        continue // 消耗回合
                    }
                }

                // 优先级 2: 魔力药水
                if (hero.maxMp > 0 && hero.currentMp.toFloat() / hero.maxMp < POTION_THRESHOLD) {
                    val potionUsed = tryUseManaPotion(hero, bag, log)
                    if (potionUsed != null) {
                        heroStates[i] = potionUsed
                        continue
                    }
                }

                val target = monsters.filter { it.isAlive }.random()

                // 优先级 3: 法术 (CP 条件 + 职业限制 + MP 足够)
                val spellAction = tryUseSpell(hero, target, log)
                if (spellAction != null) {
                    val (updatedHero, spellResult) = spellAction
                    // 施法也使用武器，获得武器熟练度
                    heroStates[i] = gainProficiency(updatedHero, EquipSlot.WEAPON, log)
                    applySpellResult(spellResult, heroStates, monsters, log)
                    continue
                }

                // 优先级 4: 普通攻击 (含暴击/二连击/吸血)
                heroStates[i] = gainProficiency(heroAttack(hero, monsters, log), EquipSlot.WEAPON, log)
            }

            // ── 怪物行动 (英雄方含闪避 + 受到伤害倍率) ──
            for (j in monsters.indices) {
                val monster = monsters[j]
                if (!monster.isAlive) continue
                val target = heroStates.filter { it.isAlive }.randomOrNull() ?: break

                val skill = monster.skills.firstOrNull { Random.nextInt(100) < it.triggerChance }
                val rawDmg: Int
                val actionDesc: String
                if (skill != null) {
                    rawDmg = ((monster.attack * skill.powerMultiplier).toInt() - target.defense / 2).coerceAtLeast(1)
                    actionDesc = "${monster.name} [${skill.name}] → ${target.name}"
                } else {
                    rawDmg = (monster.attack - target.defense / 2).coerceAtLeast(1)
                    actionDesc = "${monster.name} → ${target.name}"
                }

                val idx = heroStates.indexOf(target)

                // 闪避检定: 命中则不掉血也不获得护甲熟练度
                if (target.evasionChance > 0 && Random.nextInt(100) < target.evasionChance) {
                    val srcs = traitsTriggering(target) { it.evasionChance > 0 }.joinToString("/") { it.name }
                    log.add("  $actionDesc: ${target.name} 触发 [$srcs] 闪避！")
                    continue
                }

                // 应用受到伤害倍率
                val damage = (rawDmg * target.damageTakenMul).toInt().coerceAtLeast(1)
                val damaged = target.copy(currentHp = (target.currentHp - damage).coerceAtLeast(0))
                heroStates[idx] = gainProficiency(damaged, EquipSlot.ARMOR, log)
                log.add("  $actionDesc: $damage dmg (HP:${heroStates[idx].currentHp}/${target.maxHp})")
            }
        }

        val victory = heroStates.any { it.isAlive }
        log.add(if (victory) "  === Victory! / 胜利！ ===" else "  === All Injured... / 全员重伤... ===")

        // 经验值 & 升级 (仅胜利时)
        if (victory) {
            val totalExp = monsters.sumOf { it.expReward }
            val aliveCount = heroStates.count { it.isAlive }
            val expEach = if (aliveCount > 0) totalExp / aliveCount else 0
            log.add("  EXP +$expEach each (total $totalExp / $aliveCount alive)")

            for (i in heroStates.indices) {
                val hero = heroStates[i]
                if (!hero.isAlive) continue
                var h = hero.copy(exp = hero.exp + expEach)
                // 连续升级检查
                while (h.exp >= h.expToNextLevel) {
                    val newLevel = h.level + 1
                    h = h.copy(
                        level = newLevel,
                        exp = h.exp - h.expToNextLevel,
                        unspentStatPoints = h.unspentStatPoints + 5,
                        unspentSkillPoints = h.unspentSkillPoints + 1
                    )
                    log.add("  ★ ${h.name} Level Up! → Lv.$newLevel ★")
                    // 升级有 10% 概率获得新正面特质
                    val newTrait = TraitRegistry.rollLevelUpTrait(h.traits)
                    if (newTrait != null) {
                        h = TraitRegistry.applyTrait(h, newTrait)
                        val tmpl = TraitRegistry.get(newTrait)
                        log.add("  ✦ ${h.name} 觉醒新特质: ${tmpl.name} (${tmpl.description})")
                    }
                }
                heroStates[i] = h
            }
        }

        // 掉落
        val drops = if (victory) {
            monsters.flatMap { m ->
                m.dropChances.filter { Random.nextInt(100) < it.value }.map { it.key }
            }
        } else emptyList()

        if (drops.isNotEmpty()) {
            log.add("  Loot / 战利品: ${drops.joinToString()}")
        }

        // 记录本场倒下的英雄 (HP <= 0)
        val downedIds = heroStates.filter { it.currentHp <= 0 }.map { it.id }.toSet()

        return CombatResult(
            victory = victory,
            updatedHeroes = heroStates,
            log = log,
            droppedItems = drops,
            remainingCarriedItems = bag,
            downedHeroIds = downedIds
        )
    }

    // ============================
    // 普攻 (含暴击 / 二连击 / 输出倍率 / 吸血)
    // ============================

    /**
     * 英雄一次普攻动作, 可能因 doubleStrikeChance 多打一次.
     * 返回更新后的英雄 (吸血回血会反映在 currentHp 上).
     * 怪物列表会被原地修改.
     */
    private fun heroAttack(hero: Hero, monsters: MutableList<Monster>, log: MutableList<String>): Hero {
        var h = hero
        val doubleStrikeFired = h.doubleStrikeChance > 0 && Random.nextInt(100) < h.doubleStrikeChance
        val attacks = if (doubleStrikeFired) 2 else 1
        if (doubleStrikeFired) {
            val srcs = traitsTriggering(h) { it.doubleStrikeChance > 0 }.joinToString("/") { it.name }
            log.add("  ${h.name} 触发 [$srcs]: 二连击！")
        }
        for (n in 1..attacks) {
            val target = monsters.filter { it.isAlive }.randomOrNull() ?: break
            val baseDmg = (h.attack - target.defense / 2).coerceAtLeast(1)
            var dmg = (baseDmg * h.damageDealtMul).toInt().coerceAtLeast(1)
            val crit = h.critChance > 0 && Random.nextInt(100) < h.critChance
            if (crit) {
                dmg = (dmg * h.critMultiplier).toInt()
                val srcs = traitsTriggering(h) { it.critChance > 0 }.joinToString("/") { it.name }
                log.add("    ${h.name} 触发 [$srcs]: 暴击 ×${h.critMultiplier}!")
            }
            val targetIdx = monsters.indexOf(target)
            val newTargetHp = (target.currentHp - dmg).coerceAtLeast(0)
            monsters[targetIdx] = target.copy(currentHp = newTargetHp)
            val seqTag = if (attacks > 1) " (#$n)" else ""
            log.add("  ${h.name} → ${target.name}: $dmg dmg$seqTag (HP:$newTargetHp/${target.maxHp})")
            // 吸血
            if (h.lifestealRatio > 0f && dmg > 0) {
                val heal = (dmg * h.lifestealRatio).toInt().coerceAtLeast(0)
                if (heal > 0) {
                    val newHp = (h.currentHp + heal).coerceAtMost(h.maxHp)
                    if (newHp > h.currentHp) {
                        val srcs = traitsTriggering(h) { it.lifestealRatio > 0f }.joinToString("/") { it.name }
                        log.add("    ${h.name} 触发 [$srcs]: 吸血 +$heal HP (HP:$newHp/${h.maxHp})")
                        h = h.copy(currentHp = newHp)
                    }
                }
            }
        }
        return h
    }

    /** 找出英雄身上满足谓词的特质 (用于日志注明哪条特质触发了) */
    private fun traitsTriggering(hero: Hero, predicate: (TraitTemplate) -> Boolean): List<TraitTemplate> {
        return hero.traits.mapNotNull { id ->
            val t = TraitRegistry.get(id)
            if (predicate(t)) t else null
        }
    }

    // ============================
    // 熟练度
    // ============================

    /**
     * 装备相应槽位时累计熟练度，等级提升会写入战斗日志。
     * - WEAPON 槽: 普攻 / 施法时调用
     * - ARMOR 槽: 挨打时调用
     * 没装备 / 已满级 / 装备无 weaponType (如饰品) 时返回原 hero。
     */
    private fun gainProficiency(hero: Hero, slot: EquipSlot, log: MutableList<String>, amount: Int = 1): Hero {
        val type = hero.equipment[slot]?.weaponType ?: return hero
        val oldLevel = hero.proficiencyLevel(type)
        if (oldLevel >= Hero.MAX_PROFICIENCY_LEVEL) return hero
        val newXp = (hero.proficiencyXp[type] ?: 0) + amount
        val updated = hero.copy(proficiencyXp = hero.proficiencyXp + (type to newXp))
        val newLevel = updated.proficiencyLevel(type)
        if (newLevel > oldLevel) {
            log.add("  ★ ${hero.name} ${type.displayName()}熟练度↑ Lv.$newLevel (+${(newLevel * Hero.PROFICIENCY_BONUS_PER_LEVEL * 100).toInt()}%)")
        }
        return updated
    }

    // ============================
    // 消耗品
    // ============================

    private fun tryUseHealthPotion(hero: Hero, bag: MutableList<String>, log: MutableList<String>): Hero? {
        // 优先用高级
        val potionId = if ("great_health_potion" in bag) "great_health_potion"
                       else if ("health_potion" in bag) "health_potion"
                       else return null
        bag.remove(potionId)
        val healAmount = if (potionId == "great_health_potion") 150 else 50
        val newHp = (hero.currentHp + healAmount).coerceAtMost(hero.maxHp)
        val item = ItemRegistry.get(potionId)
        log.add("  ${hero.name} uses ${item.name} → HP ${hero.currentHp}→$newHp")
        return hero.copy(currentHp = newHp)
    }

    private fun tryUseManaPotion(hero: Hero, bag: MutableList<String>, log: MutableList<String>): Hero? {
        val potionId = if ("great_mana_potion" in bag) "great_mana_potion"
                       else if ("mana_potion" in bag) "mana_potion"
                       else return null
        bag.remove(potionId)
        val restoreAmount = if (potionId == "great_mana_potion") 100 else 30
        val newMp = (hero.currentMp + restoreAmount).coerceAtMost(hero.maxMp)
        val item = ItemRegistry.get(potionId)
        log.add("  ${hero.name} uses ${item.name} → MP ${hero.currentMp}→$newMp")
        return hero.copy(currentMp = newMp)
    }

    // ============================
    // 法术
    // ============================

    /** 返回 (更新后的Hero, SpellAction) 或 null */
    private fun tryUseSpell(hero: Hero, target: Monster, log: MutableList<String>): Pair<Hero, SpellAction>? {
        // CP 条件: 只有怪物够强时才施法
        if (target.combatPower < hero.combatPower * SPELL_CP_RATIO) return null

        // 职业限制筛选可用法术
        val usableSpells = hero.spells.filter { spell ->
            val template = SpellRegistry.get(spell.templateId)
            when (template.type) {
                SpellType.MAGIC -> hero.heroClass == HeroClass.MAGE
                SpellType.MIRACLE -> hero.heroClass == HeroClass.CLERIC
            }
        }
        if (usableSpells.isEmpty()) return null

        // 选择威力最高且 MP 够的法术
        val bestSpell = usableSpells
            .mapNotNull { spell ->
                val template = SpellRegistry.get(spell.templateId)
                val tier = template.tiers[spell.currentTier - 1]
                if (hero.currentMp >= tier.mpCost) Triple(spell, template, tier) else null
            }
            .maxByOrNull { (_, _, tier) -> tier.power }
            ?: return null

        val (spell, template, tier) = bestSpell
        val newMp = hero.currentMp - tier.mpCost
        val updatedHero = hero.copy(currentMp = newMp)

        log.add("  ${hero.name} casts [${tier.name}] (MP:${hero.currentMp}→$newMp)")

        return updatedHero to SpellAction(template, tier, hero)
    }

    private data class SpellAction(
        val template: SpellTemplate,
        val tier: SpellTier,
        val caster: Hero
    )

    /** 应用法术效果 */
    private fun applySpellResult(
        action: SpellAction,
        heroStates: MutableList<Hero>,
        monsters: MutableList<Monster>,
        log: MutableList<String>
    ) {
        val (template, tier, caster) = action
        val power = tier.power
        val intBonus = caster.effectiveStats[StatType.INT] ?: 0
        val fthBonus = caster.effectiveStats[StatType.FTH] ?: 0
        // 魔法伤害/治疗 = 基础威力 + INT或FTH的一半
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
                val alive = monsters.filter { it.isAlive }
                for (target in alive) {
                    val dmg = (power + statBonus - target.defense / 3).coerceAtLeast(1)
                    val idx = monsters.indexOf(target)
                    monsters[idx] = target.copy(currentHp = (target.currentHp - dmg).coerceAtLeast(0))
                    log.add("    → ${target.name}: $dmg magic dmg (HP:${monsters[idx].currentHp}/${target.maxHp})")
                }
            }
            SpellTarget.HEAL_SINGLE -> {
                val target = heroStates.filter { it.isAlive }.minByOrNull { it.currentHp.toFloat() / it.maxHp } ?: return
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
                val stat = template.affectedStat ?: return
                val target = monsters.filter { it.isAlive }.randomOrNull() ?: return
                val idx = monsters.indexOf(target)
                // 降低怪物对应属性
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
