package com.example.idledungeon.data

import com.example.idledungeon.model.*

/**
 * 怪物注册表 - 集中定义所有怪物模板
 * 战斗时通过 MonsterRegistry.spawn("goblin") 获取一个可变副本
 */
object MonsterRegistry {

    private val templates = listOf(
        // ──── 浅层 (1-5层) ────
        Monster(
            id = "slime", name = "史莱姆", level = 1,
            currentHp = 20, maxHp = 20, attack = 4, defense = 1,
            dropChances = mapOf("slime_gel" to 50, "monster_bone" to 20)
        ),
        Monster(
            id = "goblin", name = "哥布林", level = 2,
            currentHp = 35, maxHp = 35, attack = 8, defense = 3,
            dropChances = mapOf("goblin_ear" to 40, "iron_ore" to 20, "rusty_dagger" to 5)
        ),
        Monster(
            id = "skeleton", name = "骷髅兵", level = 3,
            currentHp = 40, maxHp = 40, attack = 10, defense = 6,
            skills = listOf(
                MonsterSkill("sk_bone_strike", "骨击", "用骨棒猛击", SkillEffect.HEAVY_STRIKE, 1.3f, 20)
            ),
            dropChances = mapOf("monster_bone" to 50, "short_sword" to 8, "ancient_coin" to 5)
        ),
        Monster(
            id = "giant_bat", name = "巨型蝙蝠", level = 2,
            currentHp = 25, maxHp = 25, attack = 7, defense = 2,
            dropChances = mapOf("monster_bone" to 25)
        ),
        Monster(
            id = "mushroom", name = "毒蘑菇", level = 3,
            currentHp = 30, maxHp = 30, attack = 6, defense = 3,
            skills = listOf(
                MonsterSkill("sk_spore", "孢子散布", "释放有毒孢子", SkillEffect.POISON, 0.4f, 35)
            ),
            dropChances = mapOf("antidote" to 30, "slime_gel" to 15)
        ),

        // ──── 中层 (6-15层) ────
        Monster(
            id = "poison_spider", name = "毒蜘蛛", level = 6,
            currentHp = 50, maxHp = 50, attack = 12, defense = 4,
            skills = listOf(
                MonsterSkill("sk_venom", "毒牙", "注入剧毒", SkillEffect.POISON, 0.5f, 30)
            ),
            dropChances = mapOf("spider_silk" to 40, "antidote" to 20)
        ),
        Monster(
            id = "orc", name = "兽人战士", level = 8,
            currentHp = 80, maxHp = 80, attack = 18, defense = 10,
            skills = listOf(
                MonsterSkill("sk_heavy_blow", "重锤", "蛮力一击", SkillEffect.HEAVY_STRIKE, 1.5f, 25)
            ),
            dropChances = mapOf("iron_ore" to 40, "chain_mail" to 3, "iron_ring" to 5)
        ),
        Monster(
            id = "lizardman", name = "蜥蜴人", level = 7,
            currentHp = 65, maxHp = 65, attack = 15, defense = 8,
            skills = listOf(
                MonsterSkill("sk_tail_whip", "尾击", "用尾巴横扫", SkillEffect.STUN, 0.6f, 20)
            ),
            dropChances = mapOf("iron_ore" to 25, "monster_bone" to 35, "iron_spear" to 4)
        ),
        Monster(
            id = "ghost", name = "怨灵", level = 9,
            currentHp = 55, maxHp = 55, attack = 20, defense = 3,
            skills = listOf(
                MonsterSkill("sk_wail", "怨嚎", "令人毛骨悚然的嚎叫", SkillEffect.STUN, 0.0f, 25),
                MonsterSkill("sk_life_drain", "吸魂", "吸取生者的精气", SkillEffect.HEAL, 0.8f, 20)
            ),
            dropChances = mapOf("magic_crystal" to 10, "ancient_coin" to 15)
        ),

        // ──── 深层 (16-30层) ────
        Monster(
            id = "dark_knight", name = "暗黑骑士", level = 15,
            currentHp = 150, maxHp = 150, attack = 30, defense = 20,
            skills = listOf(
                MonsterSkill("sk_dark_slash", "暗斩", "黑暗力量附着的斩击", SkillEffect.HEAVY_STRIKE, 1.8f, 30),
                MonsterSkill("sk_stun_bash", "盾击", "用盾牌猛击造成眩晕", SkillEffect.STUN, 0.8f, 15)
            ),
            dropChances = mapOf("dark_shard" to 35, "long_sword" to 10, "plate_armor" to 5, "magic_crystal" to 15)
        ),
        Monster(
            id = "golem", name = "石像鬼", level = 12,
            currentHp = 120, maxHp = 120, attack = 22, defense = 25,
            skills = listOf(
                MonsterSkill("sk_slam", "石拳", "坚硬的岩石拳头", SkillEffect.HEAVY_STRIKE, 1.6f, 20)
            ),
            dropChances = mapOf("iron_ore" to 60, "magic_crystal" to 8, "ancient_coin" to 10)
        ),
        Monster(
            id = "harpy", name = "鹰身女妖", level = 13,
            currentHp = 90, maxHp = 90, attack = 24, defense = 10,
            skills = listOf(
                MonsterSkill("sk_screech", "尖啸", "刺耳的叫声使人眩晕", SkillEffect.STUN, 0.0f, 30),
                MonsterSkill("sk_dive", "俯冲", "从高处猛扑而下", SkillEffect.HEAVY_STRIKE, 1.7f, 20)
            ),
            dropChances = mapOf("spider_silk" to 20, "agility_amulet" to 3, "monster_bone" to 30)
        ),

        // ──── Boss ────
        Monster(
            id = "boss_minotaur", name = "牛头人领主", level = 10,
            currentHp = 200, maxHp = 200, attack = 25, defense = 15,
            skills = listOf(
                MonsterSkill("sk_charge", "狂暴冲锋", "不可阻挡的冲撞", SkillEffect.HEAVY_STRIKE, 2.0f, 25),
                MonsterSkill("sk_roar", "战吼", "震慑敌人使其眩晕", SkillEffect.STUN, 0.0f, 20)
            ),
            dropChances = mapOf("minotaur_horn" to 50, "greatsword" to 15, "magic_crystal" to 40)
        ),
        Monster(
            id = "boss_lich", name = "巫妖", level = 20,
            currentHp = 180, maxHp = 180, attack = 35, defense = 12,
            skills = listOf(
                MonsterSkill("sk_drain", "生命汲取", "吸取生命回复自身", SkillEffect.HEAL, 1.2f, 25),
                MonsterSkill("sk_curse", "诅咒", "诅咒目标持续受到伤害", SkillEffect.POISON, 0.8f, 30),
                MonsterSkill("sk_soul_blast", "灵魂冲击", "强大的魔法打击", SkillEffect.HEAVY_STRIKE, 2.2f, 20)
            ),
            dropChances = mapOf("lich_phylactery" to 30, "arcane_scepter" to 10, "prayer_ring" to 15, "magic_crystal" to 60)
        ),
        Monster(
            id = "boss_dragon_whelp", name = "幼龙", level = 25,
            currentHp = 350, maxHp = 350, attack = 40, defense = 28,
            skills = listOf(
                MonsterSkill("sk_fire_breath", "吐息", "灼热的龙焰吞噬一切", SkillEffect.HEAVY_STRIKE, 2.5f, 20),
                MonsterSkill("sk_tail_slam", "龙尾横扫", "巨尾横扫前排", SkillEffect.STUN, 1.0f, 15),
                MonsterSkill("sk_dragon_roar", "龙吼", "令人腿软的威压", SkillEffect.STUN, 0.0f, 25)
            ),
            dropChances = mapOf("dragon_scale" to 40, "flame_blade" to 8, "dragon_eye_amulet" to 5, "magic_crystal" to 70)
        )
    ).associateBy { it.id }

    /** 生成一个战斗用的怪物实例 (copy 确保不污染模板) */
    fun spawn(id: String): Monster = templates[id]?.copy() ?: error("Unknown monster: $id")

    fun all(): List<Monster> = templates.values.toList()

    /** 按楼层范围筛选适合的怪物 */
    fun byLevelRange(minLevel: Int, maxLevel: Int): List<Monster> =
        templates.values.filter { it.level in minLevel..maxLevel }
}