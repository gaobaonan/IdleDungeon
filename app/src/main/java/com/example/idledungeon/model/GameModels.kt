package com.example.idledungeon.model

// ==========================================
// 1. 枚举类 (Enums)
// ==========================================

/** 英雄四维属性 */
enum class StatType { STR, DEX, INT, FTH }

/** 武器/护甲大类 (用于熟练度系统) */
enum class WeaponType { SWORD, SPEAR, DAGGER, STAFF, BOW, HOLY_BELL, HEAVY_ARMOR, LIGHT_ARMOR }

/**
 * 武器类别的职业限制。返回 null 表示任意职业可用。
 * - 远程武器 (BOW) 仅限游侠
 * - 法杖 (STAFF) 仅限法师
 * - 圣铃 (HOLY_BELL) 仅限圣职者
 */
fun WeaponType.requiredClass(): HeroClass? = when (this) {
    WeaponType.BOW -> HeroClass.RANGER
    WeaponType.STAFF -> HeroClass.MAGE
    WeaponType.HOLY_BELL -> HeroClass.CLERIC
    else -> null
}

/** 武器/护甲分类的本地化名称 (用于 UI / 日志) */
fun WeaponType.displayName(): String = when (this) {
    WeaponType.SWORD -> "Sword"
    WeaponType.SPEAR -> "Spear"
    WeaponType.DAGGER -> "Dagger"
    WeaponType.STAFF -> "Staff"
    WeaponType.BOW -> "Bow"
    WeaponType.HOLY_BELL -> "Holy Bell"         //魂3的圣铃，牧师专武
    WeaponType.HEAVY_ARMOR -> "Heavy Armor"
    WeaponType.LIGHT_ARMOR -> "Light Armor"
}

/** 装备槽位 */
enum class EquipSlot { WEAPON, ARMOR, ACCESSORY }

/** 物品大类 */
enum class ItemType { EQUIPMENT, CONSUMABLE, MATERIAL }

/** 物品品质 (普通/稀有/罕见/史诗/传说) */
enum class Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY }

/** 稀有度的显示名 */
fun Rarity.displayName(): String = when (this) {
    Rarity.COMMON -> "Common"
    Rarity.UNCOMMON -> "Uncommon"
    Rarity.RARE -> "Rare"
    Rarity.EPIC -> "Epic"
    Rarity.LEGENDARY -> "Legendary"
}

/** 英雄职业 (不限制装备，只提供熟练度加成) */
enum class HeroClass { WARRIOR, MAGE, RANGER, CLERIC }

/** 英雄当前状态 */
enum class HeroStatus { IDLE, DEPLOYED }

/** 队伍站位 (影响挨打概率) */
enum class Position { FRONT, BACK }

/** 主战术 (撤退条件) */
enum class StrategyType {
    SAFE,           // 稳扎稳打: 有人 HP<50% 且无回复手段 → 撤退
    BALANCED,       // 平衡: 有人 HP<20% 或全队 HP<50% 且无回复手段 → 撤退
    RISKY,          // 险中求胜: 有人 HP<20% 且无回复手段 → 撤退
    AGGRESSIVE      // 孤注一掷: 永不撤退
}

/** 副战术 (探索策略) */
enum class SecondaryStrategyType {
    EXPLORE,        // 攻略优先: 正常探索，发现楼梯则优先下楼
    SCAVENGE,       // 搜刮优先: 避开 Boss/楼梯，全部房间+拐角探索完后才打 Boss
    BOSS_RUSH       // Boss优先: 发现未击败 Boss 则优先前往
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
    TRAP,           // 陷阱 (DEX 解除)
    ENCOUNTER,      // 遭遇怪物
    LOOT,           // 发现战利品 (旧版本兼容, 新地图不再生成)
    GUARDED_LOOT,   // 守护宝藏 (怪物 + 宝箱)
    PUZZLE,         // 解谜 (INT 破解, 成功获得宝物)
    BOSS_FIGHT,     // Boss战
    STAIRS_UP,      // 上楼
    STAIRS_DOWN     // 下楼
}

