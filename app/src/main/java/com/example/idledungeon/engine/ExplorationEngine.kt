package com.example.idledungeon.engine

import com.example.idledungeon.data.ItemRegistry
import com.example.idledungeon.data.SpellRegistry
import com.example.idledungeon.data.TraitRegistry
import com.example.idledungeon.model.*
import com.example.idledungeon.viewmodel.GameUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 探索引擎 - 协程驱动的放置式探索
 *
 * 主战术控制撤退条件，副战术控制 BFS 目标优先级。
 * 已探索房间持久化在地图 grid (label "v" 前缀)，跨冒险生效。
 * HP=0 表示重伤 (injured)，不是阵亡。
 */
class ExplorationEngine {

    companion object {
        private const val BASE_INTERVAL_MS = 600L
        private const val SUPPLY_NORMAL = 1
        private const val SUPPLY_TRAP = 3
        private const val SUPPLY_COMBAT = 2
    }

    /** 本次冒险中到达过的房间 (BFS 不重复走) */
    private val visitedRooms = mutableSetOf<Pair<Int, Int>>()

    suspend fun run(session: ExpeditionSession, stateFlow: MutableStateFlow<GameUiState>) {
        visitedRooms.clear()
        val state = stateFlow.value
        val layer = session.currentLayer

        var currentFloor = state.currentFloor ?: DungeonGenerator.generate(layer)
        val startPos = findStairsUp(currentFloor)
        visitedRooms.add(startPos)
        currentFloor = revealVisibility(currentFloor, startPos.first, startPos.second)

        var s = session.copy(
            isExploring = true,
            posX = startPos.first, posY = startPos.second,
            combatLog = listOf("== Floor $layer / 第${layer}层 探索开始 ==")
        )
        stateFlow.update { it.copy(currentFloor = currentFloor, expedition = s) }
        delay(BASE_INTERVAL_MS)

        while (s.isExploring && s.currentSupply > 0) {
            // ── 主战术: 撤退判定 ──
            if (shouldRetreat(s, stateFlow)) {
                s = s.appendLog("")
                s = s.appendLog("Tactic retreat / 战术撤退！")
                s = s.copy(isExploring = false)
                stateFlow.update { it.copy(expedition = s) }
                break
            }

            val cx = s.posX; val cy = s.posY

            // ── 副战术: BFS 寻路 ──
            val next = bfsNextStep(currentFloor, cx, cy, s.secondaryTactic) ?: run {
                s = s.appendLog("")
                s = s.appendLog("No path forward / 无路可走，撤退...")
                s = s.copy(isExploring = false)
                stateFlow.update { it.copy(expedition = s) }
                break
            }
            val (nx, ny) = next
            val node = currentFloor.grid[ny][nx]

            val isRoom = isRoomNode(node)
            // 在 markVisited 之前判定: 是否首次进入此房间
            val firstEntry = isRoom && !node.label.startsWith("v")
            if (isRoom) visitedRooms.add(nx to ny)

            currentFloor = revealVisibility(currentFloor, nx, ny)

            // 标记已到达 (label "v" 前缀)
            if (firstEntry) {
                currentFloor = markVisited(currentFloor, nx, ny)
            }

            // 房间名称
            val roomLabel = node.label.removePrefix("v")
            val roomName = when {
                node.type == NodeType.BOSS_ROOM -> "Boss Room / Boss房"
                node.event == NodeEvent.STAIRS_DOWN -> "Stairs / 楼梯间"
                node.event == NodeEvent.STAIRS_UP -> "Entry / 入口"
                roomLabel.isNotEmpty() -> "Room $roomLabel / 房间$roomLabel"
                else -> ""
            }

            val hasEvent = node.event != NodeEvent.NONE && node.event != NodeEvent.STAIRS_UP
            val supplyCost: Int
            val log = mutableListOf<String>()

            if (!hasEvent) {
                supplyCost = SUPPLY_NORMAL
                // 仅首次进入空房间才记录，重新踏入不再写入日志
                if (isRoom && node.event == NodeEvent.NONE && firstEntry) {
                    log.add("")
                    log.add("[ $roomName ] Entered / 进入 — nothing here / 平安无事")
                }
            } else {
                val header = if (roomName.isNotEmpty()) "[ $roomName ]" else "[Corridor / 走廊]"

                when (node.event) {
                    NodeEvent.TRAP -> {
                        supplyCost = SUPPLY_TRAP
                        log.add(""); log.add("$header TRAP / 陷阱！")
                        val aliveHeroes = getPartyHeroes(stateFlow.value, s.party).filter { it.isAlive }
                        val maxDex = aliveHeroes.maxOfOrNull { it.effectiveStats[StatType.DEX] ?: 0 } ?: 0
                        val baseDiff = s.currentLayer * 8
                        val chance = ((maxDex - baseDiff) * 5 + 50).coerceIn(20, 95)
                        log.add("  解除判定: 最高 DEX $maxDex vs 难度 $baseDiff → ${chance}%")
                        if (kotlin.random.Random.nextInt(100) < chance) {
                            log.add("  Disarmed / 成功解除！")
                        } else {
                            val trapDmg = 5 * s.currentLayer
                            val partyHeroes = getPartyHeroes(stateFlow.value, s.party)
                            if (partyHeroes.isNotEmpty()) {
                                val damaged = partyHeroes.map { it.copy(currentHp = (it.currentHp - trapDmg).coerceAtLeast(0)) }
                                updateHeroes(stateFlow, damaged)
                                log.add("  Triggered! 全体受到 $trapDmg 点伤害")
                                damaged.forEach { h -> log.add("  ${h.name}: HP ${h.currentHp}/${h.maxHp}") }
                                val downed = damaged.filter { it.currentHp <= 0 }.map { it.id }.toSet()
                                if (downed.isNotEmpty()) s = s.copy(downedHeroIds = s.downedHeroIds + downed)
                            }
                        }
                        currentFloor = clearEvent(currentFloor, nx, ny)
                    }
                    NodeEvent.ENCOUNTER -> {
                        supplyCost = SUPPLY_COMBAT
                        val heroes = getPartyHeroes(stateFlow.value, s.party)
                        if (node.monsterIds.isNotEmpty() && heroes.isNotEmpty()) {
                            log.add(""); log.add("$header ENCOUNTER / 遭遇战！")
                            val result = CombatEngine.fight(heroes, node.monsterIds, s.carriedItems)
                            log.addAll(result.log)
                            updateHeroes(stateFlow, result.updatedHeroes)
                            s = s.copy(
                                backpack = s.backpack + result.droppedItems,
                                carriedItems = result.remainingCarriedItems,
                                downedHeroIds = s.downedHeroIds + result.downedHeroIds
                            )
                            if (result.victory) {
                                currentFloor = clearEvent(currentFloor, nx, ny)
                            } else {
                                log.add("Party injured! Retreating... / 全员重伤！撤退...")
                                // 败北 → 取消 visited 标记，保留事件
                                visitedRooms.remove(nx to ny)
                                currentFloor = unmarkVisited(currentFloor, nx, ny)
                                s = s.copy(isExploring = false)
                            }
                        } else { supplyCost.let { } }
                    }
                    NodeEvent.LOOT -> {
                        supplyCost = SUPPLY_NORMAL
                        log.add(""); log.add("$header LOOT / 发现宝箱！")
                        val loot = node.lootTable.filter { (_, c) -> kotlin.random.Random.nextInt(100) < c }.keys.toList()
                        if (loot.isNotEmpty()) {
                            s = s.copy(backpack = s.backpack + loot)
                            log.add("  Obtained / 获得: ${loot.joinToString()}")
                        } else {
                            log.add("  Empty chest... / 空箱子...")
                        }
                        currentFloor = clearEvent(currentFloor, nx, ny)
                    }
                    NodeEvent.PUZZLE -> {
                        supplyCost = SUPPLY_NORMAL
                        log.add(""); log.add("$header PUZZLE / 解谜！")
                        val aliveHeroes = getPartyHeroes(stateFlow.value, s.party).filter { it.isAlive }
                        val maxInt = aliveHeroes.maxOfOrNull { it.effectiveStats[StatType.INT] ?: 0 } ?: 0
                        val baseDiff = s.currentLayer * 8
                        val chance = ((maxInt - baseDiff) * 5 + 50).coerceIn(20, 95)
                        log.add("  破解判定: 最高 INT $maxInt vs 难度 $baseDiff → ${chance}%")
                        if (kotlin.random.Random.nextInt(100) < chance) {
                            val loot = node.lootTable.filter { (_, c) -> kotlin.random.Random.nextInt(100) < c }.keys.toList()
                            if (loot.isNotEmpty()) {
                                s = s.copy(backpack = s.backpack + loot)
                                log.add("  Solved / 谜题破解，获得: ${loot.joinToString()}")
                            } else {
                                log.add("  Solved / 谜题破解，但里面什么也没有...")
                            }
                        } else {
                            log.add("  Failed / 破解失败")
                        }
                        currentFloor = clearEvent(currentFloor, nx, ny)
                    }
                    NodeEvent.GUARDED_LOOT -> {
                        supplyCost = SUPPLY_COMBAT
                        val heroes = getPartyHeroes(stateFlow.value, s.party)
                        if (node.monsterIds.isNotEmpty() && heroes.isNotEmpty()) {
                            log.add(""); log.add("$header GUARDED LOOT / 守护宝藏！")
                            val result = CombatEngine.fight(heroes, node.monsterIds, s.carriedItems)
                            log.addAll(result.log)
                            updateHeroes(stateFlow, result.updatedHeroes)
                            s = s.copy(
                                backpack = s.backpack + result.droppedItems,
                                carriedItems = result.remainingCarriedItems,
                                downedHeroIds = s.downedHeroIds + result.downedHeroIds
                            )
                            if (result.victory) {
                                log.add("Treasure unsealed / 宝箱解封！")
                                val loot = node.lootTable.filter { (_, c) -> kotlin.random.Random.nextInt(100) < c }.keys.toList()
                                if (loot.isNotEmpty()) {
                                    s = s.copy(backpack = s.backpack + loot)
                                    log.add("  Obtained / 获得: ${loot.joinToString()}")
                                } else {
                                    log.add("  Empty chest... / 空箱子...")
                                }
                                currentFloor = clearEvent(currentFloor, nx, ny)
                            } else {
                                log.add("Defeated! Retreating... / 失败！撤退...")
                                visitedRooms.remove(nx to ny)
                                currentFloor = unmarkVisited(currentFloor, nx, ny)
                                s = s.copy(isExploring = false)
                            }
                        }
                    }
                    NodeEvent.BOSS_FIGHT -> {
                        supplyCost = SUPPLY_COMBAT * 2
                        val heroes = getPartyHeroes(stateFlow.value, s.party)
                        if (node.monsterIds.isNotEmpty() && heroes.isNotEmpty()) {
                            log.add(""); log.add("$header ★ BOSS BATTLE / Boss战！ ★")
                            val result = CombatEngine.fight(heroes, node.monsterIds, s.carriedItems)
                            log.addAll(result.log)
                            updateHeroes(stateFlow, result.updatedHeroes)
                            s = s.copy(
                                backpack = s.backpack + result.droppedItems,
                                carriedItems = result.remainingCarriedItems,
                                downedHeroIds = s.downedHeroIds + result.downedHeroIds
                            )
                            if (result.victory) {
                                log.add("★ Boss defeated! / Boss被击败！ ★")
                                currentFloor = clearEvent(currentFloor, nx, ny)
                            } else {
                                log.add("Defeated by Boss... / 被Boss击倒...全员重伤！")
                                visitedRooms.remove(nx to ny)
                                currentFloor = unmarkVisited(currentFloor, nx, ny)
                                s = s.copy(isExploring = false)
                            }
                        } else { supplyCost.let { } }
                    }
                    NodeEvent.STAIRS_DOWN -> {
                        supplyCost = SUPPLY_NORMAL
                        log.add(""); log.add("$header >> STAIRS DOWN / 发现下行楼梯！")
                        val newLayer = s.currentLayer + 1
                        currentFloor = DungeonGenerator.generate(newLayer, entryX = nx, entryY = ny)
                        visitedRooms.clear()
                        val newStart = findStairsUp(currentFloor)
                        visitedRooms.add(newStart)
                        currentFloor = revealVisibility(currentFloor, newStart.first, newStart.second)
                        s = s.copy(currentLayer = newLayer, posX = newStart.first, posY = newStart.second)
                        log.add(""); log.add("== Floor $newLayer / 第${newLayer}层 探索开始 ==")
                    }
                    else -> { supplyCost = SUPPLY_NORMAL }
                }

                // 战后/陷阱后自动恢复 (持续到无法再恢复)
                val isDamageEvent = node.event == NodeEvent.TRAP ||
                    node.event == NodeEvent.ENCOUNTER ||
                    node.event == NodeEvent.GUARDED_LOOT ||
                    node.event == NodeEvent.BOSS_FIGHT
                if (s.isExploring && isDamageEvent) {
                    val (newSession, recoveryLog) = postEventRecovery(s, stateFlow)
                    s = newSession
                    if (recoveryLog.isNotEmpty()) {
                        log.add("")
                        log.add("[ Recovery / 战后恢复 ]")
                        log.addAll(recoveryLog)
                    }
                }
            }

            s = s.copy(
                posX = nx, posY = ny,
                currentSupply = (s.currentSupply - supplyCost).coerceAtLeast(0),
                combatLog = s.combatLog + log
            )
            stateFlow.update {
                it.copy(currentFloor = currentFloor, expedition = s,
                    gameState = it.gameState.copy(deepestFloorReached = maxOf(it.gameState.deepestFloorReached, s.currentLayer)))
            }

            if (s.currentSupply <= 0) {
                s = s.appendLog(""); s = s.appendLog("Out of supplies! Retreating... / 补给耗尽！撤退...")
                s = s.copy(isExploring = false)
                stateFlow.update { it.copy(expedition = s) }; break
            }
            val alive = getPartyHeroes(stateFlow.value, s.party)
            if (alive.none { it.isAlive }) {
                s = s.appendLog(""); s = s.appendLog("All heroes injured! / 全员重伤！")
                s = s.copy(isExploring = false)
                stateFlow.update { it.copy(expedition = s) }; break
            }

            val stepDelay = if (!hasEvent) BASE_INTERVAL_MS / 3 else BASE_INTERVAL_MS
            delay(stepDelay)
        }

        // ── 探索结束 ──
        if (s.isExploring) s = s.copy(isExploring = false)
        s = s.appendLog("")
        s = s.appendLog("== Expedition ended / 探索结束 ==")
        s = s.appendLog(if (s.backpack.isNotEmpty()) "Loot / 战利品: ${s.backpack.joinToString()}" else "No loot / 无战利品")
        // 倒下过的英雄: 必得一条负面特质
        s = applyDownedTraits(s, stateFlow)
        // 队伍归城 (不再立即治疗, HP/MP 保留撤退时数据, 下次出战开始才重置)
        markPartyIdle(stateFlow, s.party)
        s = s.appendLog("Party returned to town / 队伍归城")
        returnUnusedItems(stateFlow, s.carriedItems)
        transferBackpackToInventory(stateFlow, s.backpack)
        stateFlow.update { it.copy(expedition = s) }
    }

