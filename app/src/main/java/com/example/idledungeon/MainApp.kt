package com.example.idledungeon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.idledungeon.ui.navigation.Route
import com.example.idledungeon.ui.screen.*
import com.example.idledungeon.viewmodel.GameViewModel

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
                onContinue = { navController.navigate(Route.TOWN) { popUpTo(Route.START) { inclusive = true } } },
                onNewGame = {
                    vm.startNewGame()
                    navController.navigate(Route.TOWN) { popUpTo(Route.START) { inclusive = true } }
                },
                onExit = { /* 由 Activity 处理 */ }
            )
        }

        // ── 城镇主界面 ──
        composable(Route.TOWN) {
            TownScreen(
                playerName = state.gameState.playerName,
                gold = state.gameState.guild.gold,
                onGuildClick = { navController.navigate(Route.GUILD) },
                onTavernClick = { navController.navigate(Route.TAVERN) },
                onBlacksmithClick = { navController.navigate(Route.BLACKSMITH) },
                onItemShopClick = { navController.navigate(Route.ITEM_SHOP) },
                onGateClick = { navController.navigate(Route.DUNGEON) },
                onRosterClick = { navController.navigate(Route.ROSTER) },
                onInventoryClick = { navController.navigate(Route.INVENTORY) },
                onBestiaryClick = { navController.navigate(Route.BESTIARY) }
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
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.TAVERN) {
            TavernScreen(
                tavernLevel = state.gameState.guild.tavernLevel,
                recruitableHeroes = state.recruitableHeroes,
                investCost = vm.getInvestCost("tavern"),
                onHeroClick = { navController.navigate(Route.heroDetail(it)) },
                onRecruit = { vm.recruitHero(it) },
                onInvest = { vm.invest("tavern") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.BLACKSMITH) {
            BlacksmithScreen(
                shopLevel = state.gameState.guild.blacksmithLevel,
                availableItems = state.shopEquipments,
                investCost = vm.getInvestCost("blacksmith"),
                onBuy = { vm.buyItem(it) },
                onInvest = { vm.invest("blacksmith") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.ITEM_SHOP) {
            ItemShopScreen(
                shopLevel = state.gameState.guild.itemShopLevel,
                availableItems = state.shopConsumables,
                investCost = vm.getInvestCost("item_shop"),
                onBuy = { vm.buyItem(it) },
                onInvest = { vm.invest("item_shop") },
                onBack = { navController.popBackStack() }
            )
        }

        // ── 名录 / 仓库 / 图鉴 ──
        composable(Route.ROSTER) {
            RosterScreen(
                heroes = state.heroes,
                onHeroClick = { navController.navigate(Route.heroDetail(it)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.HERO_DETAIL) { backStackEntry ->
            val heroId = backStackEntry.arguments?.getString("heroId") ?: return@composable
            val hero = state.heroes.find { it.id == heroId } ?: return@composable
            HeroDetailScreen(
                hero = hero,
                onBack = { navController.popBackStack() },
                onEditName = { /* TODO: 弹出对话框 */ },
                onEditPortrait = { /* TODO: 调用图片选择器 */ },
                onChangeClass = { /* TODO: 弹出职业选择 */ },
                onChangeEquipment = { /* TODO: 弹出装备选择 */ }
            )
        }

        composable(Route.INVENTORY) {
            InventoryScreen(
                items = state.inventory,
                onItemClick = { /* TODO: 弹出物品详情 */ },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.BESTIARY) {
            BestiaryScreen(
                entries = state.codexEntries,
                onMonsterClick = { /* TODO: 怪物详情 */ },
                onBack = { navController.popBackStack() }
            )
        }

        // ── 地下城 ──
        composable(Route.DUNGEON) {
            DungeonScreen(
                floor = state.currentFloor,
                expedition = state.expedition,
                deepestFloor = state.gameState.deepestFloorReached,
                onDepart = { navController.navigate(Route.PARTY_SETUP) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.PARTY_SETUP) {
            PartySetupScreen(
                party = state.partySlots,
                availableHeroes = state.heroes.filter { it.status == com.example.idledungeon.model.HeroStatus.IDLE },
                allHeroes = state.heroes.associateBy { it.id },
                onAddToParty = { id, pos -> vm.addToParty(id, pos) },
                onRemoveFromParty = { vm.removeFromParty(it) },
                onNext = { navController.navigate(Route.TACTIC_SETUP) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.TACTIC_SETUP) {
            TacticSetupScreen(
                selectedTactic = state.selectedTactic,
                consumables = state.inventory.filter { it.type == com.example.idledungeon.model.ItemType.CONSUMABLE },
                carriedItemIds = state.carriedItemIds,
                maxCarry = state.gameState.guild.maxCarryItems,
                onTacticChange = { vm.setTactic(it) },
                onToggleItem = { vm.toggleCarryItem(it) },
                onConfirm = {
                    vm.startExpedition()
                    navController.popBackStack(Route.DUNGEON, inclusive = false)
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}