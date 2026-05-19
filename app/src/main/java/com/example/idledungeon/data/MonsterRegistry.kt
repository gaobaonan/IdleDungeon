package com.example.idledungeon.data

import com.example.idledungeon.model.*

/**
 * 怪物注册表 — 按等级 + 三种体型重新设计
 *
 *   Type A 高攻低防  (1-2 技能, 中等血)
 *   Type B 攻防平均  (无技能, 中血)
 *   Type C 低攻高防  (无技能, 高血)
 *
 * 每个等级 (L1-L11) 三只, CP 目标 = 等级×80, 上下浮动 ±20。
 * 楼层 boss: 等级 = 楼层×3+1, 共 5 个 (L4 / L7 / L10 / L13 / L16)。
 *
 * CP = (ATK×10 + DEF×7 + HP) × (1 + 技能数×0.2)
 */
object MonsterRegistry {

    private val templates = listOf(
        // ──── L1 (target ~80) ────
        Monster(id = "giant_rat", name = "Giant Rat", level = 1,
            currentHp = 22, maxHp = 22, attack = 5, defense = 1,
            skills = listOf(MonsterSkill("sk_rat_bite", "Fang Bite", "A lethal bite", SkillEffect.HEAVY_STRIKE, 1.5f, 25)),
            dropChances = mapOf("monster_bone" to 30, "slime_gel" to 5)),
        Monster(id = "slime", name = "Slime", level = 1,
            currentHp = 30, maxHp = 30, attack = 3, defense = 3,
            dropChances = mapOf("slime_gel" to 50, "monster_bone" to 20)),
        Monster(id = "spike_shell", name = "Spike Shell Bug", level = 1,
            currentHp = 30, maxHp = 30, attack = 2, defense = 4,
            dropChances = mapOf("monster_bone" to 30, "iron_ore" to 10)),

        // ──── L2 (target ~160) ────
        Monster(id = "giant_bat", name = "Giant Bat", level = 2,
            currentHp = 40, maxHp = 40, attack = 9, defense = 2,
            // 原 sk_bat_drain (HEAL) 已删除, 蝙蝠现为无技能纯肉
            dropChances = mapOf("monster_bone" to 25, "spider_silk" to 5)),
        Monster(id = "goblin", name = "Goblin", level = 2,
            currentHp = 60, maxHp = 60, attack = 6, defense = 5,
            dropChances = mapOf("goblin_ear" to 40, "iron_ore" to 20, "small_iron_dagger" to 4)),
        Monster(id = "mushroom_brute", name = "Thick-Shelled Mushroom", level = 2,
            currentHp = 60, maxHp = 60, attack = 4, defense = 8,
            dropChances = mapOf("slime_gel" to 30, "monster_bone" to 25)),

        // ──── L3 (target ~240) ────
        Monster(id = "carrion_bat", name = "Carrion Bat", level = 3,
            currentHp = 55, maxHp = 55, attack = 14, defense = 4,
            skills = listOf(MonsterSkill("sk_carrion_strike", "Ambush", "A lunging surprise attack", SkillEffect.HEAVY_STRIKE, 1.6f, 30)),
            dropChances = mapOf("monster_bone" to 35, "health_potion" to 8)),
        Monster(id = "goblin_warrior", name = "Goblin Warrior", level = 3,
            currentHp = 90, maxHp = 90, attack = 10, defense = 7,
            dropChances = mapOf("goblin_ear" to 50, "iron_ore" to 30, "iron_shortsword" to 5)),
        Monster(id = "carapace_drone", name = "Carapace Drone", level = 3,
            currentHp = 100, maxHp = 100, attack = 6, defense = 12,
            dropChances = mapOf("monster_bone" to 35, "iron_ore" to 30, "spider_silk" to 15)),

        // ──── L4 (target ~320) ────
        Monster(id = "shadow_imp", name = "Shadow Imp", level = 4,
            currentHp = 80, maxHp = 80, attack = 19, defense = 5,
            skills = listOf(MonsterSkill("sk_imp_fireball", "Fireball", "Throws a small fireball", SkillEffect.HEAVY_STRIKE, 1.6f, 30)),
            dropChances = mapOf("dark_shard" to 15, "magic_crystal" to 10)),
        Monster(id = "skeleton_soldier", name = "Skeleton Soldier", level = 4,
            currentHp = 130, maxHp = 130, attack = 13, defense = 8,
            dropChances = mapOf("monster_bone" to 60, "iron_ore" to 30)),
        Monster(id = "iron_beetle", name = "Iron Beetle", level = 4,
            currentHp = 140, maxHp = 140, attack = 8, defense = 16,
            dropChances = mapOf("iron_ore" to 60, "monster_bone" to 30)),

        // ──── L5 (target ~400) ────
        Monster(id = "venom_spider", name = "Venom Spider", level = 5,
            currentHp = 120, maxHp = 120, attack = 23, defense = 5,
            // 原 sk_venom_fang (POISON) 已删除
            dropChances = mapOf("spider_silk" to 50, "mid_health_potion" to 8)),
        Monster(id = "ghoul", name = "Ghoul", level = 5,
            currentHp = 170, maxHp = 170, attack = 16, defense = 10,
            dropChances = mapOf("monster_bone" to 50, "mid_mana_potion" to 6, "ancient_coin" to 5)),
        Monster(id = "dwarven_guard", name = "Dwarven Guard", level = 5,
            currentHp = 170, maxHp = 170, attack = 9, defense = 20,
            dropChances = mapOf("iron_ore" to 50, "monster_bone" to 30, "chain_mail" to 4)),

        // ──── L6 (target ~480) ────
        Monster(id = "twin_blades", name = "Twin-Blade Assassin", level = 6,
            currentHp = 130, maxHp = 130, attack = 28, defense = 6,
            skills = listOf(MonsterSkill("sk_double_strike", "Double Strike", "Two consecutive blows", SkillEffect.HEAVY_STRIKE, 1.4f, 40)),
            dropChances = mapOf("spider_silk" to 30, "monster_bone" to 30, "dark_shard" to 10)),
        Monster(id = "orc_warrior", name = "Orc Warrior", level = 6,
            currentHp = 200, maxHp = 200, attack = 19, defense = 12,
            dropChances = mapOf("iron_ore" to 45, "monster_bone" to 35, "iron_long_sword" to 5)),
        Monster(id = "stone_guard", name = "Stone Guardian", level = 6,
            currentHp = 210, maxHp = 210, attack = 11, defense = 25,
            dropChances = mapOf("iron_ore" to 60, "monster_bone" to 30)),

        // ──── L7 (target ~560) ────
        Monster(id = "shadow_blade", name = "Shadow Blade", level = 7,
            currentHp = 145, maxHp = 145, attack = 32, defense = 7,
            skills = listOf(MonsterSkill("sk_assassinate", "Assassinate", "A lethal surprise strike", SkillEffect.HEAVY_STRIKE, 2.0f, 30)),
            dropChances = mapOf("dark_shard" to 30, "spider_silk" to 25, "magic_crystal" to 15)),
        Monster(id = "ogre", name = "Ogre", level = 7,
            currentHp = 240, maxHp = 240, attack = 22, defense = 14,
            dropChances = mapOf("monster_bone" to 55, "iron_ore" to 40, "knight_great_sword" to 4)),
        Monster(id = "citadel_guard", name = "Citadel Guard", level = 7,
            currentHp = 240, maxHp = 240, attack = 12, defense = 28,
            dropChances = mapOf("iron_ore" to 50, "monster_bone" to 35, "plate_armor" to 5)),

        // ──── L8 (target ~640) ────
        Monster(id = "demon_imp", name = "Demon Imp", level = 8,
            currentHp = 160, maxHp = 160, attack = 38, defense = 8,
            skills = listOf(MonsterSkill("sk_hellfire", "Hellfire", "The power of searing flame", SkillEffect.HEAVY_STRIKE, 1.8f, 30)),
            dropChances = mapOf("dark_shard" to 25, "magic_crystal" to 30, "lich_phylactery" to 3)),
        Monster(id = "werewolf", name = "Werewolf", level = 8,
            currentHp = 280, maxHp = 280, attack = 25, defense = 16,
            dropChances = mapOf("monster_bone" to 60, "spider_silk" to 25, "ancient_coin" to 15)),
        Monster(id = "iron_titan", name = "Iron Titan", level = 8,
            currentHp = 280, maxHp = 280, attack = 13, defense = 32,
            dropChances = mapOf("iron_ore" to 70, "monster_bone" to 40, "magic_crystal" to 15)),

        // ──── L9 (target ~720) ────
        Monster(id = "frost_wraith", name = "Frost Wraith", level = 9,
            currentHp = 175, maxHp = 175, attack = 35, defense = 9,
            skills = listOf(
                MonsterSkill("sk_ice_lance", "Ice Lance", "A sharp lance of ice", SkillEffect.HEAVY_STRIKE, 1.7f, 30),
                MonsterSkill("sk_frost_wail", "Frost Wail", "A chilling cry that freezes the soul", SkillEffect.STUN, 0.0f, 25)
            ),
            dropChances = mapOf("magic_crystal" to 35, "dark_shard" to 25, "ancient_coin" to 15)),
        Monster(id = "death_knight_lesser", name = "Death Knight", level = 9,
            currentHp = 320, maxHp = 320, attack = 29, defense = 18,
            dropChances = mapOf("dark_shard" to 50, "magic_crystal" to 30, "iron_long_sword" to 8)),
        Monster(id = "bone_golem", name = "Bone Golem", level = 9,
            currentHp = 320, maxHp = 320, attack = 15, defense = 36,
            dropChances = mapOf("monster_bone" to 80, "magic_crystal" to 25, "dark_shard" to 15)),

        // ──── L10 (target ~800) ────
        Monster(id = "shadow_dragon_whelp", name = "Shadow Dragon Whelp", level = 10,
            currentHp = 195, maxHp = 195, attack = 38, defense = 10,
            skills = listOf(
                MonsterSkill("sk_dark_breath", "Dark Breath", "A corrosive breath", SkillEffect.HEAVY_STRIKE, 2.0f, 30)
                // 原 sk_shadow_step (HEAL) 已删除
            ),
            dropChances = mapOf("dragon_scale" to 8, "magic_crystal" to 50, "dark_shard" to 30)),
        Monster(id = "abyssal_warrior", name = "Abyssal Warrior", level = 10,
            currentHp = 360, maxHp = 360, attack = 31, defense = 20,
            dropChances = mapOf("dark_shard" to 50, "magic_crystal" to 40, "ancient_coin" to 20)),
        Monster(id = "ancient_construct", name = "Ancient Construct", level = 10,
            currentHp = 360, maxHp = 360, attack = 17, defense = 40,
            dropChances = mapOf("iron_ore" to 80, "magic_crystal" to 30, "lich_phylactery" to 8)),

        // ──── L11 (target ~880) ────
        Monster(id = "hellfire_imp", name = "Hellfire Imp", level = 11,
            currentHp = 210, maxHp = 210, attack = 42, defense = 11,
            skills = listOf(
                MonsterSkill("sk_hellfire_blast", "Flame Blast", "A scorching flame strike", SkillEffect.HEAVY_STRIKE, 2.0f, 30)
                // 原 sk_burning_curse (POISON) 已删除
            ),
            dropChances = mapOf("magic_crystal" to 50, "dark_shard" to 40, "lich_phylactery" to 10, "flame_sword" to 5)),
        Monster(id = "crimson_lord", name = "Crimson Lord", level = 11,
            currentHp = 400, maxHp = 400, attack = 34, defense = 22,
            dropChances = mapOf("dark_shard" to 50, "magic_crystal" to 40, "dragon_scale" to 10)),
        Monster(id = "void_titan", name = "Void Titan", level = 11,
            currentHp = 400, maxHp = 400, attack = 18, defense = 44,
            dropChances = mapOf("magic_crystal" to 50, "dark_shard" to 30, "ancient_coin" to 25)),

        // ════════════════════════════════════════════════
        //  楼层 BOSS  (level = floor × 3 + 1, 3 技能, CP ≈ level × 150)
        // ════════════════════════════════════════════════

        // Floor 1 boss (L4, target ~600)
        Monster(id = "boss_rat_king", name = "Rat King", level = 4,
            currentHp = 150, maxHp = 150, attack = 16, defense = 12,
            skills = listOf(
                MonsterSkill("sk_king_bite", "Regal Bite", "A king's mighty bite", SkillEffect.HEAVY_STRIKE, 1.8f, 35),
                MonsterSkill("sk_rat_roar", "Rat King's Roar", "Strikes terror into all", SkillEffect.STUN, 0.0f, 25)
                // 原 sk_feast (HEAL) 已删除
            ),
            dropChances = mapOf("monster_bone" to 80, "slime_gel" to 60, "ancient_coin" to 30, "iron_ring" to 15)),

        // Floor 2 boss (L7, target ~1050)
        Monster(id = "boss_goblin_chieftain", name = "Goblin Chieftain", level = 7,
            currentHp = 250, maxHp = 250, attack = 28, defense = 20,
            skills = listOf(
                MonsterSkill("sk_chieftain_smash", "Hammer Smash", "A bone-crushing heavy blow", SkillEffect.HEAVY_STRIKE, 1.8f, 30),
                MonsterSkill("sk_war_cry", "War Cry", "A rallying cry of battle spirit", SkillEffect.STUN, 0.0f, 25)
                // 原 sk_rally_heal (HEAL) 已删除
            ),
            dropChances = mapOf("goblin_ear" to 100, "iron_ore" to 80, "magic_crystal" to 30, "iron_long_sword" to 15, "knight_great_sword" to 12)),

        // Floor 3 boss (L10, target ~1500)
        Monster(id = "boss_minotaur_lord", name = "Minotaur Lord", level = 10,
            currentHp = 380, maxHp = 380, attack = 40, defense = 25,
            skills = listOf(
                MonsterSkill("sk_charge", "Berserk Charge", "An unstoppable charge", SkillEffect.HEAVY_STRIKE, 2.0f, 30),
                MonsterSkill("sk_minotaur_roar", "War Cry", "A soul-shaking roar", SkillEffect.STUN, 0.0f, 25),
                MonsterSkill("sk_warlord_fury", "Warlord's Fury", "A furious cleaving strike", SkillEffect.HEAVY_STRIKE, 1.5f, 30)
            ),
            dropChances = mapOf("minotaur_horn" to 60, "magic_crystal" to 50, "ancient_coin" to 30, "knight_great_sword" to 15)),

        // Floor 4 boss (L13, target ~1950)
        Monster(id = "boss_death_general", name = "Death General", level = 13,
            currentHp = 450, maxHp = 450, attack = 55, defense = 32,
            skills = listOf(
                MonsterSkill("sk_death_blade", "Death Blade", "The reaper's blade falls", SkillEffect.HEAVY_STRIKE, 2.2f, 30),
                MonsterSkill("sk_death_aura", "Death Aura", "Tears the soul apart", SkillEffect.STUN, 1.0f, 25)
                // 原 sk_dark_curse (POISON) / sk_undead_call (HEAL) 已删除, 用 STUN 替代保留 boss 特色
            ),
            dropChances = mapOf("dark_shard" to 70, "magic_crystal" to 60, "lich_phylactery" to 25, "plate_armor" to 12)),

        // Floor 5 boss (L16, target ~2400)
        Monster(id = "boss_fire_drake", name = "Fire Drake", level = 16,
            currentHp = 550, maxHp = 550, attack = 70, defense = 38,
            skills = listOf(
                MonsterSkill("sk_fire_breath", "Flame Breath", "Scorches the heavens and earth", SkillEffect.HEAVY_STRIKE, 2.5f, 30),
                MonsterSkill("sk_wing_smash", "Wing Smash", "A stunning wing strike", SkillEffect.STUN, 0.8f, 25),
                MonsterSkill("sk_dragon_fury", "Dragon's Fury", "Uncontrollable rage", SkillEffect.HEAVY_STRIKE, 2.0f, 30)
            ),
            dropChances = mapOf("dragon_scale" to 50, "flame_sword" to 12, "magic_crystal" to 80, "lich_phylactery" to 20, "ruby_gem_sword" to 5))
    ).associateBy { it.id }

    /** 生成一个战斗用的怪物实例 (HP 已是实战值, 不再倍增) */
    fun spawn(id: String): Monster = templates[id]?.copy() ?: error("Unknown monster: $id")

    fun all(): List<Monster> = templates.values.toList()

    /** 楼层用: 按等级范围筛选普通怪物 (排除 Boss) */
    fun byLevelRange(minLevel: Int, maxLevel: Int): List<Monster> =
        templates.values.filter {
            it.level in minLevel..maxLevel && !it.id.startsWith("boss_")
        }

    /** 楼层用: 取指定等级的 Boss (如果存在) */
    fun bossForLevel(level: Int): Monster? =
        templates.values.firstOrNull { it.id.startsWith("boss_") && it.level == level }
}
