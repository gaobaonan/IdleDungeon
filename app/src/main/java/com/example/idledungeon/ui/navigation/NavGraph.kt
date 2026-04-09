package com.example.idledungeon.ui.navigation

/** 所有导航路由 - 集中管理，避免硬编码字符串 */
object Route {
    const val START = "start"
    const val TOWN = "town"

    // 城镇建筑 (从城镇界面点击进入)
    const val GUILD = "guild"
    const val TAVERN = "tavern"
    const val BLACKSMITH = "blacksmith"
    const val ITEM_SHOP = "item_shop"

    // 底部导航 (城镇界面底部三个按钮)
    const val ROSTER = "roster"
    const val INVENTORY = "inventory"
    const val BESTIARY = "bestiary"

    // 英雄详情 (从名录点击进入)
    const val HERO_DETAIL = "hero_detail/{heroId}"
    fun heroDetail(heroId: String) = "hero_detail/$heroId"

    // 怪物详情
    const val MONSTER_DETAIL = "monster_detail/{monsterId}"
    fun monsterDetail(monsterId: String) = "monster_detail/$monsterId"

    // 地下城相关 (从城镇大门进入)
    const val DUNGEON = "dungeon"
    const val PARTY_SETUP = "party_setup"
    const val TACTIC_SETUP = "tactic_setup"
}