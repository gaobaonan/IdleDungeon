package com.example.idledungeon.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.idledungeon.model.EquipSlot
import com.example.idledungeon.model.HeroClass
import com.example.idledungeon.model.HeroSpell
import com.example.idledungeon.model.HeroStatus
import com.example.idledungeon.model.SecondaryTactic
import com.example.idledungeon.model.StatType
import com.example.idledungeon.model.TacticType
import com.example.idledungeon.model.WeaponType

/**
 * 一行 = 一个英雄. 嵌套结构 (Map / List) 通过 TypeConverter 存为 JSON 字符串.
 *
 * equipmentIds 只存物品 ID, 完整 Item 通过 ItemRegistry.get(id) 反查 — 避免
 * 把整张物品表的静态数据冗余写入存档.
 */
@Entity(tableName = "heroes")
data class HeroEntity(
    @PrimaryKey val id: String,
    val name: String,
    val portraitUri: String?,
    val heroClass: HeroClass,
    val level: Int,
    val exp: Int,
    val currentHp: Int,
    val maxHp: Int,
    val currentMp: Int,
    val maxMp: Int,
    val baseStats: Map<StatType, Int>,
    val unspentStatPoints: Int,
    val unspentSkillPoints: Int,
    val traits: List<String>,
    val spells: List<HeroSpell>,
    val proficiencyXp: Map<WeaponType, Int>,
    val equipmentIds: Map<EquipSlot, String>,
    val status: HeroStatus
)

/**
 * 单行存档表 (id 永远为 1) — 装载 GameUiState 中除英雄以外的所有持久化字段.
 *
 * 没存的字段:
 *   - quests:           主线任务模板, 写死在 GameViewModel.MAIN_QUESTS, 启动时挂回
 *   - recruitableHeroes / shopEquipments / shopConsumables:
 *                       每日刷新, 由 lastShopRefreshDayKey 派生
 *
 * currentFloor / expedition 可空 — 未在远征中时存 NULL.
 */
@Entity(tableName = "snapshot")
data class SnapshotEntity(
    @PrimaryKey val id: Int = 1,

    // ── GameState ──
    val hasStarted: Boolean,
    val playerName: String,
    val playerAvatarUri: String?,
    val guild: com.example.idledungeon.model.PlayerGuild,
    val deepestFloorReached: Int,
    val mainQuestIndex: Int,
    val roomsExplored: Int,
    val bossesDefeated: Set<Int>,
    val foundItemIds: Set<String>,
    val hasUnreadQuestUpdate: Boolean,
    val monsterKills: Map<String, Int>,
    val lastShopRefreshDayKey: Int,
    val f2ChestPity: Int,

    // ── 仓库 / 队伍 / 战术 ──
    val inventoryItemIds: List<String>,
    val partySlots: List<com.example.idledungeon.model.PartySlot>,
    val selectedTactic: TacticType,
    val selectedSecondaryTactic: SecondaryTactic,
    val carriedItemIds: List<String>,
    val purchasedExtraSupply: Int,

    // ── 远征中状态 (可空) ──
    val currentFloor: com.example.idledungeon.model.DungeonFloor?,
    val expedition: com.example.idledungeon.model.ExpeditionSession?
)
