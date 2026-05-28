package com.gaotong.idledungeon.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gaotong.idledungeon.model.EquipSlot
import com.gaotong.idledungeon.model.HeroClass
import com.gaotong.idledungeon.model.HeroSpell
import com.gaotong.idledungeon.model.HeroStatus
import com.gaotong.idledungeon.model.SecondaryStrategyType
import com.gaotong.idledungeon.model.StatType
import com.gaotong.idledungeon.model.StrategyType
import com.gaotong.idledungeon.model.WeaponType

// 一行一个英雄. Map/List 走 TypeConverter 转 JSON.
// equipmentIds 只存物品 ID, Item 通过 ItemRegistry 反查, 避免把静态数据冗余写库.
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
    val spells: List<HeroSpell>,
    val proficiencyXp: Map<WeaponType, Int>,
    val equipmentIds: Map<EquipSlot, String>,
    val status: HeroStatus
)

// 单行表 (id 恒为 1), 装其它所有持久化字段. quests 没存, 是写死的模板, 启动时挂回.
@Entity(tableName = "snapshot")
data class SnapshotEntity(
    @PrimaryKey val id: Int = 1,

    // GameState
    val hasStarted: Boolean,
    val playerName: String,
    val playerAvatarUri: String?,
    val guild: com.gaotong.idledungeon.model.PlayerGuild,
    val deepestFloorReached: Int,
    val mainQuestIndex: Int,
    val roomsExplored: Int,
    val bossesDefeated: Set<Int>,
    val foundItemIds: Set<String>,
    val hasUnreadQuestUpdate: Boolean,
    val monsterKills: Map<String, Int>,
    val lastShopRefreshDayKey: Int,
    val f2ChestPity: Int,
    val nextRecruitNumber: Int,
    val lastActiveAt: Long,

    // 仓库 / 队伍 / 战术
    val inventoryItemIds: List<String>,
    val partySlots: List<com.gaotong.idledungeon.model.PartySlot>,
    val selectedStrategy: StrategyType,
    val selectedSecondaryStrategy: SecondaryStrategyType,
    val carriedItemIds: List<String>,
    val purchasedExtraSupply: Int,

    // 商店 / 招募 (每日刷新; 持久化以避免重启后同一天的商店内容被打乱)
    val recruitableHeroes: List<com.gaotong.idledungeon.model.Hero>,
    val shopEquipmentIds: List<String>,
    val shopConsumableIds: List<String>,

    // 远征中状态 (可空)
    val currentFloor: com.gaotong.idledungeon.model.DungeonFloor?,
    val expedition: com.gaotong.idledungeon.model.ExpeditionSession?
)