    /** 给本次探索倒下过的英雄各赋予一条至少 T2 的负面特质，写入日志 */
    private fun applyDownedTraits(
        session: ExpeditionSession,
        stateFlow: MutableStateFlow<GameUiState>
    ): ExpeditionSession {
        if (session.downedHeroIds.isEmpty()) return session
        var s = session
        for (heroId in session.downedHeroIds) {
            val hero = stateFlow.value.heroes.find { it.id == heroId } ?: continue
            val newTrait = TraitRegistry.rollDownedTrait(hero.traits) ?: continue
            val tmpl = TraitRegistry.get(newTrait)
            s = s.appendLog("✦ ${hero.name} 因重伤获得负面特质: ${tmpl.name} (${tmpl.description})")
            stateFlow.update { state ->
                state.copy(heroes = state.heroes.map {
                    if (it.id == heroId) TraitRegistry.applyTrait(it, newTrait) else it
                })
            }
        }
        return s
    }

    // ============================
    // 战后恢复 (战斗/陷阱事件之后)
    // ============================

    /**
     * 根据主战术阈值循环恢复:
     *   - HP < threshold (SAFE 75%, 其它 50%): 优先牧师施治疗术，没蓝再喝生命药水
     *   - 任意英雄 currentMp < 某瓶魔法药水恢复量: 喝该药水 (小瓶优先)
     * 持续直到没有任何动作可执行 (即所有人血量健康 + 无人需要补蓝，或者药水/蓝耗尽)
     */
    private fun postEventRecovery(
        session: ExpeditionSession,
        stateFlow: MutableStateFlow<GameUiState>
    ): Pair<ExpeditionSession, List<String>> {
        val hpThreshold = if (session.tactic == TacticType.SAFE) 0.75f else 0.50f
        val carriedItems = session.carriedItems.toMutableList()
        val log = mutableListOf<String>()

        var safety = 0
        while (safety++ < 30) {
            var anyAction = false

            // —— HP 恢复 (牧师优先) ——
            val state = stateFlow.value
            val heroes = getPartyHeroes(state, session.party).filter { it.isAlive }
            val needsHeal = heroes.any { it.currentHp.toFloat() / it.maxHp < hpThreshold }
            if (needsHeal) {
                if (tryClericHealOnce(session.party, stateFlow, log, hpThreshold)) {
                    anyAction = true
                } else if (tryHealthPotionOnce(session.party, stateFlow, carriedItems, log, hpThreshold)) {
                    anyAction = true
                }
            }

            // —— MP 恢复 (无论 HP 是否恢复都跑，确保牧师能续奶) ——
            if (tryManaPotionOnce(session.party, stateFlow, carriedItems, log)) {
                anyAction = true
            }

            if (!anyAction) break
        }
        return session.copy(carriedItems = carriedItems.toList()) to log
    }

