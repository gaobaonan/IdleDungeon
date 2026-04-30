package com.example.idledungeon.data

import com.example.idledungeon.R
import com.example.idledungeon.model.*

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

    // ============================================================
    //  数值公式
    // ============================================================

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

    // ============================================================
    //  Builder helpers
    // ============================================================

    private fun weapon(
        id: String, name: String, type: WeaponType,
        level: Int, rarity: Rarity, icon: Int = R.drawable.empty,
        desc: String = "", purchasable: Boolean? = null, lootable: Boolean = true,
        traits: List<String> = emptyList()
    ): Item = Item(
        id = id, name = name, description = desc,
        type = ItemType.EQUIPMENT, rarity = rarity,
        weaponType = type, equipSlot = EquipSlot.WEAPON,
        equipLevel = level,
        baseValue = weaponPower(level, rarity),
        statRequirements = reqFor(type, level),
        purchasable = purchasable ?: (rarity == Rarity.COMMON || rarity == Rarity.UNCOMMON),
        lootable = lootable,
        iconRes = icon,
        traits = traits
    )

    private fun armor(
        id: String, name: String, type: WeaponType,
        level: Int, rarity: Rarity, icon: Int = R.drawable.empty,
        desc: String = "", purchasable: Boolean? = null, lootable: Boolean = true,
        traits: List<String> = emptyList()
    ): Item = Item(
        id = id, name = name, description = desc,
        type = ItemType.EQUIPMENT, rarity = rarity,
        weaponType = type, equipSlot = EquipSlot.ARMOR,
        equipLevel = level,
        baseValue = armorPower(level, rarity),
        statRequirements = reqFor(type, level),
        purchasable = purchasable ?: (rarity == Rarity.COMMON || rarity == Rarity.UNCOMMON),
        lootable = lootable,
        iconRes = icon,
        traits = traits
    )

    private fun accessory(
        id: String, name: String,
        level: Int, rarity: Rarity, primaryStat: StatType,
        icon: Int = R.drawable.empty,
        secondaryStat: StatType? = null,
        desc: String = "", purchasable: Boolean? = null, lootable: Boolean = true,
        traits: List<String> = emptyList()
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
            iconRes = icon,
            traits = traits
        )
    }

    private fun accessoryAll(
        id: String, name: String, level: Int, rarity: Rarity,
        icon: Int = R.drawable.empty, desc: String = "",
        purchasable: Boolean? = null, lootable: Boolean = true,
        traits: List<String> = emptyList()
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
            iconRes = icon,
            traits = traits
        )
    }

    private fun consumable(
        id: String, name: String, desc: String,
        level: Int = 1, rarity: Rarity = Rarity.COMMON,
        purchasable: Boolean = true, lootable: Boolean = false,
        restoreHp: Int = 0, restoreMp: Int = 0,
        traits: List<String> = emptyList()
    ): Item = Item(
        id = id, name = name, description = desc,
        type = ItemType.CONSUMABLE, rarity = rarity,
        equipLevel = level,
        purchasable = purchasable, lootable = lootable,
        iconRes = R.drawable.empty,
        restoreHp = restoreHp, restoreMp = restoreMp,
        traits = traits
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

    // ============================================================
    //  Item 列表
    // ============================================================

    private val items: Map<String, Item> = (
        // ── 剑 (SWORD) — 75 件，对应 res/drawable 中所有剑类图片 ──
        listOf(
            // L1 — 入门铁剑
            weapon("rusty_iron_sword", "锈蚀铁剑", WeaponType.SWORD, 1, Rarity.COMMON, R.drawable.rusty_iron_sword, "布满锈迹的旧剑"),
            weapon("basic_iron_sword", "朴铁剑", WeaponType.SWORD, 1, Rarity.COMMON, R.drawable.basic_iron_sword, "最基础的铁剑"),
            weapon("common_iron_sword", "普通铁剑", WeaponType.SWORD, 1, Rarity.COMMON, R.drawable.common_iron_sword, "随处可见的铁剑"),
            weapon("petite_iron_sword", "小铁剑", WeaponType.SWORD, 1, Rarity.COMMON, R.drawable.petite_iron_sword, "尺寸偏小的铁剑"),
            weapon("thin_short_sword", "细身短剑", WeaponType.SWORD, 1, Rarity.COMMON, R.drawable.thin_short_sword, "纤细的短剑"),
            weapon("iron_shortsword", "铁短剑", WeaponType.SWORD, 1, Rarity.UNCOMMON, R.drawable.iron_shortsword, "做工尚可的铁短剑"),
            weapon("gladius", "角斗士剑", WeaponType.SWORD, 1, Rarity.UNCOMMON, R.drawable.gladius, "古罗马角斗士爱用的短剑"),

            // L2
            weapon("plain_iron_arming_sword", "朴铁佩剑", WeaponType.SWORD, 2, Rarity.COMMON, R.drawable.plain_iron_arming_sword, "平凡的佩剑"),
            weapon("plain_steel_sword", "朴素钢剑", WeaponType.SWORD, 2, Rarity.COMMON, R.drawable.plain_steel_sword, "无饰的钢剑"),
            weapon("simple_steel_sword", "简易钢剑", WeaponType.SWORD, 2, Rarity.COMMON, R.drawable.simple_steel_sword, "工艺简单的钢剑"),
            weapon("cobbled_iron_sword", "拼接铁剑", WeaponType.SWORD, 2, Rarity.COMMON, R.drawable.cobbled_iron_sword, "由数块铁拼接而成"),
            weapon("traveler_iron_sword", "旅人铁剑", WeaponType.SWORD, 2, Rarity.UNCOMMON, R.drawable.traveler_iron_sword, "旅行者随身佩剑"),
            weapon("wooden_hilt_sword", "木柄剑", WeaponType.SWORD, 2, Rarity.UNCOMMON, R.drawable.wooden_hilt_sword, "握感舒适的木柄"),
            weapon("iron_long_sword", "铁制长剑", WeaponType.SWORD, 2, Rarity.UNCOMMON, R.drawable.iron_long_sword, "标准制式长剑"),

            // L3
            weapon("broad_iron_sword", "阔身铁剑", WeaponType.SWORD, 3, Rarity.COMMON, R.drawable.broad_iron_sword, "宽刃铁剑"),
            weapon("heavy_iron_sword", "重型铁剑", WeaponType.SWORD, 3, Rarity.COMMON, R.drawable.heavy_iron_sword, "重得有些笨拙"),
            weapon("rugged_iron_sword", "粗朴铁剑", WeaponType.SWORD, 3, Rarity.COMMON, R.drawable.rugged_iron_sword, "粗犷的铁剑"),
            weapon("common_arming_sword", "寻常佩剑", WeaponType.SWORD, 3, Rarity.UNCOMMON, R.drawable.common_arming_sword, "骑士的常备武器"),
            weapon("soldier_sword", "士兵剑", WeaponType.SWORD, 3, Rarity.UNCOMMON, R.drawable.soldier_sword, "正规军制式装备"),
            weapon("squire_sword", "侍从剑", WeaponType.SWORD, 3, Rarity.UNCOMMON, R.drawable.squire_sword, "训练有素的侍从所用"),
            weapon("studded_iron_sword", "钉饰铁剑", WeaponType.SWORD, 3, Rarity.RARE, R.drawable.studded_iron_sword, "剑柄镶嵌铆钉"),
            weapon("guardsman_sword", "卫兵剑", WeaponType.SWORD, 3, Rarity.RARE, R.drawable.guardsman_sword, "城卫军特制武器"),

            // L4 — 引入 EPIC
            weapon("long_iron_blade", "长身铁刃", WeaponType.SWORD, 4, Rarity.COMMON, R.drawable.long_iron_blade, "刃长一码的铁剑"),
            weapon("bronze_engraved_sword", "青铜雕纹剑", WeaponType.SWORD, 4, Rarity.UNCOMMON, R.drawable.bronze_engraved_sword, "青铜剑身雕有古纹"),
            weapon("adventurer_long_sword", "冒险者长剑", WeaponType.SWORD, 4, Rarity.UNCOMMON, R.drawable.adventurer_long_sword, "冒险者协会推荐"),
            weapon("bronze_longsword", "青铜长剑", WeaponType.SWORD, 4, Rarity.UNCOMMON, R.drawable.bronze_longsword, "古代青铜锻造"),
            weapon("wanderer_sword", "漫游者剑", WeaponType.SWORD, 4, Rarity.RARE, R.drawable.wanderer_sword, "漫游剑客的传家宝"),
            weapon("cross_guard_iron_sword", "十字护手剑", WeaponType.SWORD, 4, Rarity.RARE, R.drawable.cross_guard_iron_sword, "护手呈十字形"),
            weapon("crosshilt_iron_sword", "十字柄铁剑", WeaponType.SWORD, 4, Rarity.EPIC, R.drawable.crosshilt_iron_sword, "圣堂武装"),

            // L5
            weapon("silver_arming_sword", "银佩剑", WeaponType.SWORD, 5, Rarity.COMMON, R.drawable.silver_arming_sword, "镀银的标准佩剑"),
            weapon("silver_thin_sword", "银细剑", WeaponType.SWORD, 5, Rarity.UNCOMMON, R.drawable.silver_thin_sword, "纤薄的银剑"),
            weapon("silver_broadsword", "银阔剑", WeaponType.SWORD, 5, Rarity.UNCOMMON, R.drawable.silver_broadsword, "宽阔的银剑"),
            weapon("falchion", "弯刀", WeaponType.SWORD, 5, Rarity.RARE, R.drawable.falchion, "单刃弯刀，劈砍犀利"),
            weapon("curved_machete", "弯刃砍刀", WeaponType.SWORD, 5, Rarity.RARE, R.drawable.curved_machete, "丛林开路的利器"),
            weapon("curved_saber", "弧月军刀", WeaponType.SWORD, 5, Rarity.RARE, R.drawable.curved_saber, "弧形军刀"),
            weapon("skull_pommel_sword", "骷髅柄剑", WeaponType.SWORD, 5, Rarity.EPIC, R.drawable.skull_pommel_sword, "剑首雕成头骨"),
            weapon("mossy_blade_sword", "苔纹剑", WeaponType.SWORD, 5, Rarity.EPIC, R.drawable.mossy_blade_sword, "古剑被青苔覆盖却仍锐利"),

            // L6
            weapon("dark_steel_sword", "玄钢剑", WeaponType.SWORD, 6, Rarity.COMMON, R.drawable.dark_steel_sword, "暗黑色的钢剑"),
            weapon("wide_steel_blade", "阔身钢刃", WeaponType.SWORD, 6, Rarity.UNCOMMON, R.drawable.wide_steel_blade, "宽阔的钢刃"),
            weapon("wide_cross_sword", "宽十字剑", WeaponType.SWORD, 6, Rarity.UNCOMMON, R.drawable.wide_cross_sword, "宽护手十字剑"),
            weapon("stone_blade_sword", "石刃剑", WeaponType.SWORD, 6, Rarity.RARE, R.drawable.stone_blade_sword, "石化魔法处理的奇剑"),
            weapon("stoneforged_sword", "石锻剑", WeaponType.SWORD, 6, Rarity.RARE, R.drawable.stoneforged_sword, "矮人石锻工艺"),
            weapon("crossed_skull_swords", "双骷髅剑", WeaponType.SWORD, 6, Rarity.EPIC, R.drawable.crossed_skull_swords, "刻有交叉骷髅的禁忌之剑"),
            weapon("frostblade_sword", "霜刃剑", WeaponType.SWORD, 6, Rarity.EPIC, R.drawable.frostblade_sword, "刃身永远覆着寒霜"),
            weapon("flame_sword", "烈焰剑", WeaponType.SWORD, 6, Rarity.EPIC, R.drawable.flame_sword, "刃身燃烧着不灭之火"),

            // L7 — 引入 LEGENDARY
            weapon("silver_gold_hilt_sword", "银金柄剑", WeaponType.SWORD, 7, Rarity.UNCOMMON, R.drawable.silver_gold_hilt_sword, "银金交错的华丽剑柄"),
            weapon("gold_hilt_longsword", "金柄长剑", WeaponType.SWORD, 7, Rarity.RARE, R.drawable.gold_hilt_longsword, "纯金剑柄"),
            weapon("gold_edged_falchion", "金边弯刀", WeaponType.SWORD, 7, Rarity.RARE, R.drawable.gold_edged_falchion, "刃边镀金的弯刀"),
            weapon("knight_great_sword", "骑士巨剑", WeaponType.SWORD, 7, Rarity.EPIC, R.drawable.knight_great_sword, "骑士团长专用"),
            weapon("ice_engraved_sword", "寒霜雕剑", WeaponType.SWORD, 7, Rarity.EPIC, R.drawable.ice_engraved_sword, "雕有冰晶纹路"),
            weapon("ice_etched_sword", "冰蚀剑", WeaponType.SWORD, 7, Rarity.EPIC, R.drawable.ice_etched_sword, "刃身被极寒侵蚀"),
            weapon("flame_runed_sword", "焰纹剑", WeaponType.SWORD, 7, Rarity.LEGENDARY, R.drawable.flame_runed_sword, "刻满火焰符文的传说之剑"),

            // L8
            weapon("gilded_runed_sword", "鎏金符纹剑", WeaponType.SWORD, 8, Rarity.RARE, R.drawable.gilded_runed_sword, "鎏金护手雕刻符文"),
            weapon("cyan_iceblade_sword", "冰青之剑", WeaponType.SWORD, 8, Rarity.EPIC, R.drawable.cyan_iceblade_sword, "刃身散发青色寒光"),
            weapon("blue_blade_sword", "蓝刃剑", WeaponType.SWORD, 8, Rarity.EPIC, R.drawable.blue_blade_sword, "通体蔚蓝的奇剑"),
            weapon("glowing_blue_runic_sword", "蓝光符文剑", WeaponType.SWORD, 8, Rarity.EPIC, R.drawable.glowing_blue_runic_sword, "符文散发蓝色光芒"),
            weapon("poison_sword", "毒剑", WeaponType.SWORD, 8, Rarity.EPIC, R.drawable.poison_sword, "刃身淬有剧毒"),
            weapon("pink_magic_sword", "粉魔剑", WeaponType.SWORD, 8, Rarity.LEGENDARY, R.drawable.pink_magic_sword, "粉色魔法附魔之剑"),
            weapon("rose_magic_sword", "蔷薇魔剑", WeaponType.SWORD, 8, Rarity.LEGENDARY, R.drawable.rose_magic_sword, "缠绕蔷薇藤蔓的魔剑"),

            // L9
            weapon("enchanted_sapphire_sword", "附魔蓝晶剑", WeaponType.SWORD, 9, Rarity.RARE, R.drawable.enchanted_sapphire_sword, "蓝宝石附魔之剑"),
            weapon("red_etched_blade", "赤纹刃剑", WeaponType.SWORD, 9, Rarity.EPIC, R.drawable.red_etched_blade, "刃身雕刻血红纹路"),
            weapon("blood_blade_sword", "血刃剑", WeaponType.SWORD, 9, Rarity.EPIC, R.drawable.blood_blade_sword, "饮血无数的凶剑"),
            weapon("ruby_pommel_sword", "红宝柄剑", WeaponType.SWORD, 9, Rarity.EPIC, R.drawable.ruby_pommel_sword, "剑首镶嵌红宝石"),
            weapon("ruby_gem_sword", "红宝石剑", WeaponType.SWORD, 9, Rarity.LEGENDARY, R.drawable.ruby_gem_sword, "整柄镶满红宝石"),
            weapon("sapphire_gem_sword", "蓝宝石剑", WeaponType.SWORD, 9, Rarity.LEGENDARY, R.drawable.sapphire_gem_sword, "整柄镶满蓝宝石"),
            weapon("emerald_short_sword", "翡翠短剑", WeaponType.SWORD, 9, Rarity.LEGENDARY, R.drawable.emerald_short_sword, "翡翠雕琢的短剑"),

            // L10 — 顶端
            weapon("emerald_sword", "翡翠剑", WeaponType.SWORD, 10, Rarity.EPIC, R.drawable.emerald_sword, "翡翠铸成的长剑"),
            weapon("teal_jewel_sword", "翠玉剑", WeaponType.SWORD, 10, Rarity.EPIC, R.drawable.teal_jewel_sword, "镶嵌翠玉的宝剑"),
            weapon("red_jewel_sword", "朱玉剑", WeaponType.SWORD, 10, Rarity.EPIC, R.drawable.red_jewel_sword, "镶嵌朱红宝玉的宝剑"),
            weapon("amethyst_blade_sword", "紫晶刃", WeaponType.SWORD, 10, Rarity.LEGENDARY, R.drawable.amethyst_blade_sword, "紫水晶铸成的刃"),
            weapon("amethyst_starlight_sword", "紫晶星辰剑", WeaponType.SWORD, 10, Rarity.LEGENDARY, R.drawable.amethyst_starlight_sword, "紫晶剑身映出星辰之光"),
            weapon("enchanted_starblade_sword", "附魔星辰剑", WeaponType.SWORD, 10, Rarity.LEGENDARY, R.drawable.enchanted_starblade_sword, "封印一颗星魂的传说之剑"),
            weapon("diamond_crystal_sword", "钻晶剑", WeaponType.SWORD, 10, Rarity.LEGENDARY, R.drawable.diamond_crystal_sword, "钻石雕琢的至宝"),
            weapon("starlight_navy_sword", "星海剑", WeaponType.SWORD, 10, Rarity.LEGENDARY, R.drawable.starlight_navy_sword, "深蓝刃身缀满星辰"),
            weapon("ruby_knight_sword", "红宝骑士剑", WeaponType.SWORD, 10, Rarity.LEGENDARY, R.drawable.ruby_knight_sword, "传说中圣骑士的红宝佩剑")
        ) +

        // ── 匕首 (DAGGER) — 13 件 ──
        listOf(
            weapon("small_iron_dagger", "小铁匕", WeaponType.DAGGER, 1, Rarity.COMMON, R.drawable.small_iron_dagger, "随手可得的小匕"),
            weapon("bronze_short_dagger", "青铜短匕", WeaponType.DAGGER, 1, Rarity.COMMON, R.drawable.bronze_short_dagger, "青铜铸造的短匕"),
            weapon("iron_short_blade", "铁制短刃", WeaponType.DAGGER, 2, Rarity.COMMON, R.drawable.iron_short_blade, "薄而锋利的铁刃"),
            weapon("leather_grip_dagger", "皮柄匕首", WeaponType.DAGGER, 2, Rarity.UNCOMMON, R.drawable.leather_grip_dagger, "缠皮的握柄牢固防滑"),
            weapon("thin_combat_dagger", "细身战匕", WeaponType.DAGGER, 3, Rarity.COMMON, R.drawable.thin_combat_dagger, "战场常用的细匕"),
            weapon("iron_combat_dagger", "铁制战匕", WeaponType.DAGGER, 3, Rarity.UNCOMMON, R.drawable.iron_combat_dagger, "军用制式战斗匕"),
            weapon("broad_dagger", "阔身匕首", WeaponType.DAGGER, 4, Rarity.UNCOMMON, R.drawable.broad_dagger, "宽刃匕首，破甲犀利"),
            weapon("hunting_dagger", "狩猎匕", WeaponType.DAGGER, 4, Rarity.RARE, R.drawable.hunting_dagger, "猎人剥皮割肉的利器"),
            weapon("sickle_blade_dagger", "镰刃匕首", WeaponType.DAGGER, 5, Rarity.RARE, R.drawable.sickle_blade_dagger, "镰刀状的奇形匕首"),
            weapon("curved_throwing_dagger", "弧形飞刀", WeaponType.DAGGER, 5, Rarity.RARE, R.drawable.curved_throwing_dagger, "投掷专用的弧形匕"),
            weapon("kris_dagger", "波纹刃", WeaponType.DAGGER, 6, Rarity.EPIC, R.drawable.kris_dagger, "刃身呈波浪形的祭祀匕首"),
            weapon("ornate_dagger", "华饰匕首", WeaponType.DAGGER, 7, Rarity.EPIC, R.drawable.ornate_dagger, "刀柄华美雕饰的贵族匕首"),
            weapon("emerald_dagger", "翡翠匕首", WeaponType.DAGGER, 8, Rarity.LEGENDARY, R.drawable.emerald_dagger, "翡翠所制的传说匕首")
        ) +

        // ── 矛 (SPEAR) — 暂用 empty 占位 ──
        listOf(
            weapon("iron_spear", "铁矛", WeaponType.SPEAR, 3, Rarity.UNCOMMON, desc = "标准制式长矛"),
            weapon("halberd", "战戟", WeaponType.SPEAR, 6, Rarity.RARE, desc = "兼备劈砍与穿刺")
        ) +

        // ── 弓 (BOW) — 游侠专属 ──
        listOf(
            weapon("hunting_bow", "猎弓", WeaponType.BOW, 2, Rarity.UNCOMMON, desc = "森林猎人的可靠伙伴"),
            weapon("composite_bow", "复合弓", WeaponType.BOW, 5, Rarity.RARE, desc = "多层材料压制而成"),
            weapon("gale_bow", "疾风弓", WeaponType.BOW, 8, Rarity.EPIC, desc = "传说由风精灵赐予")
        ) +

        // ── 法杖 (STAFF) — 法师专属 ──
        listOf(
            weapon("wooden_staff", "木杖", WeaponType.STAFF, 1, Rarity.COMMON, desc = "蕴含微弱魔力的法杖"),
            weapon("crystal_staff", "水晶法杖", WeaponType.STAFF, 4, Rarity.UNCOMMON, desc = "镶嵌纯净魔力水晶"),
            weapon("arcane_scepter", "奥术权杖", WeaponType.STAFF, 7, Rarity.EPIC, desc = "古代魔导士的遗物")
        ) +

        // ── 圣铃 (HOLY_BELL) — 圣职者专属 ──
        listOf(
            weapon("wooden_bell", "木铃", WeaponType.HOLY_BELL, 1, Rarity.COMMON, desc = "祈祷者使用的简朴铃铛"),
            weapon("silver_bell", "银铃", WeaponType.HOLY_BELL, 4, Rarity.UNCOMMON, desc = "纯银铸造，铃声清越"),
            weapon("holy_bell", "圣铃", WeaponType.HOLY_BELL, 6, Rarity.RARE, desc = "受过祝福的圣物"),
            weapon("divine_chime", "神圣之铃", WeaponType.HOLY_BELL, 8, Rarity.EPIC, desc = "传说天使遗落的铃铛")
        ) +

        // ── 轻甲 (LIGHT_ARMOR) ──
        listOf(
            armor("leather_armor", "皮甲", WeaponType.LIGHT_ARMOR, 1, Rarity.COMMON, desc = "轻便的基础防具"),
            armor("ranger_vest", "游侠皮衣", WeaponType.LIGHT_ARMOR, 3, Rarity.UNCOMMON, desc = "兼顾防护与灵活"),
            armor("mage_robe", "法师长袍", WeaponType.LIGHT_ARMOR, 3, Rarity.UNCOMMON, desc = "织入魔纹的丝绸长袍"),
            armor("shadow_cloak", "暗影斗篷", WeaponType.LIGHT_ARMOR, 6, Rarity.RARE, desc = "穿戴者身影变得模糊")
        ) +

        // ── 重甲 (HEAVY_ARMOR) ──
        listOf(
            armor("chain_mail", "锁子甲", WeaponType.HEAVY_ARMOR, 3, Rarity.UNCOMMON, desc = "铁环编织的重甲"),
            armor("plate_armor", "板甲", WeaponType.HEAVY_ARMOR, 6, Rarity.RARE, desc = "骑士团标配的全身甲胄"),
            armor("holy_plate", "圣骑士铠甲", WeaponType.HEAVY_ARMOR, 8, Rarity.EPIC, desc = "受到神圣祝福的全身甲")
        ) +

        // ── 饰品 (ACCESSORY) — 唯一提供属性加成的装备 ──
        listOf(
            accessory("iron_ring", "铁戒指", 1, Rarity.COMMON, StatType.STR, desc = "粗糙但结实的铁环"),
            accessory("prayer_ring", "祈祷戒指", 3, Rarity.UNCOMMON, StatType.FTH, desc = "虔信者的圣物"),
            accessory("agility_amulet", "敏捷护符", 3, Rarity.UNCOMMON, StatType.DEX, desc = "佩戴者身轻如燕"),
            accessory("scholar_glasses", "学者眼镜", 3, Rarity.UNCOMMON, StatType.INT, desc = "据说是某位大魔导师的遗物"),
            accessory("warriors_medal", "武勋章", 5, Rarity.RARE, StatType.STR, secondaryStat = StatType.DEX, desc = "鼓舞士气的军功章"),
            accessory("life_pendant", "生命吊坠", 5, Rarity.RARE, StatType.FTH, secondaryStat = StatType.INT, desc = "翠绿宝石散发治愈之光"),
            accessoryAll("dragon_eye_amulet", "龙眼项链", 9, Rarity.EPIC, desc = "镶嵌龙眼石，全属性提升")
        ) +

        // ── 消耗品 (CONSUMABLE) — 5 级血瓶 + 5 级蓝瓶, 等级 = 道具店上架等级 ──
        listOf(
            // 生命药水: T1-T5
            consumable("health_potion",       "生命药水",       "回复 50 HP",  level = 1, rarity = Rarity.COMMON,   restoreHp = 50),
            consumable("mid_health_potion",   "中级生命药水",   "回复 110 HP", level = 2, rarity = Rarity.COMMON,   restoreHp = 110),
            consumable("great_health_potion", "高级生命药水",   "回复 220 HP", level = 3, rarity = Rarity.UNCOMMON, restoreHp = 220),
            consumable("super_health_potion", "极品生命药水",   "回复 380 HP", level = 4, rarity = Rarity.RARE,     restoreHp = 380),
            consumable("elixir_of_vitality",  "活力之精",       "回复 600 HP", level = 5, rarity = Rarity.EPIC,     restoreHp = 600),
            // 魔法药水: T1-T5
            consumable("mana_potion",         "魔力药水",       "回复 30 MP",  level = 1, rarity = Rarity.COMMON,   restoreMp = 30),
            consumable("mid_mana_potion",     "中级魔力药水",   "回复 70 MP",  level = 2, rarity = Rarity.COMMON,   restoreMp = 70),
            consumable("great_mana_potion",   "高级魔力药水",   "回复 140 MP", level = 3, rarity = Rarity.UNCOMMON, restoreMp = 140),
            consumable("super_mana_potion",   "极品魔力药水",   "回复 240 MP", level = 4, rarity = Rarity.RARE,     restoreMp = 240),
            consumable("elixir_of_arcana",    "奥术之精",       "回复 400 MP", level = 5, rarity = Rarity.EPIC,     restoreMp = 400)
        ) +

        // ── 材料 (MATERIAL) — 仅地下城掉落，仓库出售 ──
        listOf(
            material("iron_ore", "铁矿石", "常见的锻造材料", level = 1),
            material("monster_bone", "怪物骨头", "可以卖个好价钱", level = 1),
            material("slime_gel", "史莱姆凝胶", "黏糊糊的半透明胶质", level = 1),
            material("goblin_ear", "哥布林耳朵", "讨伐证明，可换赏金", level = 1),
            material("spider_silk", "蛛丝", "坚韧异常的蛛丝", level = 2),
            material("ancient_coin", "古代金币", "古代货币，收藏家高价收购", level = 4, rarity = Rarity.UNCOMMON),
            material("dark_shard", "暗黑碎片", "暗黑骑士铠甲的碎片", level = 5, rarity = Rarity.UNCOMMON),
            material("magic_crystal", "魔力结晶", "稀有的魔法材料", level = 5, rarity = Rarity.RARE),
            material("minotaur_horn", "牛头人角", "坚硬如铁的Boss素材", level = 7, rarity = Rarity.RARE),
            material("lich_phylactery", "巫妖命匣碎片", "蕴含强大亡灵能量", level = 8, rarity = Rarity.EPIC),
            material("dragon_scale", "龙鳞", "价值连城的龙鳞", level = 9, rarity = Rarity.EPIC)
        )
    ).associateBy { it.id }

    // ============================================================
    //  访问接口
    // ============================================================

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
