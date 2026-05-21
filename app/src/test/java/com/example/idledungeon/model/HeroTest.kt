package com.example.idledungeon.model

import org.junit.Test
import org.junit.Assert.*

/**
 * Hero 的 attack / defense / combatPower 是派生公式属性 —
 * 测公式比测实际战斗结果稳得多, 写起来也快.
 */
class HeroTest {

    @Test
    fun `attack 公式`() {
        // 无武器: attack = STR + DEX/2
        val h = testHero(str = 10, dex = 4)
        assertEquals("无武器 attack = STR + DEX/2", 12, h.attack)
    }

    @Test
    fun `defense 公式`() {
        // 无护甲: defense = STR/2 + DEX/3
        val h = testHero(str = 10, dex = 6)
        assertEquals("无护甲 defense = STR/2 + DEX/3", 7, h.defense)
    }

    @Test
    fun `combatPower 公式`() {
        // attack=12, defense=6, maxMp=0, maxHp=100, 无法术
        // CP = (12*10 + 6*7 + 0*2 + 100) * 1.0 = 262
        val h = testHero(str = 10, dex = 4, hp = 100)
        assertEquals("CP = (atk*10 + def*7 + mp*2 + hp) * (1 + 法术数*0.2)", 262, h.combatPower)
    }

    private fun testHero(
        str: Int = 0, dex: Int = 0, hp: Int = 100, mp: Int = 0
    ): Hero = Hero(
        id = "t", name = "T", portraitUri = null,
        heroClass = HeroClass.WARRIOR,
        currentHp = hp, maxHp = hp, currentMp = mp, maxMp = mp,
        baseStats = mapOf(
            StatType.STR to str, StatType.DEX to dex,
            StatType.INT to 0, StatType.FTH to 0
        )
    )
}
