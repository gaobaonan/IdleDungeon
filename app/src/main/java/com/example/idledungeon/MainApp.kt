package com.example.idledungeon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.idledungeon.ui.navigation.Route
import com.example.idledungeon.ui.screen.*
import com.example.idledungeon.viewmodel.GameViewModel

/**
 * 全局导航节流：lifecycle 检查 + 时间防抖，防止动画期间重复导航
 */
private var lastNavTime = 0L
private const val NAV_DEBOUNCE_MS = 300L

private fun NavController.navigateSafe(route: String, builder: (androidx.navigation.NavOptionsBuilder.() -> Unit)? = null) {
    val now = System.currentTimeMillis()
    if (now - lastNavTime < NAV_DEBOUNCE_MS) return
    if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return
    lastNavTime = now
    if (builder != null) navigate(route, builder) else navigate(route)
}

private fun NavController.popBackStackSafe(): Boolean {
    val now = System.currentTimeMillis()
    if (now - lastNavTime < NAV_DEBOUNCE_MS) return false
    if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return false
    lastNavTime = now
    return popBackStack()
}

/**
 * 主程序入口 - Navigation Host
 * 所有屏幕的导航在此统一管理
 */
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val vm: GameViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    NavHost(navController = navController, startDestination = Route.START) {

        // ── 开始菜单 ──
        composable(Route.START) {
            StartScreen(
                hasExistingSave = state.gameState.hasStarted,
                onContinue = { navController.navigateSafe(Route.TOWN) { popUpTo(Route.START) { inclusive = true } } },
                onNewGame = {
                    vm.requestNewGame()
                    navController.navigateSafe(Route.TOWN) { popUpTo(Route.START) { inclusive = true } }
                },
                onExit = { /* 由 Activity 处 */ },
                onTestMode = {  // [DEBUG] 发布前删除
                    vm.startTestMode()
                    navController.navigateSafe(Route.TOWN) { popUpTo(Route.START) { inclusive = true } }
                }
            )
        }

        // ── 城镇主界面 ──
        composable(Route.TOWN) {
            LaunchedEffect(Unit) { vm.executePendingNewGame() }
            TownScreen(
                playerName = state.gameState.playerName,
                gold = state.gameState.guild.gold,
                onGuildClick = { navController.navigateSafe(Route.GUILD) },
                onTavernClick = { navController.navigateSafe(Route.TAVERN) },
                onBlacksmithClick = { navController.navigateSafe(Route.BLACKSMITH) },
                onItemShopClick = { navController.navigateSafe(Route.ITEM_SHOP) },
                onGateClick = { navController.navigateSafe(Route.DUNGEON) },
                onRosterClick = { navController.navigateSafe(Route.ROSTER) },
                onInventoryClick = { navController.navigateSafe(Route.INVENTORY) },
                onBestiaryClick = { navController.navigateSafe(Route.BESTIARY) }
            )
        }

        // ── 城镇建筑 ──
        composable(Route.GUILD) {
            GuildScreen(
                guildRank = state.gameState.guild.guildRank,
                guildLevel = state.gameState.guild.guildLevel,
                maxSupplies = state.gameState.guild.maxSupplies,
                quests = state.quests,
                investCost = vm.getInvestCost("guild"),
                onInvest = { vm.invest("guild") },
                onBack = { navController.popBackStackSafe() }
            )
        }

        composable(Route.TAVERN) {
            TavernScreen(
                tavernLevel = state.gameState.guild.tavernLevel,
                recruitableHeroes = state.recruitableHeroes,
                investCost = vm.getInvestCost("tavern"),
                onHeroClick = { navController.navigateSafe(Route.heroDetail(it)) },
                onRecruit = { vm.recruitHero(it) },
                onInvest = { vm.invest("tavern") },
                onBack = { navController.popBackStackSafe() }
            )
        }

        composable(Route.BLACKSMITH) {
            BlacksmithScreen(
                shopLevel = state.gameState.guild.blacksmithLevel,
                gold = state.gameState.guild.gold,
                availableItems = state.shopEquipments,
                investCost = vm.getInvestCost("blacksmith"),
                onBuy = { vm.buyItem(it) },
                onInvest = { vm.invest("blacksmith") },
                onRefresh = { vm.debugRefreshShops() },
                onBack = { navController.popBackStackSafe() }
            )
        }

        composable(Route.ITEM_SHOP) {
            ItemShopScreen(
                shopLevel = state.gameState.guild.itemShopLevel,
                gold = state.gameState.guild.gold,
                availableItems = state.shopConsumables,
                investCost = vm.getInvestCost("item_shop"),
                onBuy = { vm.buyItem(it) },
                onInvest = { vm.invest("item_shop") },
                onRefresh = { vm.debugRefreshShops() },
                onBack = { navController.popBackStackSafe() }
            )
        }

        // ── 名录 / 仓库 / 图鉴 ──
        composable(Route.ROSTER) {
            RosterScreen(
                heroes = state.heroes,
                onHeroClick = { navController.navigateSafe(Route.heroDetail(it)) },
                onBack = { navController.popBackStackSafe() }
            )
        }

        composable(Route.HERO_DETAIL) { backStackEntry ->
            val heroId = backStackEntry.arguments?.getString("heroId") ?: return@composable
            // 已招募 + 酒馆待招募 都能查看详情
            val hero = state.heroes.find { it.id == heroId }
                ?: state.recruitableHeroes.find { it.id == heroId }
                ?: return@composable
            val isRecruited = state.heroes.any { it.id == heroId }
            HeroDetailScreen(
                hero = hero,
                inventory = if (isRecruited) state.inventory else emptyList(),
                onBack = { navController.popBackStackSafe() },
                onEditName = { /* TODO: 弹出对话框 */ },
                onEditPortrait = { /* TODO: 调用图片选择器 */ },
                onChangeClass = { /* TODO: 弹出职业选择 */ },
                onEquip = { item -> if (isRecruited) vm.equipItem(heroId, item) },
                onUnequip = { slot -> if (isRecruited) vm.unequipItem(heroId, slot) },
                meetsRequirements = { h, i -> vm.meetsRequirements(h, i) },
                onAllocateStat = { stat -> vm.allocateStatPoint(heroId, stat) },
                onAllocateHp = { vm.allocateHpPoint(heroId) },
                onAllocateMp = { vm.allocateMpPoint(heroId) },
                onDebugAddProficiency = { if (isRecruited) vm.debugAddProficiency(heroId) }  // [DEBUG] 发布前删除
            )
        }

        composable(Route.INVENTORY) {
            InventoryScreen(
                items = state.inventory,
                gold = state.gameState.guild.gold,
                onSellOne = { vm.sellOne(it) },
                onSellAll = { vm.sellAll(it) },
                onBack = { navController.popBackStackSafe() }
            )
        }

        composable(Route.BESTIARY) {
            BestiaryScreen(
                entries = state.codexEntries,
                onMonsterClick = { /* TODO: 怪物详情 */ },
                onBack = { navController.popBackStackSafe() }
            )
        }

        // ── 地下城 ──
        composable(Route.DUNGEON) {
            DungeonScreen(
                floor = state.currentFloor,
                expedition = state.expedition,
                heroes = state.heroes,
                deepestFloor = state.gameState.deepestFloorReached,
                onDepart = { navController.navigateSafe(Route.PARTY_SETUP) },
                onBack = { navController.popBackStackSafe() },
                onDebugRefresh = { vm.debugPreviewMap() }  // [DEBUG] 发布前删除
            )
        }

        composable(Route.PARTY_SETUP) {
            PartySetupScreen(
                party = state.partySlots,
                availableHeroes = state.heroes.filter { hero ->
                    hero.status == com.example.idledungeon.model.HeroStatus.IDLE &&
                    state.partySlots.none { it.heroId == hero.id }
                },
                allHeroes = state.heroes.associateBy { it.id },
                onAddToParty = { id, pos -> vm.addToParty(id, pos) },
                onRemoveFromParty = { vm.removeFromParty(it) },
                onNext = { navController.navigateSafe(Route.TACTIC_SETUP) },
                onBack = { navController.popBackStackSafe() }
            )
        }

        composable(Route.TACTIC_SETUP) {
            TacticSetupScreen(
                selectedTactic = state.selectedTactic,
                selectedSecondaryTactic = state.selectedSecondaryTactic,
                consumables = state.inventory.filter { it.type == com.example.idledungeon.model.ItemType.CONSUMABLE },
                carriedItemIds = state.carriedItemIds,
                maxCarry = state.gameState.guild.maxCarryItems,
                onTacticChange = { vm.setTactic(it) },
                onSecondaryTacticChange = { vm.setSecondaryTactic(it) },
                onAddItem = { vm.addCarryItem(it) },
                onRemoveItem = { vm.removeCarryItem(it) },
                onConfirm = {
                    vm.startExpedition()
                    navController.popBackStack(Route.DUNGEON, inclusive = false)
                },
                onBack = { navController.popBackStackSafe() }
            )
        }
    }
}