package com.example.idledungeon.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.idledungeon.data.ItemRegistry
import com.example.idledungeon.data.MonsterRegistry
import com.example.idledungeon.data.SpellRegistry
import com.example.idledungeon.data.TraitRegistry
import com.example.idledungeon.engine.ExplorationEngine
import com.example.idledungeon.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 全局游戏状态 ViewModel
 * 管理所有游戏数据，驱动所有 UI 屏幕
 */
class GameViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val explorationEngine = ExplorationEngine()
    private var pendingNewGame = false

    // ============================
    // 游戏存档
    // ============================

    /** 标记需要开始新游戏，延迟到 Town 页面加载后执行，避免 StartScreen 动画中闪现"继续"按钮 */
    fun requestNewGame() { pendingNewGame = true }

    fun executePendingNewGame() {
        if (pendingNewGame) {
            pendingNewGame = false
            startNewGame()
        }
    }

    fun startNewGame() {
        _uiState.update {
            GameUiState(
                gameState = GameState(hasStarted = true, playerName = "Guild Master"),
                heroes = emptyList(),
                inventory = emptyList()
            )
        }
        refreshShops()
    }

    /**
     * [DEBUG] 测试环境一键准备:
     *   - 4 人满装备队伍 (战士/法师/牧师/游侠) Lv.10, 已编入 partySlots
     *   - 公会满级, 商店满级, 100000 G
     *   - 仓库塞满药水 + 材料, 携带槽预填 10 瓶药水
     *   - 默认 BALANCED + STRATEGY 战术
     * 发布前删除。
     */
    fun startTestMode() {
        val warrior = buildTestHero("test_warrior", "战神测试者", HeroClass.WARRIOR)
        val mage    = buildTestHero("test_mage", "法师测试者", HeroClass.MAGE)
        val cleric  = buildTestHero("test_cleric", "牧师测试者", HeroClass.CLERIC)
        val ranger  = buildTestHero("test_ranger", "游侠测试者", HeroClass.RANGER)
        val testHeroes = listOf(warrior, mage, cleric, ranger)

        val potionStock = buildList {
            repeat(10) { add(ItemRegistry.get("health_potion")) }
            repeat(10) { add(ItemRegistry.get("mana_potion")) }
            repeat(5)  { add(ItemRegistry.get("great_health_potion")) }
            repeat(5)  { add(ItemRegistry.get("great_mana_potion")) }
            repeat(3)  { add(ItemRegistry.get("antidote")) }
            repeat(5)  { add(ItemRegistry.get("ration")) }
            // 一些素材方便测试出售
            repeat(8)  { add(ItemRegistry.get("iron_ore")) }
            repeat(5)  { add(ItemRegistry.get("monster_bone")) }
            repeat(3)  { add(ItemRegistry.get("magic_crystal")) }
        }

        val carried = listOf(
            "health_potion", "health_potion", "health_potion",
            "great_health_potion", "great_health_potion",
            "mana_potion", "mana_potion",
            "great_mana_potion", "great_mana_potion",
            "antidote"
        )

        val testGuild = PlayerGuild(
            gold = 100_000,
            guildLevel = 5, tavernLevel = 5,
            blacksmithLevel = 8, itemShopLevel = 8,
            maxSupplies = 10_500, currentSupplies = 10_500,
            maxCarryItems = 30
        )

        _uiState.update {
            GameUiState(
                gameState = GameState(
                    hasStarted = true,
                    playerName = "[DEBUG] Tester",
                    guild = testGuild,
                    deepestFloorReached = 1
                ),
                heroes = testHeroes,
                inventory = potionStock,
                partySlots = listOf(
                    PartySlot(warrior.id, Position.FRONT),
                    PartySlot(ranger.id, Position.FRONT),
                    PartySlot(mage.id, Position.BACK),
                    PartySlot(cleric.id, Position.BACK)
                ),
                selectedTactic = TacticType.BALANCED,
                selectedSecondaryTactic = SecondaryTactic.STRATEGY,
                carriedItemIds = carried
            )
        }
        refreshShops()
    }

    private fun buildTestHero(id: String, name: String, cls: HeroClass): Hero {
        val level = 1
        // L1 典型属性: 主属性 12, 副属性 4-5 (刚够装上最低装备)
        val baseStats = when (cls) {
            HeroClass.WARRIOR -> mapOf(StatType.STR to 12, StatType.DEX to 5, StatType.INT to 4,  StatType.FTH to 4)
            HeroClass.MAGE    -> mapOf(StatType.STR to 4,  StatType.DEX to 5, StatType.INT to 12, StatType.FTH to 4)
            HeroClass.CLERIC  -> mapOf(StatType.STR to 5,  StatType.DEX to 4, StatType.INT to 5,  StatType.FTH to 12)
            HeroClass.RANGER  -> mapOf(StatType.STR to 4,  StatType.DEX to 12, StatType.INT to 4, StatType.FTH to 5)
            HeroClass.NOVICE  -> mapOf(StatType.STR to 7,  StatType.DEX to 7, StatType.INT to 7,  StatType.FTH to 7)
        }
        // 全部用职业可穿的最低级装备 + L1 通用皮甲 + 铁戒指
        val equipment: Map<EquipSlot, Item> = when (cls) {
            HeroClass.WARRIOR -> mapOf(
                EquipSlot.WEAPON    to ItemRegistry.get("rusty_iron_sword"),  // L1 COMMON
                EquipSlot.ARMOR     to ItemRegistry.get("leather_armor"),      // L1 COMMON
                EquipSlot.ACCESSORY to ItemRegistry.get("iron_ring")           // L1 COMMON
            )
            HeroClass.MAGE -> mapOf(
                EquipSlot.WEAPON    to ItemRegistry.get("wooden_staff"),       // L1 COMMON
                EquipSlot.ARMOR     to ItemRegistry.get("leather_armor"),
                EquipSlot.ACCESSORY to ItemRegistry.get("iron_ring")
            )
            HeroClass.CLERIC -> mapOf(
                EquipSlot.WEAPON    to ItemRegistry.get("wooden_bell"),        // L1 COMMON
                EquipSlot.ARMOR     to ItemRegistry.get("leather_armor"),
                EquipSlot.ACCESSORY to ItemRegistry.get("iron_ring")
            )
            HeroClass.RANGER -> mapOf(
                // 弓类没有 L1, 最低是 hunting_bow (L2 UNCOMMON), 算这条线最差
                EquipSlot.WEAPON    to ItemRegistry.get("hunting_bow"),
                EquipSlot.ARMOR     to ItemRegistry.get("leather_armor"),
                EquipSlot.ACCESSORY to ItemRegistry.get("iron_ring")
            )
            HeroClass.NOVICE -> emptyMap()
        }
        val maxHp = 50 + level * 10  // L1 = 60
        val maxMp = 20 + level * 3   // L1 = 23
        val spells = SpellRegistry.computeSpells(baseStats, level)
        val initial = Hero(
            id = id, name = name, portraitUri = null,
            heroClass = cls, level = level,
            currentHp = maxHp, maxHp = maxHp,
            currentMp = maxMp, maxMp = maxMp,
            baseStats = baseStats,
            equipment = equipment,
            spells = spells,
            unspentStatPoints = 5,  // 测试加点 UI
            unspentSkillPoints = 2
        )
        // 每个测试角色硬塞 2 条特质, 覆盖不同效果类型方便观察战斗触发
        val testTraits = when (cls) {
            HeroClass.WARRIOR -> listOf("trait_berserker", "trait_iron_skin")     // +25% 输出 / -20% 受伤
            HeroClass.MAGE    -> listOf("trait_lethal_strike", "trait_wise")      // 10% 暴击 3x / INT+5
            HeroClass.CLERIC  -> listOf("trait_regen", "trait_vampiric")          // 每回合 +10HP / 15% 吸血
            HeroClass.RANGER  -> listOf("trait_shadow_step", "trait_wind_blade")  // 20% 闪避 / 15% 二连击
            HeroClass.NOVICE  -> emptyList()
        }
        return testTraits.fold(initial) { h, tid -> TraitRegistry.applyTrait(h, tid) }
    }

    // TODO: fun loadGame() - 从 Room 读取存档
    // TODO: fun saveGame() - 写入 Room

    // ============================
    // 城镇经营
    // ============================

    fun invest(building: String) {
        _uiState.update { state ->
            val guild = state.gameState.guild
            val cost = getInvestCost(building)
            if (guild.gold < cost) return@update state

            val newGuild = when (building) {
                "guild" -> guild.copy(
                    guildLevel = guild.guildLevel + 1,
                    maxSupplies = guild.maxSupplies + 50,
                    maxCarryItems = guild.maxCarryItems + 5,
                    gold = guild.gold - cost
                )
                "tavern" -> guild.copy(tavernLevel = guild.tavernLevel + 1, gold = guild.gold - cost)
                "blacksmith" -> guild.copy(blacksmithLevel = guild.blacksmithLevel + 1, gold = guild.gold - cost)
                "item_shop" -> guild.copy(itemShopLevel = guild.itemShopLevel + 1, gold = guild.gold - cost)
                else -> guild
            }
            state.copy(gameState = state.gameState.copy(guild = newGuild))
        }
        refreshShops()
    }

    fun getInvestCost(building: String): Int {
        val level = when (building) {
            "guild" -> _uiState.value.gameState.guild.guildLevel
            "tavern" -> _uiState.value.gameState.guild.tavernLevel
            "blacksmith" -> _uiState.value.gameState.guild.blacksmithLevel
            "item_shop" -> _uiState.value.gameState.guild.itemShopLevel
            else -> 1
        }
        return level * 500 // 简单线性公式，可调整
    }

    // ============================
    // 商店刷新
    // ============================

    /** [DEBUG] 手动刷新商店 */
    fun debugRefreshShops() = refreshShops()

    private fun refreshShops() {
        _uiState.update { state ->
            val guild = state.gameState.guild
            state.copy(
                recruitableHeroes = generateRecruitableHeroes(guild.tavernLevel),
                shopEquipments = generateShopItems(guild.blacksmithLevel, ItemType.EQUIPMENT),
                shopConsumables = generateShopItems(guild.itemShopLevel, ItemType.CONSUMABLE)
            )
        }
    }

    private fun generateRecruitableHeroes(tavernLevel: Int): List<Hero> {
        val count = 3 + tavernLevel
        return List(count) { i ->
            val heroClass = HeroClass.entries.random()
            val level = (1..tavernLevel * 3).random()
            val baseHp = 50 + level * 10

            // 主属性高 (8~14+level)，副属性低 (2~5+level/2)
            fun primary() = (8..14 + level).random()
            fun secondary() = (2..5 + level / 2).random()

            val stats = when (heroClass) {
                //                        STR         DEX         INT         FTH
                HeroClass.WARRIOR -> mapOf(
                    StatType.STR to primary(),
                    StatType.DEX to secondary(),
                    StatType.INT to secondary(),
                    StatType.FTH to secondary()
                )
                HeroClass.RANGER -> mapOf(
                    StatType.STR to secondary(),
                    StatType.DEX to primary(),
                    StatType.INT to secondary(),
                    StatType.FTH to secondary()
                )
                HeroClass.MAGE -> mapOf(
                    StatType.STR to secondary(),
                    StatType.DEX to secondary(),
                    StatType.INT to primary(),
                    StatType.FTH to secondary()
                )
                HeroClass.CLERIC -> mapOf(
                    StatType.STR to secondary(),
                    StatType.DEX to secondary(),
                    StatType.INT to secondary(),
                    StatType.FTH to primary()
                )
                HeroClass.NOVICE -> mapOf(
                    StatType.STR to (5..8 + level).random(),
                    StatType.DEX to (5..8 + level).random(),
                    StatType.INT to (5..8 + level).random(),
                    StatType.FTH to (5..8 + level).random()
                )
            }

            val spells = SpellRegistry.computeSpells(stats, level)
            val initial = Hero(
                id = "recruit_$i",
                name = "Adventurer ${i + 1} / 冒险者${i + 1}",
                portraitUri = null,
                heroClass = heroClass,
                level = level,
                currentHp = baseHp, maxHp = baseHp,
                currentMp = 20 + level * 3, maxMp = 20 + level * 3,
                baseStats = stats,
                spells = spells
            )
            // 滚特质并立即结算 HP/MP 加成
            TraitRegistry.rollRecruitTraits().fold(initial) { h, tid ->
                TraitRegistry.applyTrait(h, tid)
            }
        }
    }

    private fun generateShopItems(shopLevel: Int, type: ItemType): List<Item> {
        // forShop 已过滤 purchasable + equipLevel <= shopLevel
        val pool = ItemRegistry.forShop(type, shopLevel)
        val count = (3 + shopLevel).coerceAtMost(pool.size)
        return pool.shuffled().take(count)
    }

    // ============================
    // 英雄管理
    // ============================

    fun recruitHero(heroId: String) {
        _uiState.update { state ->
            val hero = state.recruitableHeroes.find { it.id == heroId } ?: return@update state
            val recruited = hero.copy(id = "hero_${System.currentTimeMillis()}")
            state.copy(
                heroes = state.heroes + recruited,
                recruitableHeroes = state.recruitableHeroes - hero
            )
        }
    }

    fun buyItem(itemId: String) {
        _uiState.update { state ->
            val item = state.shopEquipments.find { it.id == itemId }
                ?: state.shopConsumables.find { it.id == itemId }
                ?: return@update state
            val cost = item.goldCost   // 购买价由 (等级×稀有度) 公式派生
            val guild = state.gameState.guild
            if (guild.gold < cost) return@update state
            state.copy(
                inventory = state.inventory + item,
                shopEquipments = state.shopEquipments.filterNot { it.id == itemId },
                shopConsumables = state.shopConsumables.filterNot { it.id == itemId },
                gameState = state.gameState.copy(guild = guild.copy(gold = guild.gold - cost))
            )
        }
    }

    // ============================
    // 装备管理
    // ============================

    /** 检查英雄是否满足物品的属性需求 + 职业限制 */
    fun meetsRequirements(hero: Hero, item: Item): Boolean {
        val statsOk = item.statRequirements.all { (stat, req) ->
            (hero.baseStats[stat] ?: 0) >= req
        }
        val classOk = item.weaponType?.requiredClass()?.let { it == hero.heroClass } ?: true
        return statsOk && classOk
    }

    /** 装备物品: 从仓库取出 → 装到英雄身上 (旧装备退回仓库) */
    fun equipItem(heroId: String, item: Item) {
        _uiState.update { state ->
            val hero = state.heroes.find { it.id == heroId } ?: return@update state
            val slot = item.equipSlot ?: return@update state
            if (!meetsRequirements(hero, item)) return@update state

            // 旧装备退回仓库
            val oldItem = hero.equipment[slot]
            val newEquipment = hero.equipment + (slot to item)
            val newHero = hero.copy(equipment = newEquipment)

            var newInventory = state.inventory.toMutableList()
            // 移除仓库中该物品 (只移除一件)
            val idx = newInventory.indexOfFirst { it.id == item.id }
            if (idx >= 0) newInventory.removeAt(idx)
            // 旧装备入库
            if (oldItem != null) newInventory.add(oldItem)

            state.copy(
                heroes = state.heroes.map { if (it.id == heroId) newHero else it },
                inventory = newInventory
            )
        }
    }

    /** 卸下装备: 从英雄身上取下 → 退回仓库 */
    fun unequipItem(heroId: String, slot: EquipSlot) {
        _uiState.update { state ->
            val hero = state.heroes.find { it.id == heroId } ?: return@update state
            val item = hero.equipment[slot] ?: return@update state
            val newHero = hero.copy(equipment = hero.equipment - slot)
            state.copy(
                heroes = state.heroes.map { if (it.id == heroId) newHero else it },
                inventory = state.inventory + item
            )
        }
    }

    // ============================
    // 属性点分配
    // ============================

    /** 消耗 1 属性点，加 1 点指定属性 */
    fun allocateStatPoint(heroId: String, stat: StatType) {
        _uiState.update { state ->
            val hero = state.heroes.find { it.id == heroId } ?: return@update state
            if (hero.unspentStatPoints <= 0) return@update state
            val newStats = hero.baseStats.toMutableMap()
            newStats[stat] = (newStats[stat] ?: 0) + 1
            val newHero = hero.copy(
                baseStats = newStats,
                unspentStatPoints = hero.unspentStatPoints - 1
            )
            state.copy(heroes = state.heroes.map { if (it.id == heroId) newHero else it })
        }
    }

    /** 消耗 1 属性点，加 50 HP */
    fun allocateHpPoint(heroId: String) {
        _uiState.update { state ->
            val hero = state.heroes.find { it.id == heroId } ?: return@update state
            if (hero.unspentStatPoints <= 0) return@update state
            val newHero = hero.copy(
                maxHp = hero.maxHp + 50,
                currentHp = hero.currentHp + 50, // 当前也加满
                unspentStatPoints = hero.unspentStatPoints - 1
            )
            state.copy(heroes = state.heroes.map { if (it.id == heroId) newHero else it })
        }
    }

    /** [DEBUG] 给当前装备的武器/护甲增加熟练度 XP，发布前删除 */
    fun debugAddProficiency(heroId: String, amount: Int = 50) {
        _uiState.update { state ->
            val hero = state.heroes.find { it.id == heroId } ?: return@update state
            val types = listOfNotNull(
                hero.equipment[EquipSlot.WEAPON]?.weaponType,
                hero.equipment[EquipSlot.ARMOR]?.weaponType
            ).distinct()
            if (types.isEmpty()) return@update state
            val newXp = hero.proficiencyXp.toMutableMap()
            for (t in types) newXp[t] = (newXp[t] ?: 0) + amount
            val newHero = hero.copy(proficiencyXp = newXp)
            state.copy(heroes = state.heroes.map { if (it.id == heroId) newHero else it })
        }
    }

    /** 消耗 1 属性点，加 20 MP */
    fun allocateMpPoint(heroId: String) {
        _uiState.update { state ->
            val hero = state.heroes.find { it.id == heroId } ?: return@update state
            if (hero.unspentStatPoints <= 0) return@update state
            val newHero = hero.copy(
                maxMp = hero.maxMp + 20,
                currentMp = hero.currentMp + 20,
                unspentStatPoints = hero.unspentStatPoints - 1
            )
            state.copy(heroes = state.heroes.map { if (it.id == heroId) newHero else it })
        }
    }

    /** 卖出该 ID 的一件物品 (UI 推荐使用) */
    fun sellOne(itemId: String) {
        _uiState.update { state ->
            val idx = state.inventory.indexOfFirst { it.id == itemId }
            if (idx < 0) return@update state
            val item = state.inventory[idx]
            val newInventory = state.inventory.toMutableList().apply { removeAt(idx) }
            val guild = state.gameState.guild
            state.copy(
                inventory = newInventory,
                gameState = state.gameState.copy(
                    guild = guild.copy(gold = guild.gold + item.sellPrice)
                )
            )
        }
    }

    /** 卖出该 ID 的全部物品 */
    fun sellAll(itemId: String) {
        _uiState.update { state ->
            val matching = state.inventory.filter { it.id == itemId }
            if (matching.isEmpty()) return@update state
            val totalGold = matching.first().sellPrice * matching.size
            val newInventory = state.inventory.filter { it.id != itemId }
            val guild = state.gameState.guild
            state.copy(
                inventory = newInventory,
                gameState = state.gameState.copy(
                    guild = guild.copy(gold = guild.gold + totalGold)
                )
            )
        }
    }

    /** 兼容旧调用 */
    fun sellItem(item: Item) = sellOne(item.id)

    // ============================
    // 探险编队
    // ============================

    fun addToParty(heroId: String, position: Position) {
        _uiState.update { state ->
            if (state.partySlots.size >= 4) return@update state
            if (state.partySlots.any { it.heroId == heroId }) return@update state
            state.copy(partySlots = state.partySlots + PartySlot(heroId, position))
        }
    }

    fun removeFromParty(heroId: String) {
        _uiState.update { state ->
            state.copy(partySlots = state.partySlots.filter { it.heroId != heroId })
        }
    }

    fun setTactic(tactic: TacticType) {
        _uiState.update { it.copy(selectedTactic = tactic) }
    }

    fun setSecondaryTactic(tactic: SecondaryTactic) {
        _uiState.update { it.copy(selectedSecondaryTactic = tactic) }
    }

    /** 携带消耗品: +1 或 -1 (按 itemId 增减，可重复携带同种物品) */
    fun addCarryItem(itemId: String) {
        _uiState.update { state ->
            val current = state.carriedItemIds
            val max = state.gameState.guild.maxCarryItems
            // 不能超过仓库中该物品的数量
            val ownedCount = state.inventory.count { it.id == itemId }
            val carriedCount = current.count { it == itemId }
            if (current.size >= max || carriedCount >= ownedCount) return@update state
            state.copy(carriedItemIds = current + itemId)
        }
    }

    fun removeCarryItem(itemId: String) {
        _uiState.update { state ->
            val current = state.carriedItemIds.toMutableList()
            val idx = current.indexOf(itemId)
            if (idx >= 0) current.removeAt(idx)
            state.copy(carriedItemIds = current)
        }
    }

    // ============================
    // [DEBUG] 地图测试 — 发布前删除
    // ============================

    private var debugWalkJob: Job? = null
    private val debugVisitedRooms = mutableSetOf<Pair<Int, Int>>()

    fun debugPreviewMap() {
        debugWalkJob?.cancel()
        debugVisitedRooms.clear()

        val floor = com.example.idledungeon.engine.DungeonGenerator.generate(layer = 1)

        // 找楼梯上行作为起点
        var sx = 0; var sy = 0
        for (y in floor.grid.indices) for (x in floor.grid[y].indices) {
            if (floor.grid[y][x].event == NodeEvent.STAIRS_UP) { sx = x; sy = y }
        }

        debugVisitedRooms.add(sx to sy)
        val revealedFloor = revealVisibility(floor, sx, sy)
        val expedition = ExpeditionSession(
            dungeonId = "debug", party = emptyList(),
            currentSupply = 99999, isExploring = true, posX = sx, posY = sy
        )
        _uiState.update { it.copy(currentFloor = revealedFloor, expedition = expedition) }

        // 启动 BFS 驱动的自动行走
        debugWalkJob = viewModelScope.launch {
            delay(400)
            var steps = 0
            while (steps < 800) {
                steps++
                val state = _uiState.value
                val curFloor = state.currentFloor ?: break
                val exp = state.expedition ?: break

                val next = debugBfsNextStep(curFloor, exp.posX, exp.posY) ?: break
                val (nx, ny) = next
                val node = curFloor.grid[ny][nx]

                // 抵达房间/Boss/楼梯 → 标记为已探索
                val isRoom = (node.label.isNotEmpty() && node.type == NodeType.CORRIDOR)
                    || node.type == NodeType.BOSS_ROOM || node.event == NodeEvent.STAIRS_DOWN
                if (isRoom) debugVisitedRooms.add(nx to ny)

                val reachedEnd = node.event == NodeEvent.STAIRS_DOWN
                var newFloor = revealVisibility(curFloor, nx, ny)

                // 标记已探索房间 (label 加 "v" 前缀供渲染区分)
                if (isRoom && !node.label.startsWith("v")) {
                    val g = newFloor.grid.mapIndexed { gy, row ->
                        row.mapIndexed { gx, n ->
                            if (gx == nx && gy == ny) n.copy(label = "v" + n.label) else n
                        }
                    }
                    newFloor = newFloor.copy(grid = g)
                }

                _uiState.update {
                    it.copy(currentFloor = newFloor, expedition = exp.copy(posX = nx, posY = ny, isExploring = !reachedEnd))
                }
                if (reachedEnd) break
                delay(150)
            }
        }
    }

    /** BFS 寻路: 优先找最近的未探索房间，其次找迷雾边界 (frontier)，返回路径上的第一步 */
    private fun debugBfsNextStep(floor: DungeonFloor, cx: Int, cy: Int): Pair<Int, Int>? {
        val queue = ArrayDeque<Pair<Int, Int>>()
        val prev = mutableMapOf<Pair<Int, Int>, Pair<Int, Int>>()
        val seen = mutableSetOf(cx to cy)
        queue.add(cx to cy)

        var roomTarget: Pair<Int, Int>? = null
        var frontierTarget: Pair<Int, Int>? = null

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            if (x != cx || y != cy) {
                val node = floor.grid[y][x]
                // 优先级 1: 未探索的房间/Boss/下楼梯
                val isTarget = ((node.label.isNotEmpty() && !node.label.startsWith("v") && node.type == NodeType.CORRIDOR)
                    || (node.type == NodeType.BOSS_ROOM && (x to y) !in debugVisitedRooms)
                    || node.event == NodeEvent.STAIRS_DOWN) && (x to y) !in debugVisitedRooms
                if (isTarget) { roomTarget = x to y; break }

                // 优先级 2: 迷雾边界 (已揭示的走廊，旁边有未揭示的非墙格)
                if (frontierTarget == null) {
                    val isFrontier = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0).any { (dx, dy) ->
                        val nx = x + dx; val ny = y + dy
                        ny in floor.grid.indices && nx in floor.grid[0].indices
                            && !floor.grid[ny][nx].explored && floor.grid[ny][nx].type != NodeType.WALL
                    }
                    if (isFrontier) frontierTarget = x to y
                }
            }
            for ((dx, dy) in listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)) {
                val nx = x + dx; val ny = y + dy
                if (ny !in floor.grid.indices || nx !in floor.grid[0].indices) continue
                if ((nx to ny) in seen) continue
                val adj = floor.grid[ny][nx]
                if (adj.type == NodeType.WALL || !adj.explored) continue
                seen.add(nx to ny)
                prev[nx to ny] = x to y
                queue.add(nx to ny)
            }
        }

        val target = roomTarget ?: frontierTarget ?: return null
        // 回溯路径，取第一步
        var step = target
        while (true) {
            val p = prev[step] ?: return step
            if (p.first == cx && p.second == cy) return step
            step = p
        }
    }

    /** 视野计算: 从 (px,py) 向四方投射直线，房间/Boss房/墙壁阻挡。每个可见格驱散 3×3 迷雾。 */
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
            for (dy in -1..1) for (dx in -1..1) {
                val nx = vx + dx; val ny = vy + dy
                if (ny in floor.grid.indices && nx in floor.grid[0].indices) toReveal.add(nx to ny)
            }
        }
        val newGrid = floor.grid.mapIndexed { y, row ->
            row.mapIndexed { x, node ->
                if ((x to y) in toReveal && !node.explored) node.copy(explored = true) else node
            }
        }
        return floor.copy(grid = newGrid)
    }

    // ============================
    // 探险运行
    // ============================

    fun startExpedition() {
        val state = _uiState.value

        // 从仓库中取出携带的消耗品
        val toCarry = state.carriedItemIds.toMutableList()
        val newInventory = state.inventory.toMutableList()
        val actualCarried = mutableListOf<String>()
        for (itemId in toCarry) {
            val idx = newInventory.indexOfFirst { it.id == itemId }
            if (idx >= 0) {
                newInventory.removeAt(idx)
                actualCarried.add(itemId)
            }
        }

        val session = ExpeditionSession(
            dungeonId = "main_dungeon",
            party = state.partySlots,
            tactic = state.selectedTactic,
            secondaryTactic = state.selectedSecondaryTactic,
            currentSupply = state.gameState.guild.currentSupplies,
            carriedItems = actualCarried,
            isExploring = true
        )

        // 出征队员: 状态置 DEPLOYED, 同时满血满蓝 (上一轮撤退保留下来的伤害此刻才修复)
        val partyIds = state.partySlots.map { it.heroId }.toSet()
        _uiState.update {
            it.copy(
                expedition = session,
                currentFloor = null,
                inventory = newInventory,
                heroes = it.heroes.map { h ->
                    if (h.id in partyIds) h.copy(
                        status = HeroStatus.DEPLOYED,
                        currentHp = h.maxHp,
                        currentMp = h.maxMp
                    ) else h
                }
            )
        }

        // 启动协程驱动探索
        viewModelScope.launch {
            explorationEngine.run(session, _uiState)
        }
    }
}

/**
 * UI 状态 - 所有屏幕从这里读取数据
 */
data class GameUiState(
    val gameState: GameState = GameState(),

    // 英雄
    val heroes: List<Hero> = emptyList(),
    val recruitableHeroes: List<Hero> = emptyList(),

    // 物品
    val inventory: List<Item> = emptyList(),
    val shopEquipments: List<Item> = emptyList(),
    val shopConsumables: List<Item> = emptyList(),

    // 任务
    val quests: List<Quest> = emptyList(),

    // 图鉴
    val codexEntries: List<Pair<MonsterCodexEntry, Monster?>> = emptyList(),

    // 地下城
    val currentFloor: DungeonFloor? = null,
    val expedition: ExpeditionSession? = null,

    // 编队临时状态
    val partySlots: List<PartySlot> = emptyList(),
    val selectedTactic: TacticType = TacticType.BALANCED,
    val selectedSecondaryTactic: SecondaryTactic = SecondaryTactic.STRATEGY,
    val carriedItemIds: List<String> = emptyList()
)