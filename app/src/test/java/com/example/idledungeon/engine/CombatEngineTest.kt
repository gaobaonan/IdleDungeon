package com.example.idledungeon.engine

import com.example.idledungeon.model.EquipSlot
import com.example.idledungeon.model.Hero
import com.example.idledungeon.model.HeroClass
import com.example.idledungeon.model.HeroSpell
import com.example.idledungeon.model.Item
import com.example.idledungeon.model.StatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CombatEngine 单元测试.
 *
 * 测试策略:
 *   - CombatEngine 内部直接调用 kotlin.random.Random.Default, 无法注入种子.
 *     ⇒ 用 "单怪/单英雄 + 数值极端化" 消除随机性
 *       (单怪时随机目标就是它本身, 单英雄时怪物目标也唯一)
 *   - 大量场景选用 slime/goblin 这类"无技能怪物", 避免 triggerChance 引入抖动
 *   - 战斗依赖 MonsterRegistry / SpellRegistry / ItemRegistry, 这些是 object 静态注册表,
 *     不需要 mock, 直接读真实数据
 *
 * 公式回顾 (来自 CombatEngine + GameModels):
 *   普攻伤害 = max(1, hero.attack - target.defense/2)
 *   法术伤害 = max(1, tier.power + (INT|FTH)/2 - target.defense/3)
 *   下一级所需总经验 = 50 * level * (level+1) / 2     (L1→L2 = 100)
 *   单怪掉落经验 = 10 + level * 8
 *   经验分配 = sum(monster.expReward) / aliveHeroes
 *   施法触发: monster.cp >= hero.cp * 0.8 && MP >= mpCost && 法术职业匹配
 *   喝瓶触发: currentHp/maxHp < 0.4  或  currentMp/maxMp < 0.4 (maxMp>0)
 */
class CombatEngineTest {

    // ─────────────────────────────────────────────────────────
    //  测试 Hero 工厂 (默认: 1级战士, 满血无蓝, 四维各10, 无装备)
    // ─────────────────────────────────────────────────────────
    private fun makeHero(
        id: String = "h1",
        name: String = "Test",
        heroClass: HeroClass = HeroClass.WARRIOR,
        level: Int = 1,
        exp: Int = 0,
        hp: Int = 100,
        maxHp: Int = 100,
        mp: Int = 0,
        maxMp: Int = 0,
        str: Int = 10,
        dex: Int = 10,
        intl: Int = 10,
        fth: Int = 10,
        spells: List<HeroSpell> = emptyList(),
        equipment: Map<EquipSlot, Item> = emptyMap()
    ): Hero = Hero(
        id = id,
        name = name,
        portraitUri = null,
        heroClass = heroClass,
        level = level,
        exp = exp,
        currentHp = hp,
        maxHp = maxHp,
        currentMp = mp,
        maxMp = maxMp,
        baseStats = mapOf(
            StatType.STR to str,
            StatType.DEX to dex,
            StatType.INT to intl,
            StatType.FTH to fth
        ),
        spells = spells,
        equipment = equipment
    )

    // =========================================================
    //  1. 边界保护 — 不合法输入不能让引擎崩
    // =========================================================

    @Test
    fun `空英雄队 - 判败 + 中止战斗`() {
        val r = CombatEngine.fight(emptyList(), listOf("slime"))
        assertFalse(r.victory)
        assertTrue(r.updatedHeroes.isEmpty())
        assertTrue("日志应说明是空队伍中止",
            r.log.any { "empty party" in it || "空队伍" in it })
    }

    @Test
    fun `全部无效怪物ID - 直接判胜, 不消耗任何东西`() {
        val hero = makeHero()
        val r = CombatEngine.fight(
            heroes = listOf(hero),
            monsterIds = listOf("not_a_monster", "also_fake"),
            carriedItems = listOf("health_potion")
        )
        assertTrue(r.victory)
        assertEquals(1, r.updatedHeroes.size)
        // 没掉血没用药
        assertEquals(hero.currentHp, r.updatedHeroes[0].currentHp)
        assertEquals(listOf("health_potion"), r.remainingCarriedItems)
        assertTrue(r.droppedItems.isEmpty())
    }

    @Test
    fun `部分无效怪物ID - 跳过坏的, 打有效的`() {
        // 强力英雄: STR 50 → attack ≈ 50 + 5 = 55, 一击杀 slime (HP30)
        val hero = makeHero(str = 50)
        val r = CombatEngine.fight(listOf(hero), listOf("not_a_monster", "slime"))
        assertTrue(r.victory)
        assertTrue("应记录击杀 slime", r.killedMonsterIds.contains("slime"))
    }

    // =========================================================
    //  2. 战斗基本流程
    // =========================================================

    @Test
    fun `强英雄秒杀史莱姆 - 胜利且未掉血(或仅掉一回合伤害)`() {
        // hero.attack = STR(50) + DEX(10)/2 + 0 = 55; slime.def = 3 → 伤害 55 - 1 = 54
        // slime 30 HP → 一回合击杀, 英雄先手 (英雄方先行动) → 不掉血
        val hero = makeHero(str = 50)
        val r = CombatEngine.fight(listOf(hero), listOf("slime"))
        assertTrue(r.victory)
        assertTrue(r.updatedHeroes[0].isAlive)
        assertEquals(100, r.updatedHeroes[0].currentHp)
        assertEquals(listOf("slime"), r.killedMonsterIds)
    }

    @Test
    fun `极弱英雄战败 - victory=false 且英雄死透`() {
        // 1 HP 的 STR=0 英雄 vs 火龙 boss → 必败
        val hero = makeHero(hp = 1, maxHp = 1, str = 0, dex = 0)
        val r = CombatEngine.fight(listOf(hero), listOf("boss_fire_drake"))
        assertFalse(r.victory)
        assertFalse(r.updatedHeroes[0].isAlive)
        assertEquals(0, r.updatedHeroes[0].currentHp)
    }

    @Test
    fun `战斗日志包含起始与结束标记`() {
        val hero = makeHero(str = 50)
        val r = CombatEngine.fight(listOf(hero), listOf("slime"))
        assertTrue("日志缺 Battle Start",
            r.log.any { "Battle Start" in it || "战斗开始" in it })
        assertTrue("日志缺胜负标记",
            r.log.any { "Victory" in it || "胜利" in it || "Injured" in it || "重伤" in it })
    }

    @Test
    fun `战斗结果包含活着的英雄列表 - 不会丢人`() {
        val heroes = (1..4).map { makeHero(id = "h$it", name = "h$it", str = 50) }
        val r = CombatEngine.fight(heroes, listOf("slime"))
        assertEquals(4, r.updatedHeroes.size)
        assertEquals(listOf("h1", "h2", "h3", "h4"), r.updatedHeroes.map { it.id })
    }

    // =========================================================
    //  3. 经验 & 升级
    // =========================================================

    @Test
    fun `胜利后单英雄独享全部经验`() {
        // slime expReward = 10 + 1*8 = 18
        val hero = makeHero(str = 50, exp = 0, level = 1)
        val r = CombatEngine.fight(listOf(hero), listOf("slime"))
        assertEquals(18, r.updatedHeroes[0].exp)
    }

    @Test
    fun `胜利后两个英雄均分经验`() {
        // 2 slime = 36 exp, 2 个活英雄 → 每人 18
        val h1 = makeHero(id = "h1", name = "h1", str = 50)
        val h2 = makeHero(id = "h2", name = "h2", str = 50)
        val r = CombatEngine.fight(listOf(h1, h2), listOf("slime", "slime"))
        assertTrue(r.victory)
        r.updatedHeroes.forEach { assertEquals(18, it.exp) }
    }

    @Test
    fun `战败不给经验`() {
        val hero = makeHero(hp = 1, maxHp = 1, str = 0)
        val r = CombatEngine.fight(listOf(hero), listOf("boss_fire_drake"))
        assertEquals(0, r.updatedHeroes[0].exp)
    }

    @Test
    fun `战士升级 - 加5属性点, 0技能点`() {
        // expToNextLevel(L1) = 50*1*2/2 = 50. 49 + slime(18) = 67 → 升 L2, 余 17
        val warrior = makeHero(heroClass = HeroClass.WARRIOR, level = 1, exp = 49, str = 50)
        val r = CombatEngine.fight(listOf(warrior), listOf("slime"))
        val after = r.updatedHeroes[0]
        assertEquals(2, after.level)
        assertEquals(17, after.exp)
        assertEquals(5, after.unspentStatPoints)
        assertEquals(0, after.unspentSkillPoints)
    }

    @Test
    fun `法师升级 - 加3属性点, 1技能点`() {
        val mage = makeHero(heroClass = HeroClass.MAGE, level = 1, exp = 49, str = 50)
        val r = CombatEngine.fight(listOf(mage), listOf("slime"))
        val after = r.updatedHeroes[0]
        assertEquals(2, after.level)
        assertEquals(3, after.unspentStatPoints)
        assertEquals(1, after.unspentSkillPoints)
    }

    @Test
    fun `游侠升级 - 同战士 +5属性 0技能`() {
        val ranger = makeHero(heroClass = HeroClass.RANGER, level = 1, exp = 49, str = 50)
        val r = CombatEngine.fight(listOf(ranger), listOf("slime"))
        assertEquals(5, r.updatedHeroes[0].unspentStatPoints)
        assertEquals(0, r.updatedHeroes[0].unspentSkillPoints)
    }

    @Test
    fun `牧师升级 - 同法师 +3属性 +1技能`() {
        val cleric = makeHero(heroClass = HeroClass.CLERIC, level = 1, exp = 49, str = 50)
        val r = CombatEngine.fight(listOf(cleric), listOf("slime"))
        assertEquals(3, r.updatedHeroes[0].unspentStatPoints)
        assertEquals(1, r.updatedHeroes[0].unspentSkillPoints)
    }

    // =========================================================
    //  4. 药水使用
    // =========================================================

    @Test
    fun `低血量自动喝血瓶 + 从背包扣除`() {
        // HP 20/100 = 20% < 40% → 触发喝瓶
        val hero = makeHero(hp = 20, maxHp = 100, str = 50)
        val r = CombatEngine.fight(
            heroes = listOf(hero),
            monsterIds = listOf("slime"),
            carriedItems = listOf("health_potion")
        )
        assertTrue(r.victory)
        assertFalse("血瓶应已被消耗", r.remainingCarriedItems.contains("health_potion"))
    }

    @Test
    fun `多种血瓶 - 优先选恢复量大的`() {
        // health_potion=50, great_health_potion=220 → 选 great
        val hero = makeHero(hp = 10, maxHp = 100, str = 50)
        val r = CombatEngine.fight(
            heroes = listOf(hero),
            monsterIds = listOf("slime"),
            carriedItems = listOf("health_potion", "great_health_potion")
        )
        assertTrue(r.victory)
        assertTrue("小瓶应留着", r.remainingCarriedItems.contains("health_potion"))
        assertFalse("大瓶应已喝掉", r.remainingCarriedItems.contains("great_health_potion"))
    }

    @Test
    fun `低血但没药水 - 直接攻击, 背包不变`() {
        val hero = makeHero(hp = 20, maxHp = 100, str = 50)
        val r = CombatEngine.fight(listOf(hero), listOf("slime"), carriedItems = emptyList())
        assertTrue(r.victory)
        assertTrue(r.remainingCarriedItems.isEmpty())
    }

    @Test
    fun `maxMp=0的战士不喝蓝瓶`() {
        // 战士天生 maxMp=0, 即便低血(为啥要喝蓝瓶), 蓝瓶判断会被 maxMp>0 拦住
        val warrior = makeHero(heroClass = HeroClass.WARRIOR, mp = 0, maxMp = 0, str = 50)
        val r = CombatEngine.fight(
            heroes = listOf(warrior),
            monsterIds = listOf("slime"),
            carriedItems = listOf("mana_potion")
        )
        assertEquals(listOf("mana_potion"), r.remainingCarriedItems)
    }

    @Test
    fun `MP低于40百分比 - 自动喝蓝瓶`() {
        // 法师 MP 5/100 = 5% → 喝蓝瓶
        val mage = makeHero(
            heroClass = HeroClass.MAGE,
            mp = 5, maxMp = 100,
            str = 50
        )
        val r = CombatEngine.fight(
            heroes = listOf(mage),
            monsterIds = listOf("slime"),
            carriedItems = listOf("mana_potion")
        )
        assertFalse("蓝瓶应已被喝掉", r.remainingCarriedItems.contains("mana_potion"))
    }

    @Test
    fun `血量充足时不喝瓶`() {
        // HP 100/100 = 100% > 40% → 不该喝
        val hero = makeHero(hp = 100, maxHp = 100, str = 50)
        val r = CombatEngine.fight(
            heroes = listOf(hero),
            monsterIds = listOf("slime"),
            carriedItems = listOf("health_potion")
        )
        assertEquals(listOf("health_potion"), r.remainingCarriedItems)
    }

    // =========================================================
    //  5. 法术决策
    // =========================================================

    @Test
    fun `MAGE对强敌主动施法 - MP 被扣`() {
        // mage CP << boss CP, 满 MP, 学了 fire (T1 mpCost=4)
        val mage = makeHero(
            heroClass = HeroClass.MAGE,
            hp = 100, maxHp = 100,
            mp = 50, maxMp = 100,
            intl = 30,
            spells = listOf(HeroSpell(templateId = "fire", currentTier = 1))
        )
        val r = CombatEngine.fight(listOf(mage), listOf("boss_fire_drake"))
        assertTrue("应有施法日志", r.log.any { "casts" in it })
        assertTrue("MP 应被消耗", r.updatedHeroes[0].currentMp < 50)
    }

    @Test
    fun `强英雄打弱敌 - 不施法 (省蓝)`() {
        // mage CP 远 > slime CP*0.8 → 不施
        val mage = makeHero(
            heroClass = HeroClass.MAGE,
            hp = 500, maxHp = 500,
            mp = 100, maxMp = 100,
            str = 200, intl = 50,
            spells = listOf(HeroSpell("fire", 1))
        )
        val r = CombatEngine.fight(listOf(mage), listOf("slime"))
        assertTrue(r.victory)
        assertEquals("MP 不应被动", 100, r.updatedHeroes[0].currentMp)
        assertFalse(r.log.any { "casts" in it })
    }

    @Test
    fun `MAGE 装了 MIRACLE 法术也不能施`() {
        // 即便条件全满足, 职业不匹配也会跳过
        val mage = makeHero(
            heroClass = HeroClass.MAGE,
            mp = 100, maxMp = 100,
            spells = listOf(HeroSpell("heal", 1))   // heal 是 MIRACLE
        )
        val r = CombatEngine.fight(listOf(mage), listOf("boss_fire_drake"))
        assertEquals(100, r.updatedHeroes[0].currentMp)
        assertFalse(r.log.any { "casts" in it })
    }

    @Test
    fun `CLERIC 装了 MAGIC 法术也不能施`() {
        val cleric = makeHero(
            heroClass = HeroClass.CLERIC,
            mp = 100, maxMp = 100,
            spells = listOf(HeroSpell("fire", 1))   // fire 是 MAGIC
        )
        val r = CombatEngine.fight(listOf(cleric), listOf("boss_fire_drake"))
        assertEquals(100, r.updatedHeroes[0].currentMp)
        assertFalse(r.log.any { "casts" in it })
    }

    @Test
    fun `WARRIOR 装了法术也无效 (战士不能施法)`() {
        val warrior = makeHero(
            heroClass = HeroClass.WARRIOR,
            mp = 100, maxMp = 100,
            spells = listOf(HeroSpell("fire", 1))
        )
        val r = CombatEngine.fight(listOf(warrior), listOf("boss_fire_drake"))
        assertEquals(100, r.updatedHeroes[0].currentMp)
        assertFalse(r.log.any { "casts" in it })
    }

    @Test
    fun `MP 不足以放最低阶法术 - 不施`() {
        // fire T1 mpCost=4, 给 mage 3 MP
        // 注: maxMp=10 → 30% < 40% 触发喝瓶判断, 但不带瓶 → 跳过, 进施法判断
        val mage = makeHero(
            heroClass = HeroClass.MAGE,
            hp = 100, maxHp = 100,
            mp = 3, maxMp = 10,
            spells = listOf(HeroSpell("fire", 1))
        )
        val r = CombatEngine.fight(listOf(mage), listOf("boss_fire_drake"))
        assertFalse(r.log.any { "casts" in it })
        assertEquals("MP 没变", 3, r.updatedHeroes[0].currentMp)
    }

    // =========================================================
    //  6. 多英雄团队
    // =========================================================

    @Test
    fun `4人小队全员存活胜利`() {
        val heroes = (1..4).map { i ->
            makeHero(id = "h$i", name = "h$i", str = 50, hp = 100, maxHp = 100)
        }
        val r = CombatEngine.fight(heroes, listOf("slime"))
        assertTrue(r.victory)
        assertEquals(4, r.updatedHeroes.size)
        assertTrue(r.updatedHeroes.all { it.isAlive })
    }

    @Test
    fun `死掉的队友不分经验`() {
        // h1 活, h2 已经是 0HP. slime 给 18 exp / 1 活英雄 = 18
        val alive = makeHero(id = "h1", name = "h1", str = 50)
        val dead = makeHero(id = "h2", name = "h2", hp = 0, maxHp = 100, str = 50)
        val r = CombatEngine.fight(listOf(alive, dead), listOf("slime"))
        assertTrue(r.victory)
        assertEquals(18, r.updatedHeroes[0].exp)
        assertEquals("死人不分 exp", 0, r.updatedHeroes[1].exp)
    }

    @Test
    fun `英雄顺序在返回列表中保持不变`() {
        val ids = listOf("alice", "bob", "carol")
        val heroes = ids.map { makeHero(id = it, name = it, str = 50) }
        val r = CombatEngine.fight(heroes, listOf("slime"))
        assertEquals(ids, r.updatedHeroes.map { it.id })
    }

    // =========================================================
    //  7. 击杀记录 (图鉴用)
    // =========================================================

    @Test
    fun `killedMonsterIds 记录每只被击杀怪物的ID`() {
        val hero = makeHero(str = 100, hp = 200, maxHp = 200)
        val r = CombatEngine.fight(listOf(hero), listOf("slime", "slime"))
        assertEquals(2, r.killedMonsterIds.size)
        assertTrue(r.killedMonsterIds.all { it == "slime" })
    }

    @Test
    fun `战败且未杀死任何怪 - killedMonsterIds 为空`() {
        val weak = makeHero(hp = 1, maxHp = 1, str = 0)
        val r = CombatEngine.fight(listOf(weak), listOf("boss_fire_drake"))
        assertFalse(r.victory)
        assertTrue(r.killedMonsterIds.isEmpty())
    }

    // =========================================================
    //  8. 不可变性 (入参不能被偷偷改)
    // =========================================================

    @Test
    fun `战斗不会修改传入的 Hero 实例`() {
        val hero = makeHero(hp = 100, maxHp = 100, str = 1) // 弱英雄, 估计要挨打
        val snapshotHp = hero.currentHp
        val snapshotExp = hero.exp
        CombatEngine.fight(listOf(hero), listOf("boss_fire_drake"))
        // Hero 是 data class (val), 字段不可变 — 此断言文档化这一保证
        assertEquals(snapshotHp, hero.currentHp)
        assertEquals(snapshotExp, hero.exp)
    }

    @Test
    fun `战斗不会修改传入的 carriedItems 列表`() {
        val hero = makeHero(hp = 20, maxHp = 100, str = 50)
        val bag = listOf("health_potion")
        val r = CombatEngine.fight(listOf(hero), listOf("slime"), carriedItems = bag)
        // 调用方传进来的列表保持原样, 返回的 remainingCarriedItems 才反映扣减
        assertEquals(listOf("health_potion"), bag)
        assertNotNull(r.remainingCarriedItems)
    }
}
