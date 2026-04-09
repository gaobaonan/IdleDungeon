package com.example.idledungeon.model

// ==========================================
// 1. 枚举类 (Enums)
// ==========================================

/** 英雄四维属性 */
enum class StatType { STR, DEX, INT, FTH }

/** 武器/护甲大类 (用于熟练度系统) */
enum class WeaponType { SWORD, SPEAR, DAGGER, STAFF, BOW, HEAVY_ARMOR, LIGHT_ARMOR }

/** 装备槽位 */
enum class EquipSlot { WEAPON, ARMOR, ACCESSORY }

/** 物品大类 */
enum class ItemType { EQUIPMENT, CONSUMABLE, MATERIAL }

/** 物品品质 */
enum class Rarity { COMMON, UNCOMMON, RARE, EPIC }

/** 英雄职业 (不限制装备，只提供熟练度加成) */
enum class HeroClass { WARRIOR, MAGE, RANGER, CLERIC, NOVICE }

/** 英雄当前状态 */
enum class HeroStatus { IDLE, DEPLOYED, INJURED }

/** 队伍站位 (影响挨打概率) */
enum class Position { FRONT, BACK }

/** 战术倾向 */
enum class TacticType {
    SAFE,       // 稳扎稳打 (HP<50% 自动撤退)
    BALANCED,   // 平衡
    AGGRESSIVE  // 孤注一掷 (死战不退)
}

/** 地牢节点类型 */
enum class NodeType {
    WALL,       // 墙壁 (不可通行)
    CORRIDOR,   // 走廊
    ROOM,       // 普通房间
    BOSS_ROOM,  // Boss房间
    STAIRS      // 楼梯
}

/** 节点事件 */
enum class NodeEvent {
    NONE,           // 无事发生
    TRAP,           // 陷阱
    ENCOUNTER,      // 遭遇怪物
    LOOT,           // 发现战利品
    BOSS_FIGHT,     // Boss战
    STAIRS_UP,      // 上楼
    STAIRS_DOWN     // 下楼
}

/** 怪物技能效果 */
enum class SkillEffect {
    HEAVY_STRIKE, // 重击 (高倍率伤害)
    HEAL,         // 治疗
    POISON,       // 中毒 (持续扣血)
    STUN          // 击晕 (跳过一回合)
}

// ==========================================
// 2. 特质 / 物品 / 英雄
// ==========================================

/** 特质 - 类似暗黑地牢的好/坏特性 */
data class Trait(
    val id: String,
    val name: String,
    val description: String,
    val isPositive: Boolean
)

/** 物品与装备 - 魂系属性需求 */
data class Item(
    val id: String,
    val name: String,
    val description: String = "",
    val type: ItemType,
    val rarity: Rarity = Rarity.COMMON,
    val weaponType: WeaponType? = null,
    val equipSlot: EquipSlot? = null,
    val baseValue: Int,                                // 攻击力 / 防御力
    val sellPrice: Int = baseValue / 2,                // 售价
    val statRequirements: Map<StatType, Int> = emptyMap(),
    val statBonuses: Map<StatType, Int> = emptyMap()
)

/** 英雄 - 游戏核心资产 */
data class Hero(
    val id: String,
    val name: String,
    val portraitUri: String?,
    val heroClass: HeroClass,
    val level: Int = 1,
    val exp: Int = 0,
    val currentHp: Int,
    val maxHp: Int,
    val currentMp: Int = 0,
    val maxMp: Int = 0,
    val baseStats: Map<StatType, Int>,          // 四维基础值
    val traits: List<Trait> = emptyList(),
    val proficiencies: Map<WeaponType, Int> = emptyMap(),
    val equipment: Map<EquipSlot, Item> = emptyMap(),
    val status: HeroStatus = HeroStatus.IDLE
) {
    val isAlive: Boolean get() = currentHp > 0

    /** 综合攻击力 = 基础属性 + 武器 */
    val attack: Int get() {
        val str = baseStats[StatType.STR] ?: 0
        val dex = baseStats[StatType.DEX] ?: 0
        val weaponPower = equipment[EquipSlot.WEAPON]?.baseValue ?: 0
        return str + dex / 2 + weaponPower
    }

    /** 综合防御力 = 基础属性 + 护甲 */
    val defense: Int get() {
        val str = baseStats[StatType.STR] ?: 0
        val dex = baseStats[StatType.DEX] ?: 0
        val armorPower = equipment[EquipSlot.ARMOR]?.baseValue ?: 0
        return str / 2 + dex / 3 + armorPower
    }
}