    /** 牧师施一次治疗术 (选 MP 够的最强治疗法术) */
    private fun tryClericHealOnce(
        party: List<PartySlot>,
        stateFlow: MutableStateFlow<GameUiState>,
        log: MutableList<String>,
        threshold: Float
    ): Boolean {
        val state = stateFlow.value
        val heroes = getPartyHeroes(state, party).filter { it.isAlive }
        if (heroes.none { it.currentHp.toFloat() / it.maxHp < threshold }) return false

        val cleric = heroes.firstOrNull { hero ->
            hero.heroClass == HeroClass.CLERIC && hero.spells.any {
                val t = SpellRegistry.get(it.templateId)
                (t.target == SpellTarget.HEAL_SINGLE || t.target == SpellTarget.HEAL_AOE) &&
                    hero.currentMp >= t.tiers[it.currentTier - 1].mpCost
            }
        } ?: return false

        val healSpells = cleric.spells.mapNotNull { spell ->
            val tmpl = SpellRegistry.get(spell.templateId)
            if (tmpl.target != SpellTarget.HEAL_SINGLE && tmpl.target != SpellTarget.HEAL_AOE) return@mapNotNull null
            val tier = tmpl.tiers[spell.currentTier - 1]
            if (cleric.currentMp >= tier.mpCost) tmpl to tier else null
        }
        val (tmpl, tier) = healSpells.maxByOrNull { it.second.power } ?: return false

        val statBonus = (cleric.effectiveStats[StatType.FTH] ?: 0) / 2
        val healAmt = tier.power + statBonus

        val updates = mutableMapOf<String, Hero>()
        val newCleric = cleric.copy(currentMp = cleric.currentMp - tier.mpCost)
        updates[cleric.id] = newCleric
        log.add("  ${cleric.name} casts [${tier.name}] (MP:${cleric.currentMp}→${newCleric.currentMp})")

        if (tmpl.target == SpellTarget.HEAL_SINGLE) {
            val patient = heroes
                .filter { it.currentHp.toFloat() / it.maxHp < threshold }
                .minByOrNull { it.currentHp.toFloat() / it.maxHp } ?: return false
            val baseHero = updates[patient.id] ?: patient
            val newHp = (baseHero.currentHp + healAmt).coerceAtMost(baseHero.maxHp)
            updates[patient.id] = baseHero.copy(currentHp = newHp)
            log.add("    → ${patient.name}: +$healAmt HP (HP:$newHp/${baseHero.maxHp})")
        } else {
            for (h in heroes) {
                val baseHero = updates[h.id] ?: h
                val newHp = (baseHero.currentHp + healAmt).coerceAtMost(baseHero.maxHp)
                updates[h.id] = baseHero.copy(currentHp = newHp)
                log.add("    → ${h.name}: +$healAmt HP (HP:$newHp/${baseHero.maxHp})")
            }
        }

        stateFlow.update { st -> st.copy(heroes = st.heroes.map { updates[it.id] ?: it }) }
        return true
    }