/** 怪物技能效果 — 只保留 HEAVY_STRIKE (高倍率伤害) 和 STUN (跳过受击者下回合) */
enum class SkillEffect {
    HEAVY_STRIKE, // 重击, 用 powerMultiplier 直接放大伤害
    STUN          // 击晕, 受击者跳过下一回合
}

/** 魔法/奇迹分类 */
enum class SpellType { MAGIC, MIRACLE }

/** 法术功能类型 */
enum class SpellTarget {
    DAMAGE_SINGLE,   // 伤害敌方单体
    DAMAGE_AOE,      // 伤害敌方群体
    HEAL_SINGLE,     // 治疗我方单体
    HEAL_AOE,        // 治疗我方群体
    BUFF_SINGLE,     // 增益我方单体属性
    DEBUFF_SINGLE    // 减益敌方单体属性
}

// ==========================================
// 2. 物品 / 英雄 / 魔法
// ==========================================

/**
 * 物品与装备 - 魂系属性需求 + 装备等级 + 商店/掉落开关
 *
 * 数值规则:
 *   - baseValue (ATK/DEF): 武器/护甲专用，由 (equipLevel + 稀有度) 公式生成
 *   - goldCost / sellPrice: 由 (equipLevel + 稀有度 + 类型) 公式自动计算
 *   - statBonuses: 仅饰品使用，武器/护甲一律为空
 */
data class Item(
    val id: String,
    val name: String,
    val description: String = "",
    val type: ItemType,
    val rarity: Rarity = Rarity.COMMON,
    val weaponType: WeaponType? = null,
    val equipSlot: EquipSlot? = null,
    val equipLevel: Int = 1,          //装备等级，独立于稀有度
    val baseValue: Int = 0,           //装备的数值，由 (equipLevel + 稀有度) 公式生成
    val statRequirements: Map<StatType, Int> = emptyMap(),      //装备的属性需求
    val statBonuses: Map<StatType, Int> = emptyMap(),           //饰品的属性增益
    val purchasable: Boolean = rarity == Rarity.COMMON || rarity == Rarity.UNCOMMON,    //能否购买
    val lootable: Boolean = true,       //是否作为战利品
    val iconRes: Int = 0,               //资源
    val restoreHp: Int = 0,                 //消耗品的回血
    val restoreMp: Int = 0                  //回蓝
) {
    init {
        require(equipLevel in 1..10) { "equipLevel must be in 1..10 (id=$id, actual=$equipLevel)" }
        if (rarity == Rarity.EPIC) require(equipLevel >= 4) {
            "EPIC equipment requires level >= 4 (id=$id, actual=$equipLevel)"
        }
        if (rarity == Rarity.LEGENDARY) require(equipLevel >= 7) {
            "LEGENDARY equipment requires level >= 7 (id=$id, actual=$equipLevel)"
        }
    }

    /** 购买价 = 等级 × 50 × 稀有度倍率 (消耗品/材料用更小的乘数) */
    val goldCost: Int get() {
        val rarityMultiplier = when (rarity) {
            Rarity.COMMON -> 1.0
            Rarity.UNCOMMON -> 2.0
            Rarity.RARE -> 4.0
            Rarity.EPIC -> 8.0
            Rarity.LEGENDARY -> 16.0
        }
        val baseValue = when (type) {
            ItemType.EQUIPMENT -> 50
            ItemType.CONSUMABLE -> 20
            ItemType.MATERIAL -> 8
        }
        return (equipLevel * baseValue * rarityMultiplier).toInt()
    }

    /** 售价 = 购买价的一半 */
    val sellPrice: Int get() = goldCost / 2     //半价！
}

/** 魔法/奇迹的单个阶级 */
data class SpellTier(
    val name: String,
    val description: String,
    val power: Int,          // 基础威力
    val mpCost: Int          // 魔力消耗
)

/** 魔法/奇迹模板 - 包含4个阶级形态 */
data class SpellTemplate(
    val id: String,
    val type: SpellType,            // MAGIC(智力系) / MIRACLE(信仰系)
    val target: SpellTarget,        // 功能类型
    val affectedStat: StatType? = null, // BUFF/DEBUFF 影响的属性
    val tiers: List<SpellTier>      // 固定4个阶级
) {
    fun tierName(tier: Int): String = tiers[tier.coerceIn(0, 3)].name
}

