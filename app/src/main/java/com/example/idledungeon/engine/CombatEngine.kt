package com.example.idledungeon.engine

import com.example.idledungeon.data.MonsterRegistry
import com.example.idledungeon.model.*
import kotlin.random.Random

/**
 * 战斗引擎 - 自动回合制战斗
 * 输入: 队伍 + 怪物列表
 * 输出: 战斗结果 (战报日志 + 最终状态)
 */
object CombatEngine {

    data class CombatResult(
        val victory: Boolean,
        val updatedHeroes: List<Hero>,
        val log: List<String>,
        val droppedItems: List<String>   // 掉落的物品ID
    )

    /**
     * 执行一场战斗
     */
    fun fight(heroes: List<Hero>, monsterIds: List<String>): CombatResult {
        val log = mutableListOf<String>()
        val monsters = monsterIds.map { MonsterRegistry.spawn(it) }.toMutableList()
        val heroStates = heroes.map { it.copy() }.toMutableList()

        log.add("=== Battle Start / 战斗开始 ===")
        log.add("Party: ${heroStates.joinToString { "${it.name} (HP:${it.currentHp})" }}")
        log.add("Enemies: ${monsters.joinToString { "${it.name} (HP:${it.currentHp})" }}")

        var round = 0
        while (heroStates.any { it.isAlive } && monsters.any { it.isAlive }) {
            round++
            log.add("--- Round $round ---")

            // 英雄行动
            for (i in heroStates.indices) {
                val hero = heroStates[i]
                if (!hero.isAlive) continue
                val target = monsters.filter { it.isAlive }.randomOrNull() ?: break

                val damage = (hero.attack - target.defense / 2).coerceAtLeast(1)
                val newHp = (target.currentHp - damage).coerceAtLeast(0)
                monsters[monsters.indexOf(target)] = target.copy(currentHp = newHp)

                log.add("${hero.name} → ${target.name}: $damage dmg (HP: $newHp/${target.maxHp})")
            }

            // 怪物行动
            for (monster in monsters) {
                if (!monster.isAlive) continue
                val target = heroStates.filter { it.isAlive }.randomOrNull() ?: break

                // 检查是否触发技能
                val skill = monster.skills.firstOrNull { Random.nextInt(100) < it.triggerChance }
                val damage: Int
                val actionDesc: String

                if (skill != null) {
                    damage = ((monster.attack * skill.powerMultiplier).toInt() - target.defense / 2).coerceAtLeast(1)
                    actionDesc = "${monster.name} uses ${skill.name} → ${target.name}"
                } else {
                    damage = (monster.attack - target.defense / 2).coerceAtLeast(1)
                    actionDesc = "${monster.name} → ${target.name}"
                }

                val idx = heroStates.indexOf(target)
                heroStates[idx] = target.copy(currentHp = (target.currentHp - damage).coerceAtLeast(0))
                log.add("$actionDesc: $damage dmg (HP: ${heroStates[idx].currentHp}/${target.maxHp})")
            }
        }

        val victory = heroStates.any { it.isAlive }
        log.add(if (victory) "=== Victory! / 胜利！ ===" else "=== Defeat... / 败北... ===")

        // 计算掉落
        val drops = if (victory) {
            monsters.flatMap { m ->
                m.dropChances.filter { Random.nextInt(100) < it.value }.map { it.key }
            }
        } else emptyList()

        if (drops.isNotEmpty()) {
            log.add("Loot / 战利品: ${drops.joinToString()}")
        }

        return CombatResult(
            victory = victory,
            updatedHeroes = heroStates,
            log = log,
            droppedItems = drops
        )
    }
}