    /** 喝一次生命药水 (最低血英雄, 大瓶优先) */
    private fun tryHealthPotionOnce(
        party: List<PartySlot>,
        stateFlow: MutableStateFlow<GameUiState>,
        carriedItems: MutableList<String>,
        log: MutableList<String>,
        threshold: Float
    ): Boolean {
        val state = stateFlow.value
        val heroes = getPartyHeroes(state, party).filter { it.isAlive }
        val patient = heroes
            .filter { it.currentHp.toFloat() / it.maxHp < threshold }
            .minByOrNull { it.currentHp.toFloat() / it.maxHp } ?: return false

        val potionId = when {
            "great_health_potion" in carriedItems -> "great_health_potion"
            "health_potion" in carriedItems -> "health_potion"
            else -> return false
        }
        val healAmt = if (potionId == "great_health_potion") 150 else 50
        carriedItems.remove(potionId)
        val newHp = (patient.currentHp + healAmt).coerceAtMost(patient.maxHp)
        val itemName = try { ItemRegistry.get(potionId).name } catch (_: Exception) { potionId }
        log.add("  ${patient.name} uses $itemName → HP ${patient.currentHp}→$newHp")

        stateFlow.update { st ->
            st.copy(heroes = st.heroes.map { if (it.id == patient.id) it.copy(currentHp = newHp) else it })
        }
        return true
    }