/** 英雄已习得的魔法/奇迹实例 */
data class HeroSpell(
    val templateId: String,         //对应 SpellRegistry 的模板 id
    val currentTier: Int = 1  // 1-4
) {
    companion object {
        /** 升到目标 tier 所需的英雄等级 — T2=4, T3=7, T4=10 */
        fun requiredLevel(targetTier: Int): Int = 3 * targetTier - 2
    }
}

/** 英雄 - 游戏核心资产 */
data class Hero(
    val id: String,
    val name: String,
    val portraitUri: String?,                   //头像 URI, null 用默认图标
    val heroClass: HeroClass,
    val level: Int = 1,
    val exp: Int = 0,
    val currentHp: Int,
    val maxHp: Int,
    val currentMp: Int = 0,
    val maxMp: Int = 0,
    val baseStats: Map<StatType, Int>,          // 四维基础值
    val unspentStatPoints: Int = 0,             // 未分配属性点 (每升级 +5)
    val unspentSkillPoints: Int = 0,            // 未分配技能点 (每升级 +1)
    val spells: List<HeroSpell> = emptyList(),  //已习得的法术/奇迹
    /** 累计熟练度经验 (按武器/护甲分类)。等级由 proficiencyLevel() 派生 */
    val proficiencyXp: Map<WeaponType, Int> = emptyMap(),       //累计熟练度经验 (按武器/护甲分类)。等级由 proficiencyLevel() 派生
    val equipment: Map<EquipSlot, Item> = emptyMap(),   //三个槽位的装备
    val status: HeroStatus = HeroStatus.IDLE    //IDLE 闲置 / DEPLOYED 出战中
) {
    val isAlive: Boolean get() = currentHp > 0

    /** 升到下一级所需的总经验值: 50 * level * (level + 1) / 2 */
    val expToNextLevel: Int get() = 50 * level * (level + 1) / 2

    /** 当前熟练度等级 (0-10)。每级 +20% 装备贡献，超过 MAX_LEVEL 不再涨 */
    fun proficiencyLevel(type: WeaponType): Int {
        val xp = proficiencyXp[type] ?: 0
        var lv = 0
        while (lv < MAX_PROFICIENCY_LEVEL && xp >= proficiencyXpForLevel(lv + 1)) lv++
        return lv
    }

    /** 有效属性 = 基础 + 装备加成 */
    val effectiveStats: Map<StatType, Int> get() {
        val bonuses = mutableMapOf<StatType, Int>()
        equipment.values.forEach { equip ->             //目前暂时无用，以后也许会有用？
            equip.statBonuses.forEach { (stat, v) ->
                bonuses[stat] = (bonuses[stat] ?: 0) + v
            }
        }
        return StatType.entries.associate { stat ->
            stat to ((baseStats[stat] ?: 0) + (bonuses[stat] ?: 0))
        }
    }

    /** 综合攻击力 = 基础属性 + 武器 × (1 + 熟练度加成) */
    val attack: Int get() {
        val es = effectiveStats
        val str = es[StatType.STR] ?: 0         //为什么？
        val dex = es[StatType.DEX] ?: 0
        val weapon = equipment[EquipSlot.WEAPON]
        val weaponPower = weapon?.baseValue ?: 0
        val mult = 1f + (weapon?.weaponType?.let { proficiencyLevel(it) } ?: 0) * PROFICIENCY_BONUS_PER_LEVEL
        return str + dex / 2 + (weaponPower * mult).toInt()
    }

    /** 综合防御力 = 基础属性 + 护甲 × (1 + 熟练度加成) */
    val defense: Int get() {
        val es = effectiveStats
        val str = es[StatType.STR] ?: 0
        val dex = es[StatType.DEX] ?: 0
        val armor = equipment[EquipSlot.ARMOR]
        val armorPower = armor?.baseValue ?: 0
        val mult = 1f + (armor?.weaponType?.let { proficiencyLevel(it) } ?: 0) * PROFICIENCY_BONUS_PER_LEVEL
        return str / 2 + dex / 3 + (armorPower * mult).toInt()
    }

    /**
     * CP = (攻击×10 + 防御×7 + MP×2 + HP) × (1 + 可用法术数×0.2)
     * 法师 (MAGE) 只计 MAGIC, 牧师 (CLERIC) 只计 MIRACLE, 其它职业不能放法术故为 0.
     * attack/defense 已含装备 + 特质 + 熟练度加成
     */
    val combatPower: Int get() {
        val basePower = attack * 10 + defense * 7 + maxMp * 2 + maxHp
        val usableSpells = spells.count { hs ->
            val tpl = com.example.idledungeon.data.SpellRegistry.get(hs.templateId)
            when (heroClass) {
                HeroClass.MAGE -> tpl.type == SpellType.MAGIC
                HeroClass.CLERIC -> tpl.type == SpellType.MIRACLE
                else -> false
            }
        }
        val spellBonus = 1.0 + usableSpells * 0.2
        return (basePower * spellBonus).toInt()
    }

    companion object {
        const val MAX_PROFICIENCY_LEVEL = 10
        const val PROFICIENCY_BONUS_PER_LEVEL = 0.2f
        /** 升到 n 级所需的累计 XP: 5 * n * (n+1)。L1=10, L2=30, L3=60, ..., L10=550 */
        fun proficiencyXpForLevel(level: Int): Int = 5 * level * (level + 1)
    }
}

