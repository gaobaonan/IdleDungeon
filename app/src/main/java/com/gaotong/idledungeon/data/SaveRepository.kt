package com.gaotong.idledungeon.data

import com.gaotong.idledungeon.data.database.GameDatabase
import com.gaotong.idledungeon.data.database.HeroEntity
import com.gaotong.idledungeon.data.database.SnapshotEntity
import com.gaotong.idledungeon.model.GameState
import com.gaotong.idledungeon.model.Hero
import com.gaotong.idledungeon.util.L
import com.gaotong.idledungeon.viewmodel.GameUiState
import com.gaotong.idledungeon.viewmodel.GameViewModel

// 抽成 interface 主要是为了单测时塞 fake. 将来想加云存档也只要写一个新实现.
// TODO 云存档: 加一个 RemoteSaveRepository 然后 AppModule 里换一下 @Provides
interface SaveRepository {
    suspend fun load(): GameUiState?
    suspend fun save(state: GameUiState)
    suspend fun clear()
}

/**
 * 把 GameUiState 拆成 Room 实体写入, 或反过来拼回来.
 * heroes 一行一英雄, snapshot 单行装其它所有, 装备/物品只存 ID 走 ItemRegistry 反查.
 */
class RoomSaveRepository(private val db: GameDatabase) : SaveRepository {

    private val tag = "Save"

    /**
     * 加载存档. 三种返回:
     *   - null: snapshot 表为空 (从未开始游戏) → ViewModel 用默认 state
     *   - null: 读库抛异常 (DB 损坏 / Gson 反序列化失败) → 视为"无存档", 让玩家走新游戏入口
     *   - GameUiState: 正常恢复
     *
     * 任何异常都不会向上抛 — 不能因为存档坏了让 App 启动崩溃.
     */
    override suspend fun load(): GameUiState? {
        return try {
            val snap = db.snapshotDao().get()
            if (snap == null) {
                L.i(tag, "load: snapshot 表为空, 视为新存档")
                return null
            }
            val heroes = db.heroDao().getAll().map { it.toHero() }
            L.i(tag, "load: 读出 ${heroes.size} 个英雄, 仓库 ${snap.inventoryItemIds.size} 件物品")

            val gameState = GameState(
                hasStarted = snap.hasStarted,
                playerName = snap.playerName,
                playerAvatarUri = snap.playerAvatarUri,
                guild = snap.guild,
                deepestFloorReached = snap.deepestFloorReached,
                mainQuestIndex = snap.mainQuestIndex,
                roomsExplored = snap.roomsExplored,
                bossesDefeated = snap.bossesDefeated,
                foundItemIds = snap.foundItemIds,
                hasUnreadQuestUpdate = snap.hasUnreadQuestUpdate,
                monsterKills = snap.monsterKills,
                lastShopRefreshDayKey = snap.lastShopRefreshDayKey,
                f2ChestPity = snap.f2ChestPity,
                nextRecruitNumber = snap.nextRecruitNumber,
                lastActiveAt = snap.lastActiveAt
            )

            // 仓库道具 ID → 实体 Item; 注册表里查不到的就跳过 (防御性: 万一以后删过物品)
            val inventory = snap.inventoryItemIds.mapNotNull { id ->
                val item = runCatching { ItemRegistry.get(id) }.getOrNull()
                if (item == null) L.w(tag, "load: 仓库物品 ID '$id' 在 ItemRegistry 中不存在, 跳过")
                item
            }
            // 商店 ID → 实体 Item; 同样防御性跳过失效 ID
            val shopEquipments = snap.shopEquipmentIds.mapNotNull { id ->
                runCatching { ItemRegistry.get(id) }.getOrNull()
            }
            val shopConsumables = snap.shopConsumableIds.mapNotNull { id ->
                runCatching { ItemRegistry.get(id) }.getOrNull()
            }

            GameUiState(
                gameState = gameState,
                heroes = heroes,
                recruitableHeroes = snap.recruitableHeroes,
                inventory = inventory,
                shopEquipments = shopEquipments,
                shopConsumables = shopConsumables,
                quests = GameViewModel.MAIN_QUESTS,
                currentFloor = snap.currentFloor,
                expedition = snap.expedition,
                partySlots = snap.partySlots,
                selectedStrategy = snap.selectedStrategy,
                selectedSecondaryStrategy = snap.selectedSecondaryStrategy,
                carriedItemIds = snap.carriedItemIds,
                purchasedExtraSupply = snap.purchasedExtraSupply
            )
        } catch (e: Exception) {
            L.e(tag, "load: 读取存档失败 — 放弃读取, 走新游戏路径", e)
            null
        }
    }

