package com.gaotong.idledungeon.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaotong.idledungeon.data.ItemRegistry
import com.gaotong.idledungeon.data.SaveRepository
import com.gaotong.idledungeon.data.SpellRegistry
import com.gaotong.idledungeon.engine.ExplorationEngine
import com.gaotong.idledungeon.model.*
import com.gaotong.idledungeon.util.L
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 全 App 唯一的 ViewModel, 装所有游戏状态.
 *
 * 持久化:
 *   - init 块异步从 Room 读出存档, 没有就保留默认 (空白) 状态
 *   - uiState 任何变化触发一次去抖动 (500ms) 后写库, 避免每次操作都 IO
 *   - 新游戏启动前先清库, 防止旧数据混入
 */
@HiltViewModel
@OptIn(FlowPreview::class)
class GameViewModel @Inject constructor(
    private val saveRepo: SaveRepository,
    private val explorationEngine: ExplorationEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // 一次性事件流 (Snackbar 消息). DROP_OLDEST 让 emit 永不阻塞.
    private val _userMessages = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private fun toast(tag: String, msg: String) {
        L.i(tag, "user-msg: $msg")
        _userMessages.tryEmit(msg)
    }

    // 真重置延迟到 TOWN 显示后, 否则 StartScreen 退出动画里会闪现"继续"按钮
    private var pendingNewGame = false

    init {
        viewModelScope.launch {
            val loadedState = saveRepo.load()
            // 用户可能在 load 完成前就点了"新游戏", 别覆盖回去
            if (loadedState != null && !_uiState.value.gameState.hasStarted) {
                _uiState.value = loadedState
                checkDailyRefresh()
                checkOfflineCatchUp()
            }
        }
        // 自动保存: state 变化 → debounce 500ms → 写库.
        // hasStarted 守卫顺便挡掉 StateFlow 订阅时重放的初始空值.
        viewModelScope.launch {
            _uiState
                .debounce(500L)
                .collect { state ->
                    if (state.gameState.hasStarted) {
                        saveRepo.save(state)
                    }
                }
        }
    }

    companion object {
        // 5 个主线任务, 顺序触发. 完成一个自动激活下一个.
        val MAIN_QUESTS: List<Quest> = listOf(
            Quest(
                id = "q1_explore15", title = "Into the Dungeon",
                description = "Explore 15 rooms",
                type = QuestType.EXPLORE_ROOMS, targetCount = 15
            ),
            Quest(
                id = "q2_boss1", title = "Slay the Rat King",
                description = "Defeat the Floor 1 Boss",
                type = QuestType.DEFEAT_BOSS, targetFloor = 1
            ),
            Quest(
                id = "q3_find_sword", title = "Legend of the Mossy Blade",
                description = "Find the Mossy Blade (Epic) in a guarded chest on Floor 2",
                type = QuestType.FIND_ITEM, targetItemId = QUEST_EPIC_SWORD_ID
            ),
            Quest(
                id = "q4_boss2", title = "Slay the Chieftain",
                description = "Defeat the Floor 2 Boss",
                type = QuestType.DEFEAT_BOSS, targetFloor = 2
            ),
            Quest(
                id = "q5_boss3", title = "Into the Abyss",
                description = "Defeat the Floor 3 Boss",
                type = QuestType.DEFEAT_BOSS, targetFloor = 3
            )
        )

        // 离线补偿参数
        /** 多少真实毫秒折算为探险的 1 步. 调大可让离线节奏接近"每格数分钟"的设计目标. */
        private const val OFFLINE_MS_PER_STEP = 1200L
        /** 离线补偿的时长上限 — 防止离开过久导致一次性模拟过多步数 (取 24 小时). */
        private const val MAX_OFFLINE_MS = 24L * 60 * 60 * 1000
    }

    // 游戏存档

    // 标记"等下要新游戏". 真正的重置延迟到 Town 屏幕加载后, 否则 StartScreen 退出动画里会闪现"继续"按钮.
    fun requestNewGame() { pendingNewGame = true }

    // 在 TOWN 的 LaunchedEffect 里调; 标志位还在就触发重置, 否则什么都不做.
    fun executePendingNewGame() {
        if (pendingNewGame) {
            pendingNewGame = false
            startNewGame()
        }
    }

    fun startNewGame() {
        L.i("VM", "startNewGame: reset state to a fresh save")
        // 不需要显式 clear DB: 后续自动保存会经 heroDao.replaceAll + snapshot upsert REPLACE 整体覆盖.
        // 显式 clear 反而会和 debounced save 形成 clear→save→clear 的竞态.
        // _uiState.update { 旧 -> 新 }: 原子地把 state 替换成 lambda 返回的新值.
        _uiState.update {
            GameUiState(
                gameState = GameState(hasStarted = true, playerName = "Guild Master"),
                heroes = emptyList(),
                inventory = emptyList(),
                quests = MAIN_QUESTS
            )
        }
        refreshShops()
    }

    // 玩家点开公会界面时调用, 清掉红感叹号.
    fun markQuestUpdateRead() {
        _uiState.update { state ->
            if (!state.gameState.hasUnreadQuestUpdate) return@update state          // 没有变化就别 emit, 省一次保存
            state.copy(gameState = state.gameState.copy(hasUnreadQuestUpdate = false))
        }
    }

    // 城镇经营

    //常规升级
    fun invest(building: String) {
        val guild = _uiState.value.gameState.guild
        val cost = getInvestCost(building)
        if (guild.gold < cost) {
            toast("VM", "Not enough gold: upgrading $building costs $cost, you have ${guild.gold}")
            return
        }
        when (building) {
            "guild" -> { toast("VM", "Upgrade guild? How did you even get here???"); return }
            "tavern" -> if (guild.tavernLevel >= 10) {
                toast("VM", "Tavern is at max level (10)"); return
            }
            "item_shop" -> if (guild.itemShopLevel >= 5) {
                toast("VM", "Item shop is at max level (5)"); return
            }
            "blacksmith" -> if (guild.blacksmithLevel >= 5) {
                toast("VM", "Blacksmith is at max level (5)"); return
            }
            else -> { L.w("VM", "invest: unknown building '$building'"); return }
        }

        _uiState.update { state ->
            val g = state.gameState.guild                                   // 重新取一次, 因为 .update {} 是原子的, lambda 内必须用最新值
            val newGuild = when (building) {
                "tavern" -> g.copy(tavernLevel = g.tavernLevel + 1, gold = g.gold - cost)
                "blacksmith" -> g.copy(blacksmithLevel = g.blacksmithLevel + 1, gold = g.gold - cost)
                "item_shop" -> g.copy(itemShopLevel = g.itemShopLevel + 1, gold = g.gold - cost)
                else -> g
            }
            state.copy(gameState = state.gameState.copy(guild = newGuild))
        }
        L.i("VM", "invest success: $building upgraded, cost $cost")
        refreshShops()                                                      // 升级后商品池可能变, 立即刷一次
    }

    fun getInvestCost(building: String): Int {
        val level = when (building) {
            "guild" -> _uiState.value.gameState.guild.guildRank
            "tavern" -> _uiState.value.gameState.guild.tavernLevel
            "blacksmith" -> _uiState.value.gameState.guild.blacksmithLevel
            "item_shop" -> _uiState.value.gameState.guild.itemShopLevel
            else -> 1
        }
        return level * 500                                                  // 简单线性公式
    }

    // 商店刷新

    // 检查是否进入新的一天 (现实时间). 是的话把铁匠铺/道具店/酒馆全部刷新.
    // 应在 App 进入前台 (ON_START) 时调用一次.
    fun checkDailyRefresh() {
        val today = currentDayKey()
        if (today > _uiState.value.gameState.lastShopRefreshDayKey) {
            refreshShops()
        }
    }

    // YYYYMMDD 格式日期键, 不依赖 java.time, 兼容老 Android 版本.
    private fun currentDayKey(): Int {                                      //获取当前时间的函数
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) * 10000 +
            (cal.get(java.util.Calendar.MONTH) + 1) * 100 +                 //666
            cal.get(java.util.Calendar.DAY_OF_MONTH)
    }

    // 刷新酒馆招募 + 铁匠铺武器/护甲 + 道具店消耗品, 同时写入今日的 dayKey + 递增招募编号.
    private fun refreshShops() {
        _uiState.update { state ->
            val guild = state.gameState.guild
            val startNum = state.gameState.nextRecruitNumber
            val recruits = generateRecruitableHeroes(guild.tavernLevel, startNum)
            state.copy(
                recruitableHeroes = recruits,
                shopEquipments = generateShopItems(guild.blacksmithLevel, ItemType.EQUIPMENT),
                shopConsumables = generateShopItems(guild.itemShopLevel, ItemType.CONSUMABLE),
                gameState = state.gameState.copy(
                    lastShopRefreshDayKey = currentDayKey(),
                    nextRecruitNumber = startNum + recruits.size
                )
            )
        }
    }

    /**
     * 生成 N 名候选冒险者. 编号从 [startNumber] 开始累加, 跨刷新保证名字唯一.
     * 调用方负责把 startNumber + count 写回 GameState.nextRecruitNumber.
     */
    private fun generateRecruitableHeroes(tavernLevel: Int, startNumber: Int): List<Hero> {
        val count = 3 + tavernLevel                                         // 酒馆每升一级多 1 个候选
        // List(n) { i -> ... }: 创建长度为 n 的列表, lambda 给每个下标 i 生成元素.
        return List(count) { i ->
            val recruitNum = startNumber + i                                // 全局递增编号 (用于 id + 名字)
            val heroClass = HeroClass.entries.random()                      // .entries 是枚举所有值的 List, 随机抽一个
            val level = (1..tavernLevel).random()                       // 等级范围跟酒馆等级挂钩

            // 主属性 (5~11+level*3), 副属性 (2~5+level). 用嵌套函数让下面 mapOf 调用清爽.
            fun primary() = (5..11 + level*3).random()
            fun secondary() = (2..5 + level).random()

            // 按职业决定主/副属性是哪一项. mapOf 创建只读 Map.
            val stats = when (heroClass) {
                HeroClass.WARRIOR -> mapOf(
                    StatType.STR to primary(), StatType.DEX to secondary(),
                    StatType.INT to secondary(), StatType.FTH to secondary()
                )
                HeroClass.RANGER -> mapOf(
                    StatType.STR to secondary(), StatType.DEX to primary(),
                    StatType.INT to secondary(), StatType.FTH to secondary()
                )
                HeroClass.MAGE -> mapOf(
                    StatType.STR to secondary(), StatType.DEX to secondary(),
                    StatType.INT to primary(), StatType.FTH to secondary()
                )
                HeroClass.CLERIC -> mapOf(
                    StatType.STR to secondary(), StatType.DEX to secondary(),
                    StatType.INT to secondary(), StatType.FTH to primary()
                )
            }

            val baseHp = 50 + level * 10 + stats[StatType.STR]!! * 5
            val spells = SpellRegistry.computeSpells(stats, level, heroClass)
            Hero(
                id = "recruit_$recruitNum",
                name = "Adventurer $recruitNum",
                portraitUri = null,
                heroClass = heroClass, level = level,
                currentHp = baseHp, maxHp = baseHp,
                currentMp = 20 + level * 3, maxMp = 20 + level * 3,
                baseStats = stats, spells = spells
            )
        }
    }

    private fun generateShopItems(shopLevel: Int, type: ItemType): List<Item> {
        val pool = ItemRegistry.forShop(type, shopLevel)                    // 已过滤 purchasable + equipLevel <= shopLevel
        val count = (3 + shopLevel).coerceAtMost(pool.size)                 // coerceAtMost = 不超过. shuffled().take(n) = 随机抽 n 个
        return pool.shuffled().take(count)
    }

    // 英雄管理

    // 重命名英雄. 空白名字忽略, 自动 trim.
    fun renameHero(heroId: String, newName: String) {
        if (newName.isBlank()) return
        _uiState.update { state ->
            // .map { } 把列表的每个元素映射成新元素. List 是不可变的, 这里返回的是新 List.
            state.copy(heroes = state.heroes.map {
                if (it.id == heroId) it.copy(name = newName.trim()) else it
            })
        }
    }

    // 设置英雄头像 (传 null 清空回到默认图标).
    fun setHeroPortrait(heroId: String, uri: String?) {
        _uiState.update { state ->
            state.copy(heroes = state.heroes.map {
                if (it.id == heroId) it.copy(portraitUri = uri) else it
            })
        }
    }

    fun recruitHero(heroId: String) {
        val hero = _uiState.value.recruitableHeroes.find { it.id == heroId }
        if (hero == null) {
            L.w("VM", "recruitHero: id=$heroId not in recruit list (already recruited or refreshed)")
            toast("VM", "That adventurer is no longer available")
            return
        }
        _uiState.update { state ->
            // 招募后给一个新的 ID, 跟酒馆模板 ID 区分开 (防止下次刷新还以为是同一人).
            val recruited = hero.copy(id = "hero_${System.currentTimeMillis()}")
            state.copy(
                heroes = state.heroes + recruited,                          // List + 元素 = 新 List
                recruitableHeroes = state.recruitableHeroes - hero          // List - 元素 = 移除该元素的新 List
            )
        }
        toast("VM", "Recruited ${hero.name} (Lv.${hero.level} ${hero.heroClass})")
    }

    fun buyItem(itemId: String) {
        val state = _uiState.value
        // ?: 是 Elvis 操作符: 左边为 null 用右边. 这里依次找两个商店列表.
        val item = state.shopEquipments.find { it.id == itemId }
            ?: state.shopConsumables.find { it.id == itemId }
        if (item == null) {
            L.w("VM", "buyItem: id=$itemId not found in shop")
            toast("VM", "That item is no longer available")
            return
        }
        val cost = item.goldCost
        val guild = state.gameState.guild
        if (guild.gold < cost) {
            toast("VM", "Not enough gold: ${item.name} costs $cost, you have ${guild.gold}")
            return
        }
        _uiState.update { st ->
            val g = st.gameState.guild
            st.copy(
                inventory = st.inventory + item,
                shopEquipments = st.shopEquipments.filterNot { it.id == itemId },   // filterNot = 反过滤
                shopConsumables = st.shopConsumables.filterNot { it.id == itemId },
                gameState = st.gameState.copy(guild = g.copy(gold = g.gold - cost))
            )
        }
        L.i("VM", "buyItem: purchased ${item.name} for $cost")
        toast("VM", "Purchased ${item.name}")
    }

    // 装备管理

    // 检查英雄是否满足物品的属性需求 + 职业限制.
    fun meetsRequirements(hero: Hero, item: Item): Boolean {
        // .all { 谓词 }: 列表所有元素都满足谓词时返回 true. 这里是检查每条属性需求都达到.
        val statsOk = item.statRequirements.all { (stat, req) ->
            (hero.baseStats[stat] ?: 0) >= req                              // Map[key] 取不到时返回 null, ?: 0 兜底
        }
        // ?.let {}: 链式调用. weaponType 为 null 整个表达式 null; 否则把它传入 let 的 lambda.
        // 最后 ?: true 表示"没有职业限制就视为通过".
        val classOk = item.weaponType?.requiredClass()?.let { it == hero.heroClass } ?: true
        return statsOk && classOk
    }

    // 装备物品: 从仓库取出 → 装到英雄身上 (旧装备退回仓库).
    fun equipItem(heroId: String, item: Item) {
        val state = _uiState.value
        val hero = state.heroes.find { it.id == heroId }
        if (hero == null) { L.w("VM", "equipItem: hero id=$heroId not found"); return }
        val slot = item.equipSlot
        if (slot == null) { L.w("VM", "equipItem: item ${item.name} has no equip slot"); return }
        if (!meetsRequirements(hero, item)) {
            val classReq = item.weaponType?.requiredClass()
            val reason = if (classReq != null && classReq != hero.heroClass) "${hero.name} is not a $classReq and cannot equip ${item.name}"
                         else "${hero.name} does not meet the stat requirements for ${item.name}"
            toast("VM", reason)
            return
        }
        _uiState.update { st ->
            val h = st.heroes.find { it.id == heroId } ?: return@update st
            val oldItem = h.equipment[slot]                                 // 该槽位原来装的(可能 null)
            val newHero = h.copy(equipment = h.equipment + (slot to item))  // Map + Pair = 新 Map (key 重复就覆盖)
            val newInventory = st.inventory.toMutableList()                 // 先转 mutable 才能 add/removeAt
            val idx = newInventory.indexOfFirst { it.id == item.id }
            if (idx >= 0) newInventory.removeAt(idx)                        // 仓库少一件该物品
            if (oldItem != null) newInventory.add(oldItem)                  // 旧装备退回仓库
            st.copy(
                heroes = st.heroes.map { if (it.id == heroId) newHero else it },
                inventory = newInventory
            )
        }
        L.i("VM", "equipItem: ${hero.name} equipped ${item.name} (slot $slot)")
    }

    // 卸下装备: 从英雄身上取下 → 退回仓库.
    fun unequipItem(heroId: String, slot: EquipSlot) {
        _uiState.update { state ->
            val hero = state.heroes.find { it.id == heroId } ?: return@update state
            val item = hero.equipment[slot] ?: return@update state          // 槽位空着没必要做事
            L.i("VM", "unequipItem: ${hero.name} unequipped ${item.name} (slot $slot)")
            val newHero = hero.copy(equipment = hero.equipment - slot)      // Map - key = 移除该 key 的新 Map
            state.copy(
                heroes = state.heroes.map { if (it.id == heroId) newHero else it },
                inventory = state.inventory + item
            )
        }
    }

    // 属性点分配

    // 消耗 1 属性点, 加 1 点指定属性.
    fun allocateStatPoint(heroId: String, stat: StatType) {
        _uiState.update { state ->
            val hero = state.heroes.find { it.id == heroId } ?: return@update state
            if (hero.unspentStatPoints <= 0) return@update state            // 没点直接 return 不变
            val newStats = hero.baseStats.toMutableMap()                    // 转 mutable 才能改
            newStats[stat] = (newStats[stat] ?: 0) + 1
            val newHero = hero.copy(
                baseStats = newStats,
                unspentStatPoints = hero.unspentStatPoints - 1
            )
            state.copy(heroes = state.heroes.map { if (it.id == heroId) newHero else it })
        }
    }

    // 消耗 1 属性点, 加 50 HP. 当前 HP 也跟着加 (避免上限提了实战还是亏).
    fun allocateHpPoint(heroId: String) {
        _uiState.update { state ->
            val hero = state.heroes.find { it.id == heroId } ?: return@update state
            if (hero.unspentStatPoints <= 0) return@update state
            val newHero = hero.copy(
                maxHp = hero.maxHp + 50,
                currentHp = hero.currentHp + 50,
                unspentStatPoints = hero.unspentStatPoints - 1
            )
            state.copy(heroes = state.heroes.map { if (it.id == heroId) newHero else it })
        }
    }

    // 技能树

    // 升级已有法术阶级. 升到目标 tier 需要英雄等级 ≥ 3*tier - 2 (T2=4, T3=7, T4=10). 消耗 1 技能点.
    fun upgradeSpellTier(heroId: String, templateId: String) {
        val hero = _uiState.value.heroes.find { it.id == heroId } ?: return
        val spell = hero.spells.find { it.templateId == templateId }
        if (spell == null) { L.w("VM", "upgradeSpellTier: ${hero.name} has not learned $templateId"); return }
        if (hero.unspentSkillPoints < 1) { toast("VM", "Not enough skill points"); return }
        if (spell.currentTier >= 4) { toast("VM", "This spell is already at max tier (★★★★)"); return }
        val nextTier = spell.currentTier + 1
        val reqLv = HeroSpell.requiredLevel(nextTier)
        if (hero.level < reqLv) { toast("VM", "Next tier requires level $reqLv (current ${hero.level})"); return }

        _uiState.update { state ->
            val h = state.heroes.find { it.id == heroId } ?: return@update state
            val newSpells = h.spells.map {
                if (it.templateId == templateId) it.copy(currentTier = nextTier) else it
            }
            val newHero = h.copy(spells = newSpells, unspentSkillPoints = h.unspentSkillPoints - 1)
            state.copy(heroes = state.heroes.map { if (it.id == heroId) newHero else it })
        }
        L.i("VM", "upgradeSpellTier: ${hero.name}'s $templateId raised to T$nextTier")
    }

    // 学习新法术 (需要法师/牧师职业, 跟法术类型匹配, 1 技能点).
    fun learnSpell(heroId: String, templateId: String) {
        val hero = _uiState.value.heroes.find { it.id == heroId } ?: return
        if (hero.unspentSkillPoints < 1) { toast("VM", "Not enough skill points"); return }
        if (hero.spells.any { it.templateId == templateId }) {              // .any { 谓词 }: 至少一个满足就 true
            toast("VM", "${hero.name} already knows this spell"); return
        }
        // try/catch: SpellRegistry.get 找不到 ID 会抛异常, 包起来防止 ViewModel 崩溃.
        val template = try { SpellRegistry.get(templateId) } catch (e: Exception) {
            L.e("VM", "learnSpell: SpellRegistry has no $templateId", e); return
        }
        val classOk = when (hero.heroClass) {
            HeroClass.MAGE -> template.type == SpellType.MAGIC
            HeroClass.CLERIC -> template.type == SpellType.MIRACLE
            else -> false                                                    // 战士/游侠不能学法术
        }
        if (!classOk) {
            toast("VM", "${hero.heroClass} cannot learn this spell (type mismatch)"); return
        }
        _uiState.update { state ->
            val h = state.heroes.find { it.id == heroId } ?: return@update state
            val newHero = h.copy(
                spells = h.spells + HeroSpell(templateId = templateId, currentTier = 1),
                unspentSkillPoints = h.unspentSkillPoints - 1
            )
            state.copy(heroes = state.heroes.map { if (it.id == heroId) newHero else it })
        }
        L.i("VM", "learnSpell: ${hero.name} learned $templateId")
    }

    // 消耗 1 属性点, 加 20 MP.
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

    // 卖物品

    // 卖出该 ID 的一件物品 (UI 推荐使用).
    fun sellOne(itemId: String) {
        _uiState.update { state ->
            val idx = state.inventory.indexOfFirst { it.id == itemId }
            if (idx < 0) return@update state
            val item = state.inventory[idx]
            // .toMutableList().apply { ... }: 拷贝成 mutable + apply 块里跑代码 + 返回该 mutable.
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

    // 卖出该 ID 的全部物品.
    fun sellAll(itemId: String) {
        _uiState.update { state ->
            val matching = state.inventory.filter { it.id == itemId }
            if (matching.isEmpty()) return@update state
            val totalGold = matching.first().sellPrice * matching.size      // 假设同 ID 物品卖价一样
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

    // 兼容旧调用.
    fun sellItem(item: Item) = sellOne(item.id)

    // 探险编队

    fun addToParty(heroId: String, position: Position) {
        val state = _uiState.value
        if (state.partySlots.size >= 4) { toast("VM", "Party is full (4 max)"); return }
        if (state.partySlots.any { it.heroId == heroId }) { toast("VM", "This hero is already in the party"); return }
        _uiState.update { it.copy(partySlots = it.partySlots + PartySlot(heroId, position)) }
        L.i("VM", "addToParty: $heroId joined party (position $position, now ${state.partySlots.size + 1}/4)")
    }

    fun removeFromParty(heroId: String) {
        _uiState.update { state ->
            state.copy(partySlots = state.partySlots.filter { it.heroId != heroId })
        }
    }

    fun setStrategy(strategy: StrategyType) {
        _uiState.update { it.copy(selectedStrategy = strategy) }
    }

    fun setSecondaryStrategy(strategy: SecondaryStrategyType) {
        _uiState.update { it.copy(selectedSecondaryStrategy = strategy) }
    }

    // 携带消耗品: +1 (按 itemId 增加, 可重复携带同种物品).
    fun addCarryItem(itemId: String) {
        val state = _uiState.value
        val current = state.carriedItemIds
        val max = state.gameState.guild.maxCarryItems
        if (current.size >= max) {
            toast("VM", "Carry slots full ($max), upgrade the tavern to expand")
            return
        }
        // 不能超过仓库中该物品的数量 (否则出战时取不出来).
        val ownedCount = state.inventory.count { it.id == itemId }
        val carriedCount = current.count { it == itemId }
        if (carriedCount >= ownedCount) {
            toast("VM", "All of this item in storage is already carried ($ownedCount)")
            return
        }
        _uiState.update { it.copy(carriedItemIds = it.carriedItemIds + itemId) }
    }

    // 出发前补给购买

    private val SUPPLY_PER_GOLD = 5                                         // 1 金币 = 5 补给

    // 花 1 金币加 5 补给到本次出战.
    fun buyExtraSupply() {
        if (_uiState.value.gameState.guild.gold < 1) {
            toast("VM", "Not enough gold to buy supplies")
            return
        }
        _uiState.update { state ->
            val guild = state.gameState.guild
            state.copy(
                gameState = state.gameState.copy(guild = guild.copy(gold = guild.gold - 1)),
                purchasedExtraSupply = state.purchasedExtraSupply + SUPPLY_PER_GOLD
            )
        }
    }

    // 退还 5 补给, 退 1 金币.
    fun refundExtraSupply() {
        _uiState.update { state ->
            if (state.purchasedExtraSupply < SUPPLY_PER_GOLD) return@update state
            val guild = state.gameState.guild
            state.copy(
                gameState = state.gameState.copy(guild = guild.copy(gold = guild.gold + 1)),
                purchasedExtraSupply = state.purchasedExtraSupply - SUPPLY_PER_GOLD
            )
        }
    }

    fun removeCarryItem(itemId: String) {
        _uiState.update { state ->
            val current = state.carriedItemIds.toMutableList()
            val idx = current.indexOf(itemId)
            if (idx >= 0) current.removeAt(idx)                             // 只移除一个, 不是全部
            state.copy(carriedItemIds = current)
        }
    }

    // 探险运行

    fun startExpedition() {
        val state = _uiState.value
        if (state.partySlots.isEmpty()) {
            toast("VM", "Add at least 1 hero to the party before departing")
            return
        }
        if (state.expedition?.isExploring == true) {                        // ?.: 链式调用, 中间为 null 整个表达式 null
            L.w("VM", "startExpedition: an expedition is already running, ignoring duplicate tap")
            return
        }

        // 从仓库中取出携带的消耗品 (防止仓库里没货, 比如玩家临走删除了).
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

        // 实际出战补给 = 酒馆默认 + 战术界面买的额外.
        val totalSupply = state.gameState.guild.defaultExpeditionSupply + state.purchasedExtraSupply
        // 复用上次冒险的楼层 (撤退保留进度); 若无则从 1 层开始.
        val savedLayer = state.currentFloor?.layer ?: 1
        val session = ExpeditionSession(
            dungeonId = "main_dungeon",
            party = state.partySlots,
            strategy = state.selectedStrategy,
            secondaryStrategy = state.selectedSecondaryStrategy,
            currentLayer = savedLayer,
            currentSupply = totalSupply,
            maxSupply = totalSupply,
            carriedItems = actualCarried,
            isExploring = true
        )

        // 出征队员: 状态置 DEPLOYED, 同时满血满蓝 (上一轮撤退保留下来的伤害此刻才修复).
        // currentFloor 不清空 — 让 ExplorationEngine 复用上次冒险的地图 (清空格子/visited 标记/位置 全部保留).
        val partyIds = state.partySlots.map { it.heroId }.toSet()           // toSet() 让后面 in 查询 O(1)
        _uiState.update {
            it.copy(
                expedition = session,
                inventory = newInventory,
                purchasedExtraSupply = 0,                                   // 用完即清, 下次出战重新攒
                heroes = it.heroes.map { h ->
                    if (h.id in partyIds) h.copy(
                        status = HeroStatus.DEPLOYED,
                        currentHp = h.maxHp,
                        currentMp = h.maxMp
                    ) else h
                }
            )
        }

        L.i("VM", "startExpedition: party=${state.partySlots.size}, supply=$totalSupply, layer=$savedLayer, " +
            "strategy=${state.selectedStrategy}/${state.selectedSecondaryStrategy}")
        // 启动协程驱动探索 — 包 try/catch 防止引擎崩了让 UI 卡死.
        viewModelScope.launch {
            try {
                explorationEngine.run(session, _uiState)                    // 阻塞到探险结束
            } catch (e: Exception) {
                L.e("VM", "ExplorationEngine threw — force-ending expedition", e)
                toast("VM", "Expedition error, forced to return (see logs)")
                // 让 UI 跳出探险界面 — 把 expedition 标记为已结束, 把 hero 状态从 DEPLOYED 还原.
                _uiState.update { st ->
                    val ended = st.expedition?.copy(isExploring = false)
                    val partyIds = session.party.map { it.heroId }.toSet()
                    val resetHeroes = st.heroes.map { h ->
                        if (h.id in partyIds) h.copy(status = HeroStatus.IDLE) else h
                    }
                    st.copy(expedition = ended, heroes = resetHeroes)
                }
            }
        }
    }

    // 离线补偿 (挂机)

    /**
     * App 退到后台时调用 (MainApp 的 ON_STOP). 记录当前时间戳并立即存盘 ——
     * 进程随后可能被系统回收, 靠这个时间戳在重新打开时折算探险的离线进度.
     */
    fun onAppBackground() {
        if (!_uiState.value.gameState.hasStarted) return
        _uiState.update {
            it.copy(gameState = it.gameState.copy(lastActiveAt = System.currentTimeMillis()))
        }
        // 不等去抖动, 立即存一次
        viewModelScope.launch { saveRepo.save(_uiState.value) }
    }

    /**
     * 进程恢复 —— 重新打开 App 时调 (init 加载存档后).
     *
     * 两种情形:
     *   1. 正常退后台再回来 → onAppBackground 写过 lastActiveAt → 做离线补偿快进, 再续前台
     *   2. 进程被强制 kill (force-stop / 内存压力) → onAppBackground 没机会跑 → lastActiveAt=0
     *      → 跳过补偿, 但仍需续起前台 run() 让探险继续, 否则会卡在起点
     *
     * 核心契约: 只要存档里 expedition.isExploring=true, 就必须有协程在跑它. 是否做离线补偿
     * 是独立的次要判断, 不能因此放弃前台续跑.
     */
    private fun checkOfflineCatchUp() {
        val state = _uiState.value
        val session = state.expedition
        if (session == null || !session.isExploring) return     // 不在探险中, 无需任何操作

        // 计算可补偿的步数. 若 lastActiveAt=0 或离开太短, steps=0 → 跳过补偿, 直接前台续跑
        val lastActive = state.gameState.lastActiveAt
        val elapsed = if (lastActive == 0L) 0L
                      else (System.currentTimeMillis() - lastActive).coerceAtMost(MAX_OFFLINE_MS)
        val steps = (elapsed / OFFLINE_MS_PER_STEP).toInt()

        viewModelScope.launch {
            // 离线补偿 (仅在有有效时间戳且步数 > 0 时执行)
            if (steps > 0) {
                L.i("VM", "offline catch-up: away ${elapsed / 1000}s → $steps step(s)")
                val roomsBefore = _uiState.value.gameState.roomsExplored
                // 快进: 关闭 delay, 限制步数上限
                explorationEngine.run(session, _uiState, stepDelayEnabled = false, maxSteps = steps)
                val gained = _uiState.value.gameState.roomsExplored - roomsBefore
                // 消费掉这段离线时长, 防止极端情况下重复补偿
                _uiState.update {
                    it.copy(gameState = it.gameState.copy(lastActiveAt = System.currentTimeMillis()))
                }
                val stillExploring = _uiState.value.expedition?.isExploring == true
                toast("Offline", if (stillExploring)
                    "Welcome back — explored $gained room(s) while away, expedition continues"
                else
                    "Welcome back — expedition finished while away, explored $gained room(s)")
            } else {
                L.i("VM", "no offline catch-up needed (lastActive=$lastActive, elapsed=${elapsed}ms)")
            }

            // 续跑前台 (核心修复点)
            // 只要还在探险中, 就启动一份前台节奏的 run(). 这一段处理:
            //   - 进程被 force-stop 后重启 (lastActiveAt=0, 上面跳过补偿, 这里接管)
            //   - 离线补偿后步数额度用尽但探险未结束 (上面已快进, 这里以前台节奏继续)
            val ongoing = _uiState.value.expedition
            if (ongoing?.isExploring == true) {
                try {
                    explorationEngine.run(ongoing, _uiState)
                } catch (e: Exception) {
                    L.e("VM", "resume run() failed", e)
                }
            }
        }
    }
}

/**
 * UI 状态聚合体, 所有屏幕从这里读数据. 默认值都给空, 这样 GameUiState() 就是"空白存档".
 */
data class GameUiState(
    val gameState: GameState = GameState(),                                 //数据

    // 英雄
    val heroes: List<Hero> = emptyList(),                                   // 已招募的
    val recruitableHeroes: List<Hero> = emptyList(),                        // 酒馆里待招募的

    // 物品
    val inventory: List<Item> = emptyList(),                                // 仓库
    val shopEquipments: List<Item> = emptyList(),                           // 铁匠铺货架
    val shopConsumables: List<Item> = emptyList(),                          // 道具店货架

    // 任务
    val quests: List<Quest> = emptyList(),

    // 地下城
    val currentFloor: DungeonFloor? = null,                                 // 当前楼层 (探险中才有)
    val expedition: ExpeditionSession? = null,                              // 当前探险会话

    // 编队临时状态 (玩家在战术界面调好的, 出发前不入存档)
    val partySlots: List<PartySlot> = emptyList(),
    val selectedStrategy: StrategyType = StrategyType.BALANCED,
    val selectedSecondaryStrategy: SecondaryStrategyType = SecondaryStrategyType.EXPLORE,
    val carriedItemIds: List<String> = emptyList(),
    // 出发前在战术界面买的额外补给 (5 补给/1 金币的倍数). 出发后清零.
    val purchasedExtraSupply: Int = 0
)