// ==========================================
// 3. 城镇 / 公会 / 任务
// ==========================================

/** 玩家公会状态 */
data class PlayerGuild(
    val guildRank: String = "F",
    val gold: Int = 0,
    val currentSupplies: Int = 10000,    // 测试用大补给
    val maxSupplies: Int = 10000,       // 公会等级决定上限
    val maxCarryItems: Int = 10,        // 探险可携带道具上限
    val guildLevel: Int = 1,
    val tavernLevel: Int = 1,
    val blacksmithLevel: Int = 1,
    val itemShopLevel: Int = 1
)

/** 公会主线任务 */
data class Quest(
    val id: String,
    val title: String,
    val description: String,
    val requiredFloor: Int = 0,         // 需要到达的楼层
    val requiredMonsterId: String? = null, // 需要击杀的怪物
    val requiredKillCount: Int = 0,
    val goldReward: Int = 0,
    val rankUpOnComplete: Boolean = false, // 完成后是否升公会等级
    val isCompleted: Boolean = false
)

// ==========================================
// 4. 地牢地图系统
// ==========================================

/** 地牢节点 - 二维数组中的一个格子 */
data class DungeonNode(
    val type: NodeType,
    val event: NodeEvent = NodeEvent.NONE,
    val monsterIds: List<String> = emptyList(),
    val lootTable: Map<String, Int> = emptyMap(),
    val explored: Boolean = false,
    val label: String = ""              // [DEBUG] 房间编号，发布前删除
)

/** 地牢楼层 - 一层 = 一个二维网格 */
data class DungeonFloor(
    val layer: Int,
    val width: Int,
    val height: Int,
    val grid: List<List<DungeonNode>>   // grid[y][x] 行优先
)

/** 地牢 */
data class Dungeon(
    val id: String,
    val name: String,
    val recommendedLevel: Int,
    val floors: List<DungeonFloor>
)

// ==========================================
// 5. 探险与编队
// ==========================================

/** 队伍槽位 (英雄ID + 前/后排) */
data class PartySlot(
    val heroId: String,
    val position: Position
)

/** 当前探险状态 - 存放在 ViewModel 中实时演算 */
data class ExpeditionSession(
    val dungeonId: String,
    val currentLayer: Int = 1,
    val posX: Int = 0,
    val posY: Int = 0,
    val party: List<PartySlot>,                 // 出战编队 (1-4人)
    val tactic: TacticType = TacticType.BALANCED,
    val currentSupply: Int,
    val carriedItems: List<String> = emptyList(), // 携带的消耗品ID
    val backpack: List<String> = emptyList(),     // 探索中拾取的战利品ID
    val isExploring: Boolean = false,
    val combatLog: List<String> = emptyList()
)

// ==========================================
// 6. 怪物与战斗
// ==========================================

/** 怪物技能 */
data class MonsterSkill(
    val id: String,
    val name: String,
    val description: String,
    val effectType: SkillEffect,
    val powerMultiplier: Float,     // 效果倍率 (1.5 = 150%攻击力)
    val triggerChance: Int          // 触发概率 (0-100)
)

/** 怪物 (模板，从 MonsterRegistry 获取；战斗时 copy() 出实例) */
data class Monster(
    val id: String,
    val name: String,
    val level: Int,
    val currentHp: Int,
    val maxHp: Int,
    val attack: Int,
    val defense: Int,
    val skills: List<MonsterSkill> = emptyList(),
    val dropChances: Map<String, Int> = emptyMap()
) {
    val isAlive: Boolean get() = currentHp > 0
}

// ==========================================
// 7. 图鉴与存档
// ==========================================

/** 怪物图鉴条目 */
data class MonsterCodexEntry(
    val monsterId: String,
    val unlocked: Boolean = false,
    val killCount: Int = 0
)

/** 游戏存档顶层状态 (用于序列化 / Room 持久化) */
data class GameState(
    val hasStarted: Boolean = false,
    val playerName: String = "Guild Master",
    val playerAvatarUri: String? = null,
    val guild: PlayerGuild = PlayerGuild(),
    val deepestFloorReached: Int = 1,
    val dayCount: Int = 1               // 游戏内天数 (控制商店刷新)
)