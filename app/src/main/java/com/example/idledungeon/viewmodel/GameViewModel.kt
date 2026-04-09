package com.example.idledungeon.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.idledungeon.data.ItemRegistry
import com.example.idledungeon.data.MonsterRegistry
import com.example.idledungeon.engine.ExplorationEngine
import com.example.idledungeon.model.*
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
            Hero(
                id = "recruit_$i",
                name = "Adventurer ${i + 1} / 冒险者${i + 1}",
                portraitUri = null,
                heroClass = heroClass,
                level = level,
                currentHp = baseHp, maxHp = baseHp,
                currentMp = 20 + level * 3, maxMp = 20 + level * 3,
                baseStats = mapOf(
                    StatType.STR to (5..10 + level).random(),
                    StatType.DEX to (5..10 + level).random(),
                    StatType.INT to (5..10 + level).random(),
                    StatType.FTH to (5..10 + level).random()
                )
            )
        }
    }

    private fun generateShopItems(shopLevel: Int, type: ItemType): List<Item> {
        val pool = ItemRegistry.byType(type)
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
            val item = ItemRegistry.get(itemId)
            val cost = item.sellPrice * 2
            val guild = state.gameState.guild
            if (guild.gold < cost) return@update state
            state.copy(
                inventory = state.inventory + item,
                gameState = state.gameState.copy(guild = guild.copy(gold = guild.gold - cost))
            )
        }
    }

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

    fun toggleCarryItem(itemId: String) {
        _uiState.update { state ->
            val current = state.carriedItemIds
            val max = state.gameState.guild.maxCarryItems
            val new = if (itemId in current) current - itemId
                      else if (current.size < max) current + itemId
                      else current
            state.copy(carriedItemIds = new)
        }
    }

    // ============================
    // 探险运行
    // ============================

    fun startExpedition() {
        val state = _uiState.value
        val session = ExpeditionSession(
            dungeonId = "main_dungeon",
            party = state.partySlots,
            tactic = state.selectedTactic,
            currentSupply = state.gameState.guild.currentSupplies,
            carriedItems = state.carriedItemIds,
            isExploring = true
        )
        _uiState.update { it.copy(expedition = session) }

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
    val carriedItemIds: List<String> = emptyList()
)