    /** 喝一次魔法药水: 任意英雄 currentMp < 某瓶恢复量则喝。小瓶优先以免浪费。 */
    private fun tryManaPotionOnce(
        party: List<PartySlot>,
        stateFlow: MutableStateFlow<GameUiState>,
        carriedItems: MutableList<String>,
        log: MutableList<String>
    ): Boolean {
        val state = stateFlow.value
        val heroes = getPartyHeroes(state, party).filter { it.isAlive && it.maxMp > 0 }
        if (heroes.isEmpty()) return false

        // 小瓶优先 (mana_potion 30 / great_mana_potion 100)
        val potionTypes = listOf("mana_potion" to 30, "great_mana_potion" to 100)
        for ((potionId, restore) in potionTypes) {
            if (potionId !in carriedItems) continue
            val candidate = heroes.filter { it.currentMp < restore }
                .minByOrNull { it.currentMp } ?: continue
            carriedItems.remove(potionId)
            val newMp = (candidate.currentMp + restore).coerceAtMost(candidate.maxMp)
            val itemName = try { ItemRegistry.get(potionId).name } catch (_: Exception) { potionId }
            log.add("  ${candidate.name} uses $itemName → MP ${candidate.currentMp}→$newMp")
            stateFlow.update { st ->
                st.copy(heroes = st.heroes.map { if (it.id == candidate.id) it.copy(currentMp = newMp) else it })
            }
            return true
        }
        return false
    }