    /** 写一份完整快照. 英雄表整体替换, snapshot 表 upsert */
    override suspend fun save(state: GameUiState) {
        try {
            db.heroDao().replaceAll(state.heroes.map { it.toEntity() })
            db.snapshotDao().upsert(state.toSnapshot())
            L.d(tag, "save: 已落盘 (英雄 ${state.heroes.size}, 仓库 ${state.inventory.size})")
        } catch (e: Exception) {
            // 不向上抛 — 自动保存失败不应该崩溃 UI; 下次 state 变化会再次尝试
            L.e(tag, "save: 写库失败, 已忽略本次", e)
        }
    }

    /** 抹掉所有数据 — 新游戏前调用. */
    override suspend fun clear() {
        try {
            db.heroDao().clear()
            db.snapshotDao().clear()
            L.i(tag, "clear: 存档已清空")
        } catch (e: Exception) {
            L.e(tag, "clear: 清库失败", e)
        }
    }

    // ─
    // 映射: model ↔ entity
    // ─

    private fun Hero.toEntity(): HeroEntity = HeroEntity(
        id = id,
        name = name,
        portraitUri = portraitUri,
        heroClass = heroClass,
        level = level,
        exp = exp,
        currentHp = currentHp,
        maxHp = maxHp,
        currentMp = currentMp,
        maxMp = maxMp,
        baseStats = baseStats,
        unspentStatPoints = unspentStatPoints,
        unspentSkillPoints = unspentSkillPoints,
        spells = spells,
        proficiencyXp = proficiencyXp,
        equipmentIds = equipment.mapValues { it.value.id },
        status = status
    )

    private fun HeroEntity.toHero(): Hero = Hero(
        id = id,
        name = name,
        portraitUri = portraitUri,
        heroClass = heroClass,
        level = level,
        exp = exp,
        currentHp = currentHp,
        maxHp = maxHp,
        currentMp = currentMp,
        maxMp = maxMp,
        baseStats = baseStats,
        unspentStatPoints = unspentStatPoints,
        unspentSkillPoints = unspentSkillPoints,
        spells = spells,
        proficiencyXp = proficiencyXp,
        // 装备 ID → 实例; 注册表丢失时跳过
        equipment = equipmentIds.mapNotNull { (slot, id) ->
            runCatching { ItemRegistry.get(id) }.getOrNull()?.let { slot to it }
        }.toMap(),
        status = status
    )

    private fun GameUiState.toSnapshot(): SnapshotEntity = SnapshotEntity(
        id = 1,
        hasStarted = gameState.hasStarted,
        playerName = gameState.playerName,
        playerAvatarUri = gameState.playerAvatarUri,
        guild = gameState.guild,
        deepestFloorReached = gameState.deepestFloorReached,
        mainQuestIndex = gameState.mainQuestIndex,
        roomsExplored = gameState.roomsExplored,
        bossesDefeated = gameState.bossesDefeated,
        foundItemIds = gameState.foundItemIds,
        hasUnreadQuestUpdate = gameState.hasUnreadQuestUpdate,
        monsterKills = gameState.monsterKills,
        lastShopRefreshDayKey = gameState.lastShopRefreshDayKey,
        f2ChestPity = gameState.f2ChestPity,
        nextRecruitNumber = gameState.nextRecruitNumber,
        lastActiveAt = gameState.lastActiveAt,
        inventoryItemIds = inventory.map { it.id },
        partySlots = partySlots,
        selectedStrategy = selectedStrategy,
        selectedSecondaryStrategy = selectedSecondaryStrategy,
        carriedItemIds = carriedItemIds,
        purchasedExtraSupply = purchasedExtraSupply,
        recruitableHeroes = recruitableHeroes,
        shopEquipmentIds = shopEquipments.map { it.id },
        shopConsumableIds = shopConsumables.map { it.id },
        currentFloor = currentFloor,
        expedition = expedition
    )
}
