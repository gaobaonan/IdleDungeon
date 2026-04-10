package com.example.idledungeon.data

import com.example.idledungeon.model.*

/**
 * 物品注册表 - 集中定义所有物品模板
 * 用法: ItemRegistry.get("iron_sword")
 */
object ItemRegistry {

    private val items = listOf(
        // ============================================================
        //  武器 - 剑类 (SWORD)
        // ============================================================
        Item(
            id = "short_sword", name = "短剑",
            description = "轻便的入门武器",
            type = ItemType.EQUIPMENT, rarity = Rarity.COMMON,
            weaponType = WeaponType.SWORD, equipSlot = EquipSlot.WEAPON,
            baseValue = 8,
            statRequirements = mapOf(StatType.STR to 5, StatType.DEX to 5)
        ),
        Item(
            id = "long_sword", name = "长剑",
            description = "骑士的标配武器",
            type = ItemType.EQUIPMENT, rarity = Rarity.UNCOMMON,
            weaponType = WeaponType.SWORD, equipSlot = EquipSlot.WEAPON,
            baseValue = 14,
            statRequirements = mapOf(StatType.STR to 10, StatType.DEX to 8)
        ),
        Item(
            id = "greatsword", name = "双手剑",
            description = "沉重的巨剑，挥舞需要极大力量",
            type = ItemType.EQUIPMENT, rarity = Rarity.RARE,
            weaponType = WeaponType.SWORD, equipSlot = EquipSlot.WEAPON,
            baseValue = 22,
            statRequirements = mapOf(StatType.STR to 18)
        ),
        Item(
            id = "flame_blade", name = "炎之刃",
            description = "刀身常年燃烧着不灭之火",
            type = ItemType.EQUIPMENT, rarity = Rarity.EPIC,
            weaponType = WeaponType.SWORD, equipSlot = EquipSlot.WEAPON,
            baseValue = 32,
            statRequirements = mapOf(StatType.STR to 22, StatType.DEX to 12),
            statBonuses = mapOf(StatType.STR to 3)
        ),

        // ============================================================
        //  武器 - 匕首类 (DAGGER)
        // ============================================================
        Item(
            id = "rusty_dagger", name = "锈蚀匕首",
            description = "布满锈迹但依然锋利",
            type = ItemType.EQUIPMENT, rarity = Rarity.COMMON,
            weaponType = WeaponType.DAGGER, equipSlot = EquipSlot.WEAPON,
            baseValue = 5,
            statRequirements = mapOf(StatType.DEX to 4)
        ),
        Item(
            id = "steel_dagger", name = "精钢短刀",
            description = "盗贼公会定制的轻巧利器",
            type = ItemType.EQUIPMENT, rarity = Rarity.UNCOMMON,
            weaponType = WeaponType.DAGGER, equipSlot = EquipSlot.WEAPON,
            baseValue = 10,
            statRequirements = mapOf(StatType.DEX to 10)
        ),
        Item(
            id = "shadow_fang", name = "暗影之牙",
            description = "淬炼了暗影精华的刺客匕首",
            type = ItemType.EQUIPMENT, rarity = Rarity.RARE,
            weaponType = WeaponType.DAGGER, equipSlot = EquipSlot.WEAPON,
            baseValue = 18,
            statRequirements = mapOf(StatType.DEX to 16),
            statBonuses = mapOf(StatType.DEX to 4)
        ),

        // ============================================================
        //  武器 - 矛类 (SPEAR)
        // ============================================================
        Item(
            id = "iron_spear", name = "铁矛",
            description = "擅长保持距离的长兵器",
            type = ItemType.EQUIPMENT, rarity = Rarity.UNCOMMON,
            weaponType = WeaponType.SPEAR, equipSlot = EquipSlot.WEAPON,
            baseValue = 12,
            statRequirements = mapOf(StatType.STR to 10, StatType.DEX to 6)
        ),
        Item(
            id = "halberd", name = "战戟",
            description = "兼备劈砍与穿刺的重型长柄武器",
            type = ItemType.EQUIPMENT, rarity = Rarity.RARE,
            weaponType = WeaponType.SPEAR, equipSlot = EquipSlot.WEAPON,
            baseValue = 20,
            statRequirements = mapOf(StatType.STR to 16, StatType.DEX to 8)
        ),

        // ============================================================
        //  武器 - 弓类 (BOW)
        // ============================================================
        Item(
            id = "hunting_bow", name = "猎弓",
            description = "森林猎人的可靠伙伴",
            type = ItemType.EQUIPMENT, rarity = Rarity.UNCOMMON,
            weaponType = WeaponType.BOW, equipSlot = EquipSlot.WEAPON,
            baseValue = 10,
            statRequirements = mapOf(StatType.DEX to 12)
        ),
        Item(
            id = "composite_bow", name = "复合弓",
            description = "多层材料压制而成，射程与威力兼备",
            type = ItemType.EQUIPMENT, rarity = Rarity.RARE,
            weaponType = WeaponType.BOW, equipSlot = EquipSlot.WEAPON,
            baseValue = 18,
            statRequirements = mapOf(StatType.DEX to 18, StatType.STR to 8)
        ),
        Item(
            id = "gale_bow", name = "疾风弓",
            description = "传说由风精灵赐予的弓，箭矢快如闪电",
            type = ItemType.EQUIPMENT, rarity = Rarity.EPIC,
            weaponType = WeaponType.BOW, equipSlot = EquipSlot.WEAPON,
            baseValue = 28,
            statRequirements = mapOf(StatType.DEX to 24),
            statBonuses = mapOf(StatType.DEX to 5)
        ),

        // ============================================================
        //  武器 - 法杖类 (STAFF)
        // ============================================================
        Item(
            id = "wooden_staff", name = "木杖",
            description = "蕴含微弱魔力的法杖",
            type = ItemType.EQUIPMENT, rarity = Rarity.COMMON,
            weaponType = WeaponType.STAFF, equipSlot = EquipSlot.WEAPON,
            baseValue = 6,
            statRequirements = mapOf(StatType.INT to 8),
            statBonuses = mapOf(StatType.INT to 3)
        ),
        Item(
            id = "crystal_staff", name = "水晶法杖",
            description = "杖端镶嵌着纯净的魔力水晶",
            type = ItemType.EQUIPMENT, rarity = Rarity.UNCOMMON,
            weaponType = WeaponType.STAFF, equipSlot = EquipSlot.WEAPON,
            baseValue = 11,
            statRequirements = mapOf(StatType.INT to 14),
            statBonuses = mapOf(StatType.INT to 5)
        ),
        Item(
            id = "arcane_scepter", name = "奥术权杖",
            description = "古代魔导士的遗物，蕴含深渊般的魔力",
            type = ItemType.EQUIPMENT, rarity = Rarity.EPIC,
            weaponType = WeaponType.STAFF, equipSlot = EquipSlot.WEAPON,
            baseValue = 18,
            statRequirements = mapOf(StatType.INT to 22),
            statBonuses = mapOf(StatType.INT to 8, StatType.FTH to 3)
        ),

        // ============================================================
        //  护甲 - 轻甲 (LIGHT_ARMOR)
        // ============================================================
        Item(
            id = "leather_armor", name = "皮甲",
            description = "轻便的基础防具",
            type = ItemType.EQUIPMENT, rarity = Rarity.COMMON,
            weaponType = WeaponType.LIGHT_ARMOR, equipSlot = EquipSlot.ARMOR,
            baseValue = 8,
            statRequirements = mapOf(StatType.STR to 6)
        ),
        Item(
            id = "ranger_vest", name = "游侠皮衣",
            description = "经过特殊鞣制，兼顾防护与灵活",
            type = ItemType.EQUIPMENT, rarity = Rarity.UNCOMMON,
            weaponType = WeaponType.LIGHT_ARMOR, equipSlot = EquipSlot.ARMOR,
            baseValue = 13,
            statRequirements = mapOf(StatType.DEX to 10),
            statBonuses = mapOf(StatType.DEX to 2)
        ),
        Item(
            id = "mage_robe", name = "法师长袍",
            description = "织入了魔纹的丝绸长袍",
            type = ItemType.EQUIPMENT, rarity = Rarity.UNCOMMON,
            weaponType = WeaponType.LIGHT_ARMOR, equipSlot = EquipSlot.ARMOR,
            baseValue = 10,
            statRequirements = mapOf(StatType.INT to 10),
            statBonuses = mapOf(StatType.INT to 4)
        ),
        Item(
            id = "shadow_cloak", name = "暗影斗篷",
            description = "穿戴者的身影变得模糊难辨",
            type = ItemType.EQUIPMENT, rarity = Rarity.RARE,
            weaponType = WeaponType.LIGHT_ARMOR, equipSlot = EquipSlot.ARMOR,
            baseValue = 16,
            statRequirements = mapOf(StatType.DEX to 14),
            statBonuses = mapOf(StatType.DEX to 5)
        ),

        // ============================================================
        //  护甲 - 重甲 (HEAVY_ARMOR)
        // ============================================================
        Item(
            id = "chain_mail", name = "锁子甲",
            description = "铁环编织的重型铠甲",
            type = ItemType.EQUIPMENT, rarity = Rarity.UNCOMMON,
            weaponType = WeaponType.HEAVY_ARMOR, equipSlot = EquipSlot.ARMOR,
            baseValue = 16,
            statRequirements = mapOf(StatType.STR to 14)
        ),
        Item(
            id = "plate_armor", name = "板甲",
            description = "骑士团标配的全身甲胄",
            type = ItemType.EQUIPMENT, rarity = Rarity.RARE,
            weaponType = WeaponType.HEAVY_ARMOR, equipSlot = EquipSlot.ARMOR,
            baseValue = 24,
            statRequirements = mapOf(StatType.STR to 20)
        ),
        Item(
            id = "holy_plate", name = "圣骑士铠甲",
            description = "受到神圣祝福的全身板甲，散发柔和光辉",
            type = ItemType.EQUIPMENT, rarity = Rarity.EPIC,
            weaponType = WeaponType.HEAVY_ARMOR, equipSlot = EquipSlot.ARMOR,
            baseValue = 32,
            statRequirements = mapOf(StatType.STR to 24, StatType.FTH to 10),
            statBonuses = mapOf(StatType.FTH to 4)
        ),

        // ============================================================
        //  饰品 (ACCESSORY)
        // ============================================================
        Item(
            id = "prayer_ring", name = "祈祷戒指",
            description = "虔信者的圣物，提升信仰",
            type = ItemType.EQUIPMENT, rarity = Rarity.UNCOMMON,
            equipSlot = EquipSlot.ACCESSORY,
            baseValue = 0,
            statBonuses = mapOf(StatType.FTH to 5)
        ),
        Item(
            id = "agility_amulet", name = "敏捷护符",
            description = "佩戴者会感到身轻如燕",
            type = ItemType.EQUIPMENT, rarity = Rarity.UNCOMMON,
            equipSlot = EquipSlot.ACCESSORY,
            baseValue = 0,
            statBonuses = mapOf(StatType.DEX to 4)
        ),
        Item(
            id = "iron_ring", name = "铁戒指",
            description = "粗糙但结实的铁环，佩戴者力量微增",
            type = ItemType.EQUIPMENT, rarity = Rarity.COMMON,
            equipSlot = EquipSlot.ACCESSORY,
            baseValue = 0,
            statBonuses = mapOf(StatType.STR to 2)
        ),
        Item(
            id = "scholar_glasses", name = "学者眼镜",
            description = "据说是某位大魔导师的遗物",
            type = ItemType.EQUIPMENT, rarity = Rarity.UNCOMMON,
            equipSlot = EquipSlot.ACCESSORY,
            baseValue = 0,
            statBonuses = mapOf(StatType.INT to 4)
        ),
        Item(
            id = "warriors_medal", name = "武勋章",
            description = "表彰英勇战斗的军功章，鼓舞士气",
            type = ItemType.EQUIPMENT, rarity = Rarity.RARE,
            equipSlot = EquipSlot.ACCESSORY,
            baseValue = 0,
            statBonuses = mapOf(StatType.STR to 5, StatType.DEX to 3)
        ),
        Item(
            id = "life_pendant", name = "生命吊坠",
            description = "翠绿宝石散发温暖的治愈之光",
            type = ItemType.EQUIPMENT, rarity = Rarity.RARE,
            equipSlot = EquipSlot.ACCESSORY,
            baseValue = 0,
            statBonuses = mapOf(StatType.FTH to 6, StatType.INT to 2)
        ),
        Item(
            id = "dragon_eye_amulet", name = "龙眼项链",
            description = "镶嵌着龙眼石的项链，佩戴者全属性提升",
            type = ItemType.EQUIPMENT, rarity = Rarity.EPIC,
            equipSlot = EquipSlot.ACCESSORY,
            baseValue = 0,
            statBonuses = mapOf(StatType.STR to 4, StatType.DEX to 4, StatType.INT to 4, StatType.FTH to 4)
        ),

        // ============================================================
        //  消耗品 (CONSUMABLE)
        // ============================================================
        Item(id = "health_potion", name = "生命药水", description = "回复50HP", type = ItemType.CONSUMABLE, baseValue = 30),
        Item(id = "mana_potion", name = "魔力药水", description = "回复30MP", type = ItemType.CONSUMABLE, baseValue = 25),
        Item(id = "antidote", name = "解毒药", description = "解除中毒状态", type = ItemType.CONSUMABLE, baseValue = 20),
        Item(id = "great_health_potion", name = "高级生命药水", description = "回复150HP", type = ItemType.CONSUMABLE, rarity = Rarity.UNCOMMON, baseValue = 80),
        Item(id = "great_mana_potion", name = "高级魔力药水", description = "回复100MP", type = ItemType.CONSUMABLE, rarity = Rarity.UNCOMMON, baseValue = 65),
        Item(id = "smoke_bomb", name = "烟雾弹", description = "立刻脱离战斗", type = ItemType.CONSUMABLE, baseValue = 40),
        Item(id = "ration", name = "干粮", description = "恢复10点补给", type = ItemType.CONSUMABLE, baseValue = 15),

        // ============================================================
        //  材料 / 换金道具 (MATERIAL)
        // ============================================================
        Item(id = "iron_ore", name = "铁矿石", description = "常见的锻造材料", type = ItemType.MATERIAL, baseValue = 10),
        Item(id = "monster_bone", name = "怪物骨头", description = "可以卖个好价钱", type = ItemType.MATERIAL, baseValue = 15),
        Item(id = "magic_crystal", name = "魔力结晶", description = "稀有的魔法材料", type = ItemType.MATERIAL, rarity = Rarity.RARE, baseValue = 50),
        Item(id = "slime_gel", name = "史莱姆凝胶", description = "黏糊糊的半透明胶质", type = ItemType.MATERIAL, baseValue = 5),
        Item(id = "spider_silk", name = "蛛丝", description = "坚韧异常的蜘蛛丝线", type = ItemType.MATERIAL, baseValue = 12),
        Item(id = "goblin_ear", name = "哥布林耳朵", description = "讨伐证明，可在公会换赏金", type = ItemType.MATERIAL, baseValue = 8),
        Item(id = "dark_shard", name = "暗黑碎片", description = "暗黑骑士铠甲上剥落的碎片", type = ItemType.MATERIAL, rarity = Rarity.UNCOMMON, baseValue = 25),
        Item(id = "minotaur_horn", name = "牛头人角", description = "坚硬如铁的Boss素材", type = ItemType.MATERIAL, rarity = Rarity.RARE, baseValue = 80),
        Item(id = "lich_phylactery", name = "巫妖命匣碎片", description = "蕴含强大亡灵能量的禁忌之物", type = ItemType.MATERIAL, rarity = Rarity.EPIC, baseValue = 150),
        Item(id = "ancient_coin", name = "古代金币", description = "地牢中发现的古代货币，收藏家高价收购", type = ItemType.MATERIAL, rarity = Rarity.UNCOMMON, baseValue = 35),
        Item(id = "dragon_scale", name = "龙鳞", description = "不知从何而来的龙鳞片，价值连城", type = ItemType.MATERIAL, rarity = Rarity.EPIC, baseValue = 200)
    ).associateBy { it.id }

    fun get(id: String): Item = items[id] ?: error("Unknown item: $id")
    fun all(): List<Item> = items.values.toList()
    fun byType(type: ItemType): List<Item> = items.values.filter { it.type == type }
    fun byRarity(rarity: Rarity): List<Item> = items.values.filter { it.rarity == rarity }
}