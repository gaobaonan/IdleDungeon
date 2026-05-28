package com.gaotong.idledungeon.data

import com.gaotong.idledungeon.R
import com.gaotong.idledungeon.model.*

/**
 * 物品注册表 - 集中定义所有物品模板
 *
 * 数值由公式驱动:
 *   - 武器/护甲 baseValue = weaponPower(level, rarity) / armorPower(level, rarity)
 *   - 饰品 statBonuses 数额由 accessoryBonus(level, rarity) 决定
 *   - 价格由 Item.goldCost / sellPrice 自动派生
 *   - 属性需求由 reqFor(weaponType, level) 决定
 *
 * 商店刷出条件: purchasable && equipLevel <= 建筑等级
 * 地下城掉落条件: lootable
 */
object ItemRegistry {

    // 数值公式

    private fun rarityBonus(r: Rarity, scale: Int = 1): Int = when (r) {
        Rarity.COMMON -> 0
        Rarity.UNCOMMON -> 2 * scale
        Rarity.RARE -> 5 * scale
        Rarity.EPIC -> 10 * scale
        Rarity.LEGENDARY -> 18 * scale
    }

    /** 武器基础攻击力: 同等级稀有度越高越强 */
    private fun weaponPower(level: Int, r: Rarity): Int = 3 + level * 4 + rarityBonus(r)

    /** 护甲基础防御力: 同等级稀有度越高越强 */
    private fun armorPower(level: Int, r: Rarity): Int = 4 + level * 4 + rarityBonus(r)

    /** 饰品单条属性加成 (饰品才有 statBonuses) */
    private fun accessoryBonus(level: Int, r: Rarity): Int {
        val bonus = when (r) {
            Rarity.COMMON -> 0
            Rarity.UNCOMMON -> 1
            Rarity.RARE -> 2
            Rarity.EPIC -> 4
            Rarity.LEGENDARY -> 7
        }
        return 1 + level / 2 + bonus
    }

    /** 武器/护甲属性需求 */
    private fun reqFor(t: WeaponType, level: Int): Map<StatType, Int> = when (t) {
        WeaponType.SWORD -> mapOf(StatType.STR to (level * 2 + 3), StatType.DEX to (level + 3))
        WeaponType.DAGGER -> mapOf(StatType.DEX to (level * 2 + 3))
        WeaponType.SPEAR -> mapOf(StatType.STR to (level * 2 + 5))
        WeaponType.BOW -> mapOf(StatType.DEX to (level * 2 + 5))
        WeaponType.STAFF -> mapOf(StatType.INT to (level * 2 + 5))
        WeaponType.HOLY_BELL -> mapOf(StatType.FTH to (level * 2 + 5))
        WeaponType.HEAVY_ARMOR -> mapOf(StatType.STR to (level * 2 + 5))
        WeaponType.LIGHT_ARMOR -> mapOf(StatType.DEX to (level + 3))
    }

    // Builder helpers

    private fun weapon(
        id: String, name: String, type: WeaponType,
        level: Int, rarity: Rarity, icon: Int = R.drawable.empty,
        desc: String = "", purchasable: Boolean? = null, lootable: Boolean = true
    ): Item = Item(
        id = id, name = name, description = desc,
        type = ItemType.EQUIPMENT, rarity = rarity,
        weaponType = type, equipSlot = EquipSlot.WEAPON,
        equipLevel = level,
        baseValue = weaponPower(level, rarity),
        statRequirements = reqFor(type, level),
        purchasable = purchasable ?: (rarity == Rarity.COMMON || rarity == Rarity.UNCOMMON),
        lootable = lootable,
        iconRes = icon
    )

    private fun armor(
        id: String, name: String, type: WeaponType,
        level: Int, rarity: Rarity, icon: Int = R.drawable.empty,
        desc: String = "", purchasable: Boolean? = null, lootable: Boolean = true
    ): Item = Item(
        id = id, name = name, description = desc,
        type = ItemType.EQUIPMENT, rarity = rarity,
        weaponType = type, equipSlot = EquipSlot.ARMOR,
        equipLevel = level,
        baseValue = armorPower(level, rarity),
        statRequirements = reqFor(type, level),
        purchasable = purchasable ?: (rarity == Rarity.COMMON || rarity == Rarity.UNCOMMON),
        lootable = lootable,
        iconRes = icon
    )

