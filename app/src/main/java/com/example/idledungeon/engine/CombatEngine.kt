package com.example.idledungeon.engine

import com.example.idledungeon.data.ItemRegistry
import com.example.idledungeon.data.MonsterRegistry
import com.example.idledungeon.data.SpellRegistry
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
        val remainingCarriedItems: List<String>
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
                    heroStates[i] = updatedHero
                    applySpellResult(spellResult, heroStates, monsters, log)
                    continue
                }

                // 优先级 4: 普通攻击
                val damage = (hero.attack - target.defense / 2).coerceAtLeast(1)
                val newHp = (target.currentHp - damage).coerceAtLeast(0)
                monsters[monsters.indexOf(target)] = target.copy(currentHp = newHp)
                log.add("  ${hero.name} → ${target.name}: $damage dmg (HP:$newHp/${target.maxHp})")
            }

            // ── 怪物行动 ──
            for (j in monsters.indices) {
                val monster = monsters[j]
                if (!monster.isAlive) continue
                val target = heroStates.filter { it.isAlive }.randomOrNull() ?: break

                val skill = monster.skills.firstOrNull { Random.nextInt(100) < it.triggerChance }
                val damage: Int
                val actionDesc: String

                if (skill != null) {
                    damage = ((monster.attack * skill.powerMultiplier).toInt() - target.defense / 2).coerceAtLeast(1)
                    actionDesc = "${monster.name} [${skill.name}] → ${target.name}"
                } else {
                    damage = (monster.attack - target.defense / 2).coerceAtLeast(1)
                    actionDesc = "${monster.name} → ${target.name}"
                }

                val idx = heroStates.indexOf(target)
                heroStates[idx] = target.copy(currentHp = (target.currentHp - damage).coerceAtLeast(0))
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

        return CombatResult(
            victory = victory,
            updatedHeroes = heroStates,
            log = log,
            droppedItems = drops,
            remainingCarriedItems = bag
        )
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