    // ============================
    // 主战术: 撤退判定
    // ============================

    private fun shouldRetreat(session: ExpeditionSession, stateFlow: MutableStateFlow<GameUiState>): Boolean {
        if (session.tactic == TacticType.AGGRESSIVE) return false

        val heroes = getPartyHeroes(stateFlow.value, session.party).filter { it.isAlive }
        if (heroes.isEmpty()) return false

        // 是否有回复手段 (生命药水 或 治疗法术)
        val hasHealPotion = session.carriedItems.any { it == "health_potion" || it == "great_health_potion" }
        val hasHealSpell = heroes.any { hero ->
            val canCast = hero.heroClass == HeroClass.CLERIC
            canCast && hero.spells.any { spell ->
                val t = com.example.idledungeon.data.SpellRegistry.get(spell.templateId)
                (t.target == SpellTarget.HEAL_SINGLE || t.target == SpellTarget.HEAL_AOE) &&
                    hero.currentMp >= t.tiers[spell.currentTier - 1].mpCost
            }
        }
        val hasHealing = hasHealPotion || hasHealSpell
        if (hasHealing) return false

        val anyBelow50 = heroes.any { it.currentHp.toFloat() / it.maxHp < 0.5f }
        val anyBelow20 = heroes.any { it.currentHp.toFloat() / it.maxHp < 0.2f }
        val totalHpRatio = heroes.sumOf { it.currentHp }.toFloat() / heroes.sumOf { it.maxHp }

        return when (session.tactic) {
            TacticType.SAFE -> anyBelow50
            TacticType.BALANCED -> anyBelow20 || totalHpRatio < 0.5f
            TacticType.RISKY -> anyBelow20
            TacticType.AGGRESSIVE -> false
        }
    }

    // ============================
    // 副战术: BFS 寻路
    // ============================