    private fun accessory(
        id: String, name: String,
        level: Int, rarity: Rarity, primaryStat: StatType,
        icon: Int = R.drawable.empty,
        secondaryStat: StatType? = null,
        desc: String = "", purchasable: Boolean? = null, lootable: Boolean = true
    ): Item {
        val n = accessoryBonus(level, rarity)
        val bonuses = mutableMapOf(primaryStat to n)
        if (secondaryStat != null) bonuses[secondaryStat] = (n / 2).coerceAtLeast(1)
        return Item(
            id = id, name = name, description = desc,
            type = ItemType.EQUIPMENT, rarity = rarity,
            equipSlot = EquipSlot.ACCESSORY,
            equipLevel = level,
            statBonuses = bonuses,
            purchasable = purchasable ?: (rarity == Rarity.COMMON || rarity == Rarity.UNCOMMON),
            lootable = lootable,
            iconRes = icon
        )
    }

    private fun accessoryAll(
        id: String, name: String, level: Int, rarity: Rarity,
        icon: Int = R.drawable.empty, desc: String = "",
        purchasable: Boolean? = null, lootable: Boolean = true
    ): Item {
        val n = accessoryBonus(level, rarity)
        return Item(
            id = id, name = name, description = desc,
            type = ItemType.EQUIPMENT, rarity = rarity,
            equipSlot = EquipSlot.ACCESSORY,
            equipLevel = level,
            statBonuses = StatType.entries.associateWith { n },
            purchasable = purchasable ?: (rarity == Rarity.COMMON || rarity == Rarity.UNCOMMON),
            lootable = lootable,
            iconRes = icon
        )
    }

    private fun consumable(
        id: String, name: String, desc: String,
        level: Int = 1, rarity: Rarity = Rarity.COMMON,
        purchasable: Boolean = true, lootable: Boolean = false,
        restoreHp: Int = 0, restoreMp: Int = 0
    ): Item = Item(
        id = id, name = name, description = desc,
        type = ItemType.CONSUMABLE, rarity = rarity,
        equipLevel = level,
        purchasable = purchasable, lootable = lootable,
        iconRes = R.drawable.empty,
        restoreHp = restoreHp, restoreMp = restoreMp
    )

    private fun material(
        id: String, name: String, desc: String,
        level: Int = 1, rarity: Rarity = Rarity.COMMON
    ): Item = Item(
        id = id, name = name, description = desc,
        type = ItemType.MATERIAL, rarity = rarity,
        equipLevel = level,
        purchasable = false, lootable = true,  // 材料只能从地下城获取
        iconRes = R.drawable.empty
    )

    // Item 列表

