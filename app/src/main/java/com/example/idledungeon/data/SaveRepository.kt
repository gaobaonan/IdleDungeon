package com.example.idledungeon.data

import com.example.idledungeon.data.database.GameDatabase
import com.example.idledungeon.data.database.HeroEntity
import com.example.idledungeon.data.database.SnapshotEntity
import com.example.idledungeon.model.GameState
import com.example.idledungeon.model.Hero
import com.example.idledungeon.viewmodel.GameUiState
import com.example.idledungeon.viewmodel.GameViewModel

/**
 * 高层存档仓库 — 把 [GameUiState] 拆成 Room 实体写入, 或反过来从 DB 拼回 [GameUiState].
 *
 * 设计取舍:
 *   - 英雄 (heroes) 表: 一行一英雄, 便于将来按 id 查/改单条
 *   - snapshot 表: 单行 (id=1), 装其余所有状态. 因为这些字段几乎总是一起读写,
 *     拆表反而增加事务复杂度
 *   - 商店刷新列表 / 任务模板 不入库, 启动时按规则重建
 *   - 装备只存 ID (Map<EquipSlot, String>), 通过 [ItemRegistry] 反查; 仓库
 *     (List<Item>) 同理只存 ID
 */
class SaveRepository(private val db: GameDatabase) {

    /** 加载存档. 如果还没开始过游戏 (snapshot 表为空) 返回 null */
    suspend fun load(): GameUiState? {
        val snap = db.snapshotDao().get() ?: return null
        val heroes = db.heroDao().getAll().map { it.toHero() }

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
            f2ChestPity = snap.f2ChestPity
        )

        // 仓库道具 ID → 实体 Item; 注册表里查不到的就跳过 (防御性: 万一以后删过物品)
        val inventory = snap.inventoryItemIds.mapNotNull { id ->
            runCatching { ItemRegistry.get(id) }.getOrNull()
        }

        return GameUiState(
            gameState = gameState,
            heroes = heroes,
            inventory = inventory,
            quests = GameViewModel.MAIN_QUESTS,
            currentFloor = snap.currentFloor,
            expedition = snap.expedition,
            partySlots = snap.partySlots,
            selectedTactic = snap.selectedTactic,
            selectedSecondaryTactic = snap.selectedSecondaryTactic,
            carriedItemIds = snap.carriedItemIds,
            purchasedExtraSupply = snap.purchasedExtraSupply
            // recruitableHeroes / shopEquipments / shopConsumables 由 ViewModel 加载后调
            // refreshShops 重建, 不在此处设置
        )
    }

    /** 写一份完整快照. 英雄表整体替换, snapshot 表 upsert */
    suspend fun save(state: GameUiState) {
        db.heroDao().replaceAll(state.heroes.map { it.toEntity() })
        db.snapshotDao().upsert(state.toSnapshot())
    }

    /** 抹掉所有数据 — 新游戏前调用. */
    suspend fun clear() {
        db.heroDao().clear()
        db.snapshotDao().clear()
    }

    // ───────────────────────────────────────
    // 映射: model ↔ entity
    // ───────────────────────────────────────

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
        traits = traits,
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
        traits = traits,
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
        inventoryItemIds = inventory.map { it.id },
        partySlots = partySlots,
        selectedTactic = selectedTactic,
        selectedSecondaryTactic = selectedSecondaryTactic,
        carriedItemIds = carriedItemIds,
        purchasedExtraSupply = purchasedExtraSupply,
        currentFloor = currentFloor,
        expedition = expedition
    )
}