    /**
     * BFS 寻路，优先级根据副战术变化:
     *
     * 所有模式共通:
     *   - 已探索的房间 (label 以 "v" 开头) 永远不会成为目标
     *   - 未探索的拐角 (frontier) 和未探索的房间同等优先
     *
     * STRATEGY (攻略优先):
     *   优先: 未探索的房间/拐角 → 楼梯 → Boss
     *   如果发现了楼梯，楼梯成为最高优先
     *
     * SCAVENGE (搜刮优先):
     *   优先: 未探索的房间/拐角 (排除 Boss/楼梯)
     *   全部探索完后才前往 Boss，Boss 击败后才去楼梯
     *
     * BOSS_RUSH (Boss优先):
     *   优先: 未击败 Boss → 未探索的房间/拐角 → 楼梯
     */
    private fun bfsNextStep(floor: DungeonFloor, cx: Int, cy: Int, tactic: SecondaryTactic): Pair<Int, Int>? {
        val queue = ArrayDeque<Pair<Int, Int>>()
        val prev = mutableMapOf<Pair<Int, Int>, Pair<Int, Int>>()
        val seen = mutableSetOf(cx to cy)
        queue.add(cx to cy)

        // 收集不同类型的最近目标
        var nearestRoom: Pair<Int, Int>? = null      // 未探索房间
        var nearestFrontier: Pair<Int, Int>? = null   // 未探索拐角
        var nearestBoss: Pair<Int, Int>? = null       // 未击败 Boss
        var nearestStairs: Pair<Int, Int>? = null     // 下楼梯

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            if (x != cx || y != cy) {
                val node = floor.grid[y][x]

                // 未探索房间 (label 非空, 没有 "v" 前缀, 不是 Boss/楼梯)
                val isUnexploredRoom = node.label.isNotEmpty() && !node.label.startsWith("v")
                    && node.type == NodeType.CORRIDOR
                    && node.event != NodeEvent.BOSS_FIGHT && node.event != NodeEvent.STAIRS_DOWN

                if (isUnexploredRoom && nearestRoom == null) nearestRoom = x to y

                // 未击败 Boss (仍有 BOSS_FIGHT 事件)
                if (node.type == NodeType.BOSS_ROOM && node.event == NodeEvent.BOSS_FIGHT
                    && (x to y) !in visitedRooms && nearestBoss == null) {
                    nearestBoss = x to y
                }

                // 下楼梯
                if (node.event == NodeEvent.STAIRS_DOWN && (x to y) !in visitedRooms && nearestStairs == null) {
                    nearestStairs = x to y
                }

                // 迷雾边界 (拐角: 已揭示的格子，旁边有未揭示的非墙格)
                if (nearestFrontier == null) {
                    val isFrontier = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0).any { (dx, dy) ->
                        val fx = x + dx; val fy = y + dy
                        fy in floor.grid.indices && fx in floor.grid[0].indices
                            && !floor.grid[fy][fx].explored && floor.grid[fy][fx].type != NodeType.WALL
                    }
                    if (isFrontier) nearestFrontier = x to y
                }
            }
            for ((dx, dy) in listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)) {
                val fx = x + dx; val fy = y + dy
                if (fy !in floor.grid.indices || fx !in floor.grid[0].indices) continue
                if ((fx to fy) in seen) continue
                val adj = floor.grid[fy][fx]
                if (adj.type == NodeType.WALL || !adj.explored) continue
                seen.add(fx to fy)
                prev[fx to fy] = x to y
                queue.add(fx to fy)
            }
        }

        // 检查是否还有未探索内容
        val hasUnexplored = nearestRoom != null || nearestFrontier != null

        // 按副战术选择目标
        val target = when (tactic) {
            SecondaryTactic.STRATEGY -> {
                // 有楼梯就优先楼梯，否则探索房间/拐角，最后 Boss
                if (nearestStairs != null && !hasUnexplored) nearestStairs
                else nearestRoom ?: nearestFrontier ?: nearestStairs ?: nearestBoss
            }
            SecondaryTactic.SCAVENGE -> {
                // 先全部探索完，再打 Boss，最后下楼
                if (hasUnexplored) nearestRoom ?: nearestFrontier
                else nearestBoss ?: nearestStairs
            }
            SecondaryTactic.BOSS_RUSH -> {
                // 有未击败 Boss 就最优先
                nearestBoss ?: nearestRoom ?: nearestFrontier ?: nearestStairs
            }
        } ?: return null

        // 回溯路径取第一步
        var step = target
        while (true) {
            val p = prev[step] ?: return step
            if (p.first == cx && p.second == cy) return step
            step = p
        }
    }

    // ============================
    // 战争迷雾
    // ============================

    private fun revealVisibility(floor: DungeonFloor, px: Int, py: Int): DungeonFloor {
        val visible = mutableSetOf(px to py)
        for ((dx, dy) in listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)) {
            var i = 1
            while (true) {
                val nx = px + dx * i; val ny = py + dy * i
                if (ny !in floor.grid.indices || nx !in floor.grid[0].indices) break
                val node = floor.grid[ny][nx]
                visible.add(nx to ny)
                if (node.type == NodeType.WALL || node.type == NodeType.BOSS_ROOM) break
                if (node.type == NodeType.CORRIDOR && node.label.isNotEmpty()) break
                i++
            }
        }
        val toReveal = mutableSetOf<Pair<Int, Int>>()
        for ((vx, vy) in visible) {
            for (d2 in -1..1) for (d1 in -1..1) {
                val rx = vx + d1; val ry = vy + d2
                if (ry in floor.grid.indices && rx in floor.grid[0].indices) toReveal.add(rx to ry)
            }
        }
        return floor.copy(grid = floor.grid.mapIndexed { y, row ->
            row.mapIndexed { x, node ->
                if ((x to y) in toReveal && !node.explored) node.copy(explored = true) else node
            }
        })
    }

    // ============================
    // Helpers
    // ============================

    private fun isRoomNode(node: DungeonNode): Boolean =
        (node.label.isNotEmpty() && node.type == NodeType.CORRIDOR)
            || node.type == NodeType.BOSS_ROOM
            || node.event == NodeEvent.STAIRS_DOWN
            || node.event == NodeEvent.STAIRS_UP

    private fun markVisited(floor: DungeonFloor, x: Int, y: Int): DungeonFloor =
        floor.copy(grid = floor.grid.mapIndexed { gy, row ->
            row.mapIndexed { gx, n -> if (gx == x && gy == y && !n.label.startsWith("v")) n.copy(label = "v" + n.label) else n }
        })

    private fun unmarkVisited(floor: DungeonFloor, x: Int, y: Int): DungeonFloor =
        floor.copy(grid = floor.grid.mapIndexed { gy, row ->
            row.mapIndexed { gx, n -> if (gx == x && gy == y && n.label.startsWith("v")) n.copy(label = n.label.removePrefix("v")) else n }
        })

    private fun clearEvent(floor: DungeonFloor, x: Int, y: Int): DungeonFloor =
        floor.copy(grid = floor.grid.mapIndexed { gy, row ->
            row.mapIndexed { gx, n ->
                if (gx == x && gy == y) n.copy(event = NodeEvent.NONE, monsterIds = emptyList(), lootTable = emptyMap()) else n
            }
        })

    private fun findStairsUp(floor: DungeonFloor): Pair<Int, Int> {
        for (y in floor.grid.indices) for (x in floor.grid[y].indices)
            if (floor.grid[y][x].event == NodeEvent.STAIRS_UP) return x to y
        for (y in floor.grid.indices) for (x in floor.grid[y].indices)
            if (floor.grid[y][x].type != NodeType.WALL) return x to y
        return 0 to 0
    }

    private fun getPartyHeroes(state: GameUiState, party: List<PartySlot>): List<Hero> =
        party.mapNotNull { slot -> state.heroes.find { it.id == slot.heroId } }

    private fun updateHeroes(stateFlow: MutableStateFlow<GameUiState>, updatedHeroes: List<Hero>) {
        stateFlow.update { state ->
            val map = updatedHeroes.associateBy { it.id }
            state.copy(heroes = state.heroes.map { map[it.id] ?: it })
        }
    }

    /** 探索结束: 状态归 IDLE, 但保留 HP/MP 现值 (下次出战前才回满) */
    private fun markPartyIdle(stateFlow: MutableStateFlow<GameUiState>, party: List<PartySlot>) {
        val ids = party.map { it.heroId }.toSet()
        stateFlow.update { state ->
            state.copy(heroes = state.heroes.map { h ->
                if (h.id in ids) h.copy(status = HeroStatus.IDLE) else h
            })
        }
    }

    private fun transferBackpackToInventory(stateFlow: MutableStateFlow<GameUiState>, backpack: List<String>) {
        if (backpack.isEmpty()) return
        stateFlow.update { state ->
            val items = backpack.mapNotNull { id -> try { ItemRegistry.get(id) } catch (_: Exception) { null } }
            state.copy(inventory = state.inventory + items)
        }
    }

    private fun returnUnusedItems(stateFlow: MutableStateFlow<GameUiState>, remaining: List<String>) {
        if (remaining.isEmpty()) return
        stateFlow.update { state ->
            val items = remaining.mapNotNull { id -> try { ItemRegistry.get(id) } catch (_: Exception) { null } }
            state.copy(inventory = state.inventory + items)
        }
    }

    private fun ExpeditionSession.appendLog(msg: String) = copy(combatLog = combatLog + msg)
}
