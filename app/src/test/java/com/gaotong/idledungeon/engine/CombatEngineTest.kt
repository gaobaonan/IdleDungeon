package com.gaotong.idledungeon.engine

import com.gaotong.idledungeon.model.*
import org.junit.Test
import org.junit.Assert.*

/**
 * CombatEngine 是无状态 object, 测试相对简单 — 构造 hero/monster, 调 fight(), 断言结果.
 *
 * 难点: 战斗里有大量随机 (技能触发率、伤害、目标选择...). 没有 seed 注入,
 *      所以测试要么用"极端参数消除随机", 要么用统计型断言.
 */
class CombatEngineTest {

    // ═══════════════════════════════════════════════════════════════════════
    //  示例测试: 验证 fight() 在所有怪物 ID 都无效时, 走"自动判胜"防御分支.
    //
    //  为什么选这个作为入门: 不用真造怪物 (MonsterRegistry 里查不到的 ID 就够了),
    //  只需要构造一个英雄, 是结构最简单的一个测试.
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `fight 在所有怪物 ID 无效时应自动判胜`() {
        // Arrange (准备测试数据)
        // 用辅助构造函数 (见文件底部 testHero) 造一个英雄. 默认参数足够,
        // 这个测试不关心英雄数值, 只关心 fight() 的边界行为.
        val hero = testHero()

        // Act (调用被测函数)
        // 故意传入一个 MonsterRegistry 里不存在的 ID.
        // CombatEngine.fight() 内部会 try-catch 这种失效 ID, 全部跳过后若怪物列表为空,
        // 走"无有效怪物 → 自动判胜"分支 (见 CombatEngine.kt:90-97).
        val result = CombatEngine.fight(
            heroes = listOf(hero),
            monsterIds = listOf("__nonexistent_monster_id__"),
            carriedItems = emptyList()
        )

        // Assert (断言结果)
        // assertTrue/assertEquals 第一个参数是失败时的提示信息 (可选但强烈建议写),
        // 不写的话失败日志只显示 "expected true but was false", 看不出在测什么.
        assertTrue("所有怪物 ID 无效时, 战斗应自动判胜", result.victory)
        assertEquals("英雄列表数量应保留 (没人阵亡, 没人新增)", 1, result.updatedHeroes.size)
        assertTrue("无战斗发生 → 不应有掉落物", result.droppedItems.isEmpty())
    }

    @Test
    fun `空队伍自动失败`() {

        val result = CombatEngine.fight(
            heroes = emptyList(),
            monsterIds = listOf("anything"),
            carriedItems = emptyList()
        )

        // Assert (断言结果)
        // assertTrue/assertEquals 第一个参数是失败时的提示信息 (可选但强烈建议写),
        // 不写的话失败日志只显示 "expected true but was false", 看不出在测什么.
        assertFalse("无英雄，战斗胜利", result.victory)
        assertEquals("没有人，队伍为空", 0, result.updatedHeroes.size)
        assertTrue("无战斗发生 → 不应有掉落物", result.droppedItems.isEmpty())
    }

    @Test
    fun `必胜场景`() {
        val hero = testHero(str = 999)
        val result = CombatEngine.fight(listOf(hero), listOf("slime"))
        assertTrue("强英雄打弱怪应胜", result.victory)
    }

    @Test
    fun `必败场景`() {
        val hero = testHero(hp = 1)
        val result = CombatEngine.fight(listOf(hero), listOf("slime"))
        assertFalse("残血英雄应被打死", result.victory)
        assertEquals("英雄 HP 应归零", 0, result.updatedHeroes[0].currentHp)
    }

    // 站位加权统计: 前 3 / 后 1, 10 只低伤怪撑场, 100 回合上限保证样本量足够.
    @Test
    fun `前排比后排多挨打`() {
        val front = testHero(id = "front", hp = 20000, str = 0, dex = 0)
        val back = testHero(id = "back", hp = 20000, str = 0, dex = 0)
        val result = CombatEngine.fight(
            heroes = listOf(front, back),
            monsterIds = List(10) { "slime" },
            partyPositions = mapOf(front.id to Position.FRONT, back.id to Position.BACK)
        )
        val frontDmg = 20000 - result.updatedHeroes.first { it.id == "front" }.currentHp
        val backDmg = 20000 - result.updatedHeroes.first { it.id == "back" }.currentHp
        assertTrue("前排应明显多受伤 (front=$frontDmg, back=$backDmg)", frontDmg > backDmg * 1.5)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  测试辅助 (不是 @Test 方法, 不会被当成测试运行)
    //  Hero 字段很多, 每个 @Test 都手写一遍构造太烦. 这种辅助函数让测试只填关心的字段.
    // ═══════════════════════════════════════════════════════════════════════
    private fun testHero(
        id: String = "test_hero",
        hp: Int = 100,
        mp: Int = 0,
        str: Int = 10,
        dex: Int = 10,
        intStat: Int = 5,       // 不能直接叫 int — 与 Kotlin 关键字冲突
        fth: Int = 5,
        heroClass: HeroClass = HeroClass.WARRIOR
    ): Hero = Hero(
        id = id,
        name = "Tester",
        portraitUri = null,
        heroClass = heroClass,
        currentHp = hp,
        maxHp = hp,
        currentMp = mp,
        maxMp = mp,
        baseStats = mapOf(
            StatType.STR to str,
            StatType.DEX to dex,
            StatType.INT to intStat,
            StatType.FTH to fth
        )
    )
}