// ==========================================
// 3. 城镇 / 公会 / 任务
// ==========================================

/** 玩家公会状态 */
data class PlayerGuild(
    val gold: Int = 500,
    val maxCarryItems: Int = 10,         // 探险可携带道具上限
    val guildRank: Int = 1,             //公会声望/阶级, 完成主线任务自动 +1
    val tavernLevel: Int = 1,
    val blacksmithLevel: Int = 1,
    val itemShopLevel: Int = 1
) {
    /** 出战默认补给 = 100 + 酒馆每升一级 +20 */
    val defaultExpeditionSupply: Int get() = 100 + 20 * (tavernLevel - 1)
}

enum class QuestType {
    EXPLORE_ROOMS,    // 探索 N 个房间 (累计)
    DEFEAT_BOSS,      // 击败 X 层 Boss
    FIND_ITEM         // 获得指定物品
}

/** 公会主线任务 (顺序触发, 上一个完成后自动激活下一个) */
data class Quest(
    val id: String,
    val title: String,
    val description: String,
    val type: QuestType,
    val targetCount: Int = 0,           // 为探索房间准备
    val targetFloor: Int = 0,           // 为boss准备
    val targetItemId: String? = null,   // 为找item准备
    val isCompleted: Boolean = false
    // 完成奖励仅是公会升级, 不再有金币奖励
)

// ==========================================
// 4. 地牢地图系统
// ==========================================

/** 地牢节点 - 二维数组中的一个格子 */
data class DungeonNode(
    val type: NodeType,
    val event: NodeEvent = NodeEvent.NONE,          //格子上的事件 (战斗/陷阱/楼梯…)
    val monsterIds: List<String> = emptyList(),     //遭遇/守护战的怪物 id
    val lootTable: Map<String, Int> = emptyMap(),   //掉落表 itemId→概率(0-100)
    val explored: Boolean = false,                  //是否已揭开 (战争迷雾)
    /** 房间编号 (走廊为 null). 用于 BFS / 视野 / 区分房间格. */
    val roomNumber: Int? = null,
    /** 是否已被队伍踩过. 用于地图渲染高亮已访问房间 + BFS 不重复探索. */
    val visited: Boolean = false
) {
    /** 该格子是否承载一个房间 (有编号或是 Boss 房 / 楼梯) */
    val isRoom: Boolean get() =
        roomNumber != null
            || type == NodeType.BOSS_ROOM
            || event == NodeEvent.STAIRS_DOWN
            || event == NodeEvent.STAIRS_UP
}

/** 地牢楼层 - 一层 = 一个二维网格 */
data class DungeonFloor(
    val layer: Int,
    val width: Int,
    val height: Int,
    val grid: List<List<DungeonNode>>   // y x 顺序
)

// ==========================================
// 5. 探险与编队
// ==========================================

/** 队伍槽位 (英雄ID + 前/后排) */
data class PartySlot(
    val heroId: String,
    val position: Position  //前排 FRONT / 后排 BACK
)

/** 当前探险状态 - 存放在 ViewModel 中实时演算 */
data class ExpeditionSession(
    val dungeonId: String,
    val currentLayer: Int = 1,
    val posX: Int = 0,
    val posY: Int = 0,
    val party: List<PartySlot>,                 // 出战编队 (1-4人)
    val strategy: StrategyType = StrategyType.BALANCED,         //主战术: 何时撤退
    val secondaryStrategy: SecondaryStrategyType = SecondaryStrategyType.EXPLORE,   //副战术: BFS 目标优先级
    val currentSupply: Int,                       //剩余补给, 归零自动撤退
    val maxSupply: Int = currentSupply,            // 本次探险起始补给, 用于 UI 显示比例
    val carriedItems: List<String> = emptyList(), // 携带的消耗品ID
    val backpack: List<String> = emptyList(),     // 探索中拾取的战利品ID
    val isExploring: Boolean = false,
    val combatLog: List<String> = emptyList()     //探索/战斗日志 (UI 显示)
)

// ==========================================
// 6. 怪物与战斗
// ==========================================

/** 怪物技能 */
data class MonsterSkill(
    val id: String,
    val name: String,
    val description: String,
    val effectType: SkillEffect,    //HEAVY_STRIKE 重击 / STUN 击晕
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
    val skills: List<MonsterSkill> = emptyList(),   //怪物技能 (可空)
    val dropChances: Map<String, Int> = emptyMap()  //掉落表 itemId→概率(0-100)
) {
    val isAlive: Boolean get() = currentHp > 0
    /**
     * CP = (攻击×10 + 防御×7 + MP×2 + HP) × (1 + 技能数×0.2)
     * 怪物没有 MP, 按 0 计算
     */
    val combatPower: Int get() {
        val basePower = attack * 10 + defense * 7 + maxHp
        val skillBonus = 1.0 + skills.size * 0.2
        return (basePower * skillBonus).toInt()
    }
    /** 击败后奖励的经验值 */
    val expReward: Int get() = 10 + level * 8
}

// ==========================================
// 7. 图鉴与存档
// ==========================================

/** 主线 Q3 剑 ID — `GameViewModel.MAIN_QUESTS` 与 `DungeonGenerator.randomGuardedLootCell` 共用 */
const val QUEST_EPIC_SWORD_ID = "mossy_blade_sword"

/** 游戏存档顶层状态 (用于序列化 / Room 持久化) */
data class GameState(
    val hasStarted: Boolean = false,              //是否已开始 (区分新游戏/继续)
    val playerName: String = "Guild Master",
    val playerAvatarUri: String? = null,          //玩家头像 URI, null 用默认
    val guild: PlayerGuild = PlayerGuild(),
    val deepestFloorReached: Int = 1,
    // ── 主线任务进度 ──
    val mainQuestIndex: Int = 0,        // 当前进行中的任务下标; ≥ quests.size 表示全部完成
    val roomsExplored: Int = 0,         // 累计探索房间数
    val bossesDefeated: Set<Int> = emptySet(),    // 已击败 boss 的层号
    val foundItemIds: Set<String> = emptySet(),   // 历史拿到过的物品 ID
    val hasUnreadQuestUpdate: Boolean = true,     // 任务更新待玩家查看 (城镇公会图标红点)
    // ── 怪物图鉴 ──
    val monsterKills: Map<String, Int> = emptyMap(),  // monsterId → 累计击败次数
    // ── 商店每日刷新 ──
    /** 上次刷新的"日期键" = YYYY * 10000 + MM * 100 + DD; 进入新一天则触发刷新 */
    val lastShopRefreshDayKey: Int = 0,
    // ── 主线 Q3 保底 ──
    /** F2 累计开过的守卫宝箱中没掉到苔纹剑的次数; 达到 5 时下个 F2 守卫宝箱必出 */
    val f2ChestPity: Int = 0,
    // ── 招募者编号 ──
    /**
     * 下一个招募者要分配的编号; 每次刷新酒馆按此累加.
     * 保证"冒险者 N"的名字跨刷新单调递增, 不会回到 1.
     */
    val nextRecruitNumber: Int = 1
)