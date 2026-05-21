package com.example.idledungeon.model

import org.junit.Test
import org.junit.Assert.*

/**
 * Item 的价格 (goldCost / sellPrice) 是公式属性; init 块对稀有度+等级组合做约束.
 */
class ItemTest {

    @Test
    fun `goldCost 公式`() {
        // EQUIPMENT 基数 50, COMMON 倍率 1.0, level 3 → 50 * 3 * 1.0 = 150
        val item = Item(
            id = "i", name = "X",
            type = ItemType.EQUIPMENT,
            rarity = Rarity.COMMON,
            equipLevel = 3
        )
        assertEquals("装备价 = level * 基数 * 稀有度倍率", 150, item.goldCost)
    }

    @Test
    fun `sellPrice 为 goldCost 一半`() {
        val item = Item(
            id = "i", name = "X",
            type = ItemType.EQUIPMENT,
            equipLevel = 4
        )
        assertEquals("售价应为购买价的一半", item.goldCost / 2, item.sellPrice)
    }

    // init 块校验: EPIC 必须 ≥4 级 (LEGENDARY 必须 ≥7 级)
    @Test(expected = IllegalArgumentException::class)
    fun `EPIC 装备低于 4 级应拒绝构造`() {
        Item(
            id = "i", name = "X",
            type = ItemType.EQUIPMENT,
            rarity = Rarity.EPIC,
            equipLevel = 1
        )
    }
}