    private val items: Map<String, Item> = (
        // 剑 (SWORD) — 75 件，对应 res/drawable 中所有剑类图片
        listOf(
            // L1 — 入门铁剑
            weapon("rusty_iron_sword", "Rusty Iron Sword", WeaponType.SWORD, 1, Rarity.COMMON, R.drawable.rusty_iron_sword, "An old sword covered in rust"),
            weapon("basic_iron_sword", "Basic Iron Sword", WeaponType.SWORD, 1, Rarity.COMMON, R.drawable.basic_iron_sword, "The most basic iron sword"),
            weapon("common_iron_sword", "Common Iron Sword", WeaponType.SWORD, 1, Rarity.COMMON, R.drawable.common_iron_sword, "An iron sword found everywhere"),
            weapon("petite_iron_sword", "Petite Iron Sword", WeaponType.SWORD, 1, Rarity.COMMON, R.drawable.petite_iron_sword, "A smaller-than-usual iron sword"),
            weapon("thin_short_sword", "Thin Short Sword", WeaponType.SWORD, 1, Rarity.COMMON, R.drawable.thin_short_sword, "A slender short sword"),
            weapon("iron_shortsword", "Iron Shortsword", WeaponType.SWORD, 1, Rarity.UNCOMMON, R.drawable.iron_shortsword, "A decently crafted iron shortsword"),
            weapon("gladius", "Gladius", WeaponType.SWORD, 1, Rarity.UNCOMMON, R.drawable.gladius, "The short sword favored by Roman gladiators"),

            // L2
            weapon("plain_iron_arming_sword", "Plain Iron Arming Sword", WeaponType.SWORD, 2, Rarity.COMMON, R.drawable.plain_iron_arming_sword, "An ordinary arming sword"),
            weapon("plain_steel_sword", "Plain Steel Sword", WeaponType.SWORD, 2, Rarity.COMMON, R.drawable.plain_steel_sword, "An unadorned steel sword"),
            weapon("simple_steel_sword", "Simple Steel Sword", WeaponType.SWORD, 2, Rarity.COMMON, R.drawable.simple_steel_sword, "A simply crafted steel sword"),
            weapon("cobbled_iron_sword", "Cobbled Iron Sword", WeaponType.SWORD, 2, Rarity.COMMON, R.drawable.cobbled_iron_sword, "Forged from several pieces of iron"),
            weapon("traveler_iron_sword", "Traveler Iron Sword", WeaponType.SWORD, 2, Rarity.UNCOMMON, R.drawable.traveler_iron_sword, "A traveler's trusty sidearm"),
            weapon("wooden_hilt_sword", "Wooden Hilt Sword", WeaponType.SWORD, 2, Rarity.UNCOMMON, R.drawable.wooden_hilt_sword, "A comfortable wooden grip"),
            weapon("iron_long_sword", "Iron Long Sword", WeaponType.SWORD, 2, Rarity.UNCOMMON, R.drawable.iron_long_sword, "A standard-issue longsword"),

            // L3
            weapon("broad_iron_sword", "Broad Iron Sword", WeaponType.SWORD, 3, Rarity.COMMON, R.drawable.broad_iron_sword, "A broad-bladed iron sword"),
            weapon("heavy_iron_sword", "Heavy Iron Sword", WeaponType.SWORD, 3, Rarity.COMMON, R.drawable.heavy_iron_sword, "So heavy it feels clumsy"),
            weapon("rugged_iron_sword", "Rugged Iron Sword", WeaponType.SWORD, 3, Rarity.COMMON, R.drawable.rugged_iron_sword, "A coarse, rugged iron sword"),
            weapon("common_arming_sword", "Common Arming Sword", WeaponType.SWORD, 3, Rarity.UNCOMMON, R.drawable.common_arming_sword, "A knight's standard weapon"),
            weapon("soldier_sword", "Soldier Sword", WeaponType.SWORD, 3, Rarity.UNCOMMON, R.drawable.soldier_sword, "Regular army standard-issue gear"),
            weapon("squire_sword", "Squire Sword", WeaponType.SWORD, 3, Rarity.UNCOMMON, R.drawable.squire_sword, "Carried by well-trained squires"),
            weapon("studded_iron_sword", "Studded Iron Sword", WeaponType.SWORD, 3, Rarity.RARE, R.drawable.studded_iron_sword, "The hilt is set with rivets"),
            weapon("guardsman_sword", "Guardsman Sword", WeaponType.SWORD, 3, Rarity.RARE, R.drawable.guardsman_sword, "A weapon specially made for the city guard"),

            // L4 — 引入 EPIC
            weapon("long_iron_blade", "Long Iron Blade", WeaponType.SWORD, 4, Rarity.COMMON, R.drawable.long_iron_blade, "An iron sword with a yard-long blade"),
            weapon("bronze_engraved_sword", "Bronze Engraved Sword", WeaponType.SWORD, 4, Rarity.UNCOMMON, R.drawable.bronze_engraved_sword, "A bronze blade engraved with ancient patterns"),
            weapon("adventurer_long_sword", "Adventurer Long Sword", WeaponType.SWORD, 4, Rarity.UNCOMMON, R.drawable.adventurer_long_sword, "Recommended by the Adventurers' Guild"),
            weapon("bronze_longsword", "Bronze Longsword", WeaponType.SWORD, 4, Rarity.UNCOMMON, R.drawable.bronze_longsword, "Forged from ancient bronze"),
            weapon("wanderer_sword", "Wanderer Sword", WeaponType.SWORD, 4, Rarity.RARE, R.drawable.wanderer_sword, "A wandering swordsman's heirloom"),
            weapon("cross_guard_iron_sword", "Cross Guard Iron Sword", WeaponType.SWORD, 4, Rarity.RARE, R.drawable.cross_guard_iron_sword, "Its guard forms a cross shape"),
            weapon("crosshilt_iron_sword", "Crosshilt Iron Sword", WeaponType.SWORD, 4, Rarity.EPIC, R.drawable.crosshilt_iron_sword, "Armament of the holy temple"),

            // L5
            weapon("silver_arming_sword", "Silver Arming Sword", WeaponType.SWORD, 5, Rarity.COMMON, R.drawable.silver_arming_sword, "A silver-plated standard arming sword"),
            weapon("silver_thin_sword", "Silver Thin Sword", WeaponType.SWORD, 5, Rarity.UNCOMMON, R.drawable.silver_thin_sword, "A slender silver sword"),
            weapon("silver_broadsword", "Silver Broadsword", WeaponType.SWORD, 5, Rarity.UNCOMMON, R.drawable.silver_broadsword, "A broad silver sword"),
            weapon("falchion", "Falchion", WeaponType.SWORD, 5, Rarity.RARE, R.drawable.falchion, "A single-edged blade with sharp cleaving power"),
            weapon("curved_machete", "Curved Machete", WeaponType.SWORD, 5, Rarity.RARE, R.drawable.curved_machete, "A sharp tool for cutting through jungle"),
            weapon("curved_saber", "Curved Saber", WeaponType.SWORD, 5, Rarity.RARE, R.drawable.curved_saber, "A curved military saber"),
            weapon("skull_pommel_sword", "Skull Pommel Sword", WeaponType.SWORD, 5, Rarity.EPIC, R.drawable.skull_pommel_sword, "Its pommel is carved into a skull"),
            weapon("mossy_blade_sword", "Mossy Blade Sword", WeaponType.SWORD, 5, Rarity.EPIC, R.drawable.mossy_blade_sword, "An ancient sword covered in moss yet still sharp"),

            // L6
            weapon("dark_steel_sword", "Dark Steel Sword", WeaponType.SWORD, 6, Rarity.COMMON, R.drawable.dark_steel_sword, "A dark-colored steel sword"),
            weapon("wide_steel_blade", "Wide Steel Blade", WeaponType.SWORD, 6, Rarity.UNCOMMON, R.drawable.wide_steel_blade, "A broad steel blade"),
            weapon("wide_cross_sword", "Wide Cross Sword", WeaponType.SWORD, 6, Rarity.UNCOMMON, R.drawable.wide_cross_sword, "A cross sword with a wide guard"),
            weapon("stone_blade_sword", "Stone Blade Sword", WeaponType.SWORD, 6, Rarity.RARE, R.drawable.stone_blade_sword, "A strange sword treated with petrification magic"),
            weapon("stoneforged_sword", "Stoneforged Sword", WeaponType.SWORD, 6, Rarity.RARE, R.drawable.stoneforged_sword, "Crafted with dwarven stone-forging techniques"),
            weapon("crossed_skull_swords", "Crossed Skull Swords", WeaponType.SWORD, 6, Rarity.EPIC, R.drawable.crossed_skull_swords, "A forbidden sword carved with crossed skulls"),
            weapon("frostblade_sword", "Frostblade Sword", WeaponType.SWORD, 6, Rarity.EPIC, R.drawable.frostblade_sword, "Its blade is forever coated in frost"),
            weapon("flame_sword", "Flame Sword", WeaponType.SWORD, 6, Rarity.EPIC, R.drawable.flame_sword, "Its blade burns with an undying fire"),

            // L7 — 引入 LEGENDARY
            weapon("silver_gold_hilt_sword", "Silver Gold Hilt Sword", WeaponType.SWORD, 7, Rarity.UNCOMMON, R.drawable.silver_gold_hilt_sword, "An ornate hilt interwoven with silver and gold"),
            weapon("gold_hilt_longsword", "Gold Hilt Longsword", WeaponType.SWORD, 7, Rarity.RARE, R.drawable.gold_hilt_longsword, "A pure gold hilt"),
            weapon("gold_edged_falchion", "Gold Edged Falchion", WeaponType.SWORD, 7, Rarity.RARE, R.drawable.gold_edged_falchion, "A falchion with a gold-plated edge"),
            weapon("knight_great_sword", "Knight Great Sword", WeaponType.SWORD, 7, Rarity.EPIC, R.drawable.knight_great_sword, "Reserved for the knight commander"),
            weapon("ice_engraved_sword", "Ice Engraved Sword", WeaponType.SWORD, 7, Rarity.EPIC, R.drawable.ice_engraved_sword, "Engraved with ice crystal patterns"),
            weapon("ice_etched_sword", "Ice Etched Sword", WeaponType.SWORD, 7, Rarity.EPIC, R.drawable.ice_etched_sword, "Its blade is eroded by extreme cold"),
            weapon("flame_runed_sword", "Flame Runed Sword", WeaponType.SWORD, 7, Rarity.LEGENDARY, R.drawable.flame_runed_sword, "A legendary sword inscribed with flame runes"),

            // L8
            weapon("gilded_runed_sword", "Gilded Runed Sword", WeaponType.SWORD, 8, Rarity.RARE, R.drawable.gilded_runed_sword, "A gilded guard carved with runes"),
            weapon("cyan_iceblade_sword", "Cyan Iceblade Sword", WeaponType.SWORD, 8, Rarity.EPIC, R.drawable.cyan_iceblade_sword, "Its blade emits a cyan icy glow"),
            weapon("blue_blade_sword", "Blue Blade Sword", WeaponType.SWORD, 8, Rarity.EPIC, R.drawable.blue_blade_sword, "A strange sword azure all over"),
            weapon("glowing_blue_runic_sword", "Glowing Blue Runic Sword", WeaponType.SWORD, 8, Rarity.EPIC, R.drawable.glowing_blue_runic_sword, "Its runes emit a blue radiance"),
            weapon("poison_sword", "Poison Sword", WeaponType.SWORD, 8, Rarity.EPIC, R.drawable.poison_sword, "Its blade is coated with deadly poison"),
            weapon("pink_magic_sword", "Pink Magic Sword", WeaponType.SWORD, 8, Rarity.LEGENDARY, R.drawable.pink_magic_sword, "A sword enchanted with pink magic"),
            weapon("rose_magic_sword", "Rose Magic Sword", WeaponType.SWORD, 8, Rarity.LEGENDARY, R.drawable.rose_magic_sword, "A magic sword entwined with rose vines"),

            // L9
            weapon("enchanted_sapphire_sword", "Enchanted Sapphire Sword", WeaponType.SWORD, 9, Rarity.RARE, R.drawable.enchanted_sapphire_sword, "A sword enchanted with sapphire"),
            weapon("red_etched_blade", "Red Etched Blade", WeaponType.SWORD, 9, Rarity.EPIC, R.drawable.red_etched_blade, "Its blade is etched with blood-red patterns"),
            weapon("blood_blade_sword", "Blood Blade Sword", WeaponType.SWORD, 9, Rarity.EPIC, R.drawable.blood_blade_sword, "A vicious sword that has drunk countless blood"),
            weapon("ruby_pommel_sword", "Ruby Pommel Sword", WeaponType.SWORD, 9, Rarity.EPIC, R.drawable.ruby_pommel_sword, "Its pommel is set with a ruby"),
            weapon("ruby_gem_sword", "Ruby Gem Sword", WeaponType.SWORD, 9, Rarity.LEGENDARY, R.drawable.ruby_gem_sword, "The whole hilt is studded with rubies"),
            weapon("sapphire_gem_sword", "Sapphire Gem Sword", WeaponType.SWORD, 9, Rarity.LEGENDARY, R.drawable.sapphire_gem_sword, "The whole hilt is studded with sapphires"),
            weapon("emerald_short_sword", "Emerald Short Sword", WeaponType.SWORD, 9, Rarity.LEGENDARY, R.drawable.emerald_short_sword, "A short sword carved from jade"),

            // L10 — 顶端
            weapon("emerald_sword", "Emerald Sword", WeaponType.SWORD, 10, Rarity.EPIC, R.drawable.emerald_sword, "A longsword forged from emerald"),
            weapon("teal_jewel_sword", "Teal Jewel Sword", WeaponType.SWORD, 10, Rarity.EPIC, R.drawable.teal_jewel_sword, "A precious sword set with teal jewels"),
            weapon("red_jewel_sword", "Red Jewel Sword", WeaponType.SWORD, 10, Rarity.EPIC, R.drawable.red_jewel_sword, "A precious sword set with crimson jewels"),
            weapon("amethyst_blade_sword", "Amethyst Blade Sword", WeaponType.SWORD, 10, Rarity.LEGENDARY, R.drawable.amethyst_blade_sword, "A blade forged from amethyst"),
            weapon("amethyst_starlight_sword", "Amethyst Starlight Sword", WeaponType.SWORD, 10, Rarity.LEGENDARY, R.drawable.amethyst_starlight_sword, "Its amethyst blade reflects starlight"),
            weapon("enchanted_starblade_sword", "Enchanted Starblade Sword", WeaponType.SWORD, 10, Rarity.LEGENDARY, R.drawable.enchanted_starblade_sword, "A legendary sword sealing a star soul"),
            weapon("diamond_crystal_sword", "Diamond Crystal Sword", WeaponType.SWORD, 10, Rarity.LEGENDARY, R.drawable.diamond_crystal_sword, "A treasure carved from diamond"),
            weapon("starlight_navy_sword", "Starlight Navy Sword", WeaponType.SWORD, 10, Rarity.LEGENDARY, R.drawable.starlight_navy_sword, "A deep blue blade studded with stars"),
            weapon("ruby_knight_sword", "Ruby Knight Sword", WeaponType.SWORD, 10, Rarity.LEGENDARY, R.drawable.ruby_knight_sword, "The legendary ruby sword of the holy knight")
        ) +

        // 匕首 (DAGGER) — 13 件
        listOf(
            weapon("small_iron_dagger", "Small Iron Dagger", WeaponType.DAGGER, 1, Rarity.COMMON, R.drawable.small_iron_dagger, "A small dagger easy to come by"),
            weapon("bronze_short_dagger", "Bronze Short Dagger", WeaponType.DAGGER, 1, Rarity.COMMON, R.drawable.bronze_short_dagger, "A short dagger cast from bronze"),
            weapon("iron_short_blade", "Iron Short Blade", WeaponType.DAGGER, 2, Rarity.COMMON, R.drawable.iron_short_blade, "A thin and sharp iron blade"),
            weapon("leather_grip_dagger", "Leather Grip Dagger", WeaponType.DAGGER, 2, Rarity.UNCOMMON, R.drawable.leather_grip_dagger, "Its leather-wrapped grip is firm and non-slip"),
            weapon("thin_combat_dagger", "Thin Combat Dagger", WeaponType.DAGGER, 3, Rarity.COMMON, R.drawable.thin_combat_dagger, "A thin dagger common on the battlefield"),
            weapon("iron_combat_dagger", "Iron Combat Dagger", WeaponType.DAGGER, 3, Rarity.UNCOMMON, R.drawable.iron_combat_dagger, "A military standard-issue combat dagger"),
            weapon("broad_dagger", "Broad Dagger", WeaponType.DAGGER, 4, Rarity.UNCOMMON, R.drawable.broad_dagger, "A broad-bladed dagger with sharp armor-piercing power"),
            weapon("hunting_dagger", "Hunting Dagger", WeaponType.DAGGER, 4, Rarity.RARE, R.drawable.hunting_dagger, "A sharp tool for hunters to skin and butcher"),
            weapon("sickle_blade_dagger", "Sickle Blade Dagger", WeaponType.DAGGER, 5, Rarity.RARE, R.drawable.sickle_blade_dagger, "An oddly shaped sickle-bladed dagger"),
            weapon("curved_throwing_dagger", "Curved Throwing Dagger", WeaponType.DAGGER, 5, Rarity.RARE, R.drawable.curved_throwing_dagger, "A curved dagger made for throwing"),
            weapon("kris_dagger", "Kris Dagger", WeaponType.DAGGER, 6, Rarity.EPIC, R.drawable.kris_dagger, "A ritual dagger with a wavy blade"),
            weapon("ornate_dagger", "Ornate Dagger", WeaponType.DAGGER, 7, Rarity.EPIC, R.drawable.ornate_dagger, "A noble's dagger with an ornately decorated hilt"),
            weapon("emerald_dagger", "Emerald Dagger", WeaponType.DAGGER, 8, Rarity.LEGENDARY, R.drawable.emerald_dagger, "A legendary dagger made of emerald")
        ) +

        // 矛 (SPEAR) — 暂用 empty 占位
        listOf(
            weapon("iron_spear", "Iron Spear", WeaponType.SPEAR, 3, Rarity.UNCOMMON, desc = "A standard-issue spear"),
            weapon("halberd", "Halberd", WeaponType.SPEAR, 6, Rarity.RARE, desc = "Capable of both cleaving and piercing")
        ) +

        // 弓 (BOW) — 游侠专属
        listOf(
            weapon("hunting_bow", "Hunting Bow", WeaponType.BOW, 2, Rarity.UNCOMMON, desc = "A reliable companion of forest hunters"),
            weapon("composite_bow", "Composite Bow", WeaponType.BOW, 5, Rarity.RARE, desc = "Pressed from multiple layers of material"),
            weapon("gale_bow", "Gale Bow", WeaponType.BOW, 8, Rarity.EPIC, desc = "Said to be granted by wind spirits")
        ) +

        // 法杖 (STAFF) — 法师专属
        listOf(
            weapon("wooden_staff", "Wooden Staff", WeaponType.STAFF, 1, Rarity.COMMON, desc = "A staff holding faint magical power"),
            weapon("crystal_staff", "Crystal Staff", WeaponType.STAFF, 4, Rarity.UNCOMMON, desc = "Set with a pure mana crystal"),
            weapon("arcane_scepter", "Arcane Scepter", WeaponType.STAFF, 7, Rarity.EPIC, desc = "A relic of an ancient archmage")
        ) +

        // 圣铃 (HOLY_BELL) — 圣职者专属
        listOf(
            weapon("wooden_bell", "Wooden Bell", WeaponType.HOLY_BELL, 1, Rarity.COMMON, desc = "A simple bell used by those who pray"),
            weapon("silver_bell", "Silver Bell", WeaponType.HOLY_BELL, 4, Rarity.UNCOMMON, desc = "Cast from pure silver with a clear, crisp ring"),
            weapon("holy_bell", "Holy Bell", WeaponType.HOLY_BELL, 6, Rarity.RARE, desc = "A blessed holy relic"),
            weapon("divine_chime", "Divine Chime", WeaponType.HOLY_BELL, 8, Rarity.EPIC, desc = "Legend says it was a bell dropped by an angel")
        ) +

        // 轻甲 (LIGHT_ARMOR)
        listOf(
            armor("leather_armor", "Leather Armor", WeaponType.LIGHT_ARMOR, 1, Rarity.COMMON, desc = "Lightweight basic protection"),
            armor("ranger_vest", "Ranger Vest", WeaponType.LIGHT_ARMOR, 3, Rarity.UNCOMMON, desc = "Balances protection and agility"),
            armor("mage_robe", "Mage Robe", WeaponType.LIGHT_ARMOR, 3, Rarity.UNCOMMON, desc = "A silk robe woven with magic patterns"),
            armor("shadow_cloak", "Shadow Cloak", WeaponType.LIGHT_ARMOR, 6, Rarity.RARE, desc = "The wearer's silhouette becomes blurred")
        ) +

        // 重甲 (HEAVY_ARMOR)
        listOf(
            armor("chain_mail", "Chain Mail", WeaponType.HEAVY_ARMOR, 3, Rarity.UNCOMMON, desc = "Heavy armor woven from iron rings"),
            armor("plate_armor", "Plate Armor", WeaponType.HEAVY_ARMOR, 6, Rarity.RARE, desc = "Full-body armor standard for the knight order"),
            armor("holy_plate", "Holy Plate", WeaponType.HEAVY_ARMOR, 8, Rarity.EPIC, desc = "Full-body armor with a holy blessing")
        ) +

        // 饰品 (ACCESSORY) — 唯一提供属性加成的装备
        listOf(
            accessory("iron_ring", "Iron Ring", 1, Rarity.COMMON, StatType.STR, desc = "A rough but sturdy iron band"),
            accessory("prayer_ring", "Prayer Ring", 3, Rarity.UNCOMMON, StatType.FTH, desc = "A holy relic of the devout"),
            accessory("agility_amulet", "Agility Amulet", 3, Rarity.UNCOMMON, StatType.DEX, desc = "The wearer becomes light as a swallow"),
            accessory("scholar_glasses", "Scholar Glasses", 3, Rarity.UNCOMMON, StatType.INT, desc = "Said to be a relic of some great archmage"),
            accessory("warriors_medal", "Warrior's Medal", 5, Rarity.RARE, StatType.STR, secondaryStat = StatType.DEX, desc = "A war medal that boosts morale"),
            accessory("life_pendant", "Life Pendant", 5, Rarity.RARE, StatType.FTH, secondaryStat = StatType.INT, desc = "An emerald gem radiates healing light"),
            accessoryAll("dragon_eye_amulet", "Dragon Eye Amulet", 9, Rarity.EPIC, desc = "Set with a dragon eye stone, boosting all stats")
        ) +

        // 消耗品 (CONSUMABLE) — 5 级血瓶 + 5 级蓝瓶, 等级 = 道具店上架等级
        listOf(
            // 生命药水: T1-T5
            consumable("health_potion",       "Health Potion",         "Restores 50 HP",  level = 1, rarity = Rarity.COMMON,   restoreHp = 50),
            consumable("mid_health_potion",   "Mid Health Potion",     "Restores 110 HP", level = 2, rarity = Rarity.COMMON,   restoreHp = 110),
            consumable("great_health_potion", "Greater Health Potion", "Restores 220 HP", level = 3, rarity = Rarity.UNCOMMON, restoreHp = 220),
            consumable("super_health_potion", "Super Health Potion",   "Restores 380 HP", level = 4, rarity = Rarity.RARE,     restoreHp = 380),
            consumable("elixir_of_vitality",  "Elixir of Vitality",    "Restores 600 HP", level = 5, rarity = Rarity.EPIC,     restoreHp = 600),
            // 魔法药水: T1-T5
            consumable("mana_potion",         "Mana Potion",           "Restores 30 MP",  level = 1, rarity = Rarity.COMMON,   restoreMp = 30),
            consumable("mid_mana_potion",     "Mid Mana Potion",       "Restores 70 MP",  level = 2, rarity = Rarity.COMMON,   restoreMp = 70),
            consumable("great_mana_potion",   "Greater Mana Potion",   "Restores 140 MP", level = 3, rarity = Rarity.UNCOMMON, restoreMp = 140),
            consumable("super_mana_potion",   "Super Mana Potion",     "Restores 240 MP", level = 4, rarity = Rarity.RARE,     restoreMp = 240),
            consumable("elixir_of_arcana",    "Elixir of Arcana",      "Restores 400 MP", level = 5, rarity = Rarity.EPIC,     restoreMp = 400)
        ) +

        // 材料 (MATERIAL) — 仅地下城掉落，仓库出售
        listOf(
            material("iron_ore", "Iron Ore", "A common forging material", level = 1),
            material("monster_bone", "Monster Bone", "Can fetch a good price", level = 1),
            material("slime_gel", "Slime Gel", "A sticky, semi-transparent jelly", level = 1),
            material("goblin_ear", "Goblin Ear", "Proof of a kill, exchangeable for bounty", level = 1),
            material("spider_silk", "Spider Silk", "Exceptionally tough spider silk", level = 2),
            material("ancient_coin", "Ancient Coin", "Ancient currency, bought at high prices by collectors", level = 4, rarity = Rarity.UNCOMMON),
            material("dark_shard", "Dark Shard", "A shard from a dark knight's armor", level = 5, rarity = Rarity.UNCOMMON),
            material("magic_crystal", "Magic Crystal", "A rare magical material", level = 5, rarity = Rarity.RARE),
            material("minotaur_horn", "Minotaur Horn", "A boss material as hard as iron", level = 7, rarity = Rarity.RARE),
            material("lich_phylactery", "Lich Phylactery Shard", "Contains powerful undead energy", level = 8, rarity = Rarity.EPIC),
            material("dragon_scale", "Dragon Scale", "A priceless dragon scale", level = 9, rarity = Rarity.EPIC)
        )
    ).associateBy { it.id }

    // 访问接口

    fun get(id: String): Item = items[id] ?: error("Unknown item: $id")
    fun all(): List<Item> = items.values.toList()
    fun byType(type: ItemType): List<Item> = items.values.filter { it.type == type }
    fun byRarity(rarity: Rarity): List<Item> = items.values.filter { it.rarity == rarity }

    /** 商店可刷出: 启用购买 + 等级不超建筑等级 */
    fun forShop(type: ItemType, shopLevel: Int): List<Item> =
        items.values.filter { it.type == type && it.purchasable && it.equipLevel <= shopLevel }

    /** 地下城可掉落: 启用掉落 + 等级不超指定上限 */
    fun lootableUpTo(maxLevel: Int): List<Item> =
        items.values.filter { it.lootable && it.equipLevel <= maxLevel }
}
