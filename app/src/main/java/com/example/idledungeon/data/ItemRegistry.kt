package com.example.idledungeon.data

import com.example.idledungeon.model.*

/**
 * 物品注册表 - 集中定义所有物品模板
 * 用法: ItemRegistry.get("iron_sword")
 */
object ItemRegistry {

    private val items = listOf(
        // ──── 剑类 ────
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

        // ──── 其他武器 ────
        Item(
            id = "rusty_dagger", name = "锈蚀匕首",
            description = "布满锈迹但依然锋利",
            type = ItemType.EQUIPMENT, rarity = Rarity.COMMON,
            weaponType = WeaponType.DAGGER, equipSlot = EquipSlot.WEAPON,
            baseValue = 5,
            statRequirements = mapOf(StatType.DEX to 4)
        ),
        Item(
            id = "iron_spear", name = "铁矛",
            description = "擅长保持距离的长兵器",
            type = ItemType.EQUIPMENT, rarity = Rarity.UNCOMMON,
            weaponType = WeaponType.SPEAR, equipSlot = EquipSlot.WEAPON,
            baseValue = 12,
            statRequirements = mapOf(StatType.STR to 10, StatType.DEX to 6)
        ),
        Item(
            id = "hunting_bow", name = "猎弓",
            description = "森林猎人的可靠伙伴",
            type = ItemType.EQUIPMENT, rarity = Rarity.UNCOMMON,
            weaponType = WeaponType.BOW, equipSlot = EquipSlot.WEAPON,
            baseValue = 10,
            statRequirements = mapOf(StatType.DEX to 12)
        ),
        Item(
            id = "wooden_staff", name = "木杖",
            description = "蕴含微弱魔力的法杖",
            type = ItemType.EQUIPMENT, rarity = Rarity.COMMON,
            weaponType = WeaponType.STAFF, equipSlot = EquipSlot.WEAPON,
            baseValue = 6,
            statRequirements = mapOf(StatType.INT to 8),
            statBonuses = mapOf(StatType.INT to 3)
        ),

        // ──── 护甲 ────
        Item(
            id = "leather_armor", name = "皮甲",
            description = "轻便的基础防具",
            type = ItemType.EQUIPMENT, rarity = Rarity.COMMON,
            weaponType = WeaponType.LIGHT_ARMOR, equipSlot = EquipSlot.ARMOR,
            baseValue = 8,
            statRequirements = mapOf(StatType.STR to 6)
        ),
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

        // ──── 饰品 ────
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

        // ──── 消耗品 ────
        Item(id = "health_potion", name = "生命药水", description = "回复50HP", type = ItemType.CONSUMABLE, baseValue = 30),
        Item(id = "mana_potion", name = "魔力药水", description = "回复30MP", type = ItemType.CONSUMABLE, baseValue = 25),
        Item(id = "antidote", name = "解毒药", description = "解除中毒状态", type = ItemType.CONSUMABLE, baseValue = 20),

        // ──── 材料 (换金道具) ────
        Item(id = "iron_ore", name = "铁矿石", description = "常见的锻造材料", type = ItemType.MATERIAL, baseValue = 10),
        Item(id = "monster_bone", name = "怪物骨头", description = "可以卖个好价钱", type = ItemType.MATERIAL, baseValue = 15),
        Item(id = "magic_crystal", name = "魔力结晶", description = "稀有的魔法材料", type = ItemType.MATERIAL, rarity = Rarity.RARE, baseValue = 50)
    ).associateBy { it.id }

    fun get(id: String): Item = items[id] ?: error("Unknown item: $id")
    fun all(): List<Item> = items.values.toList()
    fun byType(type: ItemType): List<Item> = items.values.filter { it.type == type }
    fun byRarity(rarity: Rarity): List<Item> = items.values.filter { it.rarity == rarity }
}