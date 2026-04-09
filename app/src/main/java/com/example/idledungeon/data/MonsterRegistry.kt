package com.example.idledungeon.data

import com.example.idledungeon.model.*

/**
 * 怪物注册表 - 集中定义所有怪物模板
 * 战斗时通过 MonsterRegistry.spawn("goblin") 获取一个可变副本
 */
object MonsterRegistry {

    private val templates = listOf(
        // ──── 浅层 (1-10层) ────
        Monster(
            id = "slime", name = "史莱姆", level = 1,
            currentHp = 20, maxHp = 20, attack = 4, defense = 1,
            dropChances = mapOf("monster_bone" to 30)
        ),
        Monster(
            id = "goblin", name = "哥布林", level = 2,
            currentHp = 35, maxHp = 35, attack = 8, defense = 3,
            dropChances = mapOf("iron_ore" to 20, "rusty_dagger" to 5)
        ),
        Monster(
            id = "skeleton", name = "骷髅兵", level = 3,
            currentHp = 40, maxHp = 40, attack = 10, defense = 6,
            skills = listOf(
                MonsterSkill("sk_bone_strike", "骨击", "用骨棒猛击", SkillEffect.HEAVY_STRIKE, 1.3f, 20)
            ),
            dropChances = mapOf("monster_bone" to 50, "short_sword" to 8)
        ),

        // ──── 中层 (11-25层) ────
        Monster(
            id = "orc", name = "兽人战士", level = 8,
            currentHp = 80, maxHp = 80, attack = 18, defense = 10,
            skills = listOf(
                MonsterSkill("sk_heavy_blow", "重锤", "蛮力一击", SkillEffect.HEAVY_STRIKE, 1.5f, 25)
            ),
            dropChances = mapOf("iron_ore" to 40, "chain_mail" to 3)
        ),
        Monster(
            id = "poison_spider", name = "毒蜘蛛", level = 6,
            currentHp = 50, maxHp = 50, attack = 12, defense = 4,
            skills = listOf(
                MonsterSkill("sk_venom", "毒牙", "注入剧毒", SkillEffect.POISON, 0.5f, 30)
            ),
            dropChances = mapOf("antidote" to 25)
        ),

        // ──── 深层 (26-50层) ────
        Monster(
            id = "dark_knight", name = "暗黑骑士", level = 15,
            currentHp = 150, maxHp = 150, attack = 30, defense = 20,
            skills = listOf(
                MonsterSkill("sk_dark_slash", "暗斩", "黑暗力量附着的斩击", SkillEffect.HEAVY_STRIKE, 1.8f, 30),
                MonsterSkill("sk_stun_bash", "盾击", "用盾牌猛击造成眩晕", SkillEffect.STUN, 0.8f, 15)
            ),
            dropChances = mapOf("long_sword" to 10, "plate_armor" to 5, "magic_crystal" to 15)
        ),

        // ──── Boss ────
        Monster(
            id = "boss_minotaur", name = "牛头人领主", level = 10,
            currentHp = 200, maxHp = 200, attack = 25, defense = 15,
            skills = listOf(
                MonsterSkill("sk_charge", "狂暴冲锋", "不可阻挡的冲撞", SkillEffect.HEAVY_STRIKE, 2.0f, 25),
                MonsterSkill("sk_roar", "战吼", "震慑敌人使其眩晕", SkillEffect.STUN, 0.0f, 20)
            ),
            dropChances = mapOf("greatsword" to 15, "magic_crystal" to 40)
        ),
        Monster(
            id = "boss_lich", name = "巫妖", level = 20,
            currentHp = 180, maxHp = 180, attack = 35, defense = 12,
            skills = listOf(
                MonsterSkill("sk_drain", "生命汲取", "吸取生命回复自身", SkillEffect.HEAL, 1.2f, 25),
                MonsterSkill("sk_curse", "诅咒", "诅咒目标持续受到伤害", SkillEffect.POISON, 0.8f, 30),
                MonsterSkill("sk_soul_blast", "灵魂冲击", "强大的魔法打击", SkillEffect.HEAVY_STRIKE, 2.2f, 20)
            ),
            dropChances = mapOf("wooden_staff" to 20, "prayer_ring" to 15, "magic_crystal" to 60)
        )
    ).associateBy { it.id }

    /** 生成一个战斗用的怪物实例 (copy 确保不污染模板) */
    fun spawn(id: String): Monster = templates[id]?.copy() ?: error("Unknown monster: $id")

    fun all(): List<Monster> = templates.values.toList()

    /** 按楼层范围筛选适合的怪物 */
    fun byLevelRange(minLevel: Int, maxLevel: Int): List<Monster> =
        templates.values.filter { it.level in minLevel..maxLevel }
}