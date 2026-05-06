package com.example.idledungeon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.idledungeon.ui.navigation.Route
import com.example.idledungeon.ui.screen.*
import com.example.idledungeon.viewmodel.GameViewModel

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

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val vm: GameViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) vm.checkDailyRefresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        vm.userMessages.collect { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHostBody(navController, vm, state)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun NavHostBody(
    navController: androidx.navigation.NavHostController,
    vm: GameViewModel,
    state: com.example.idledungeon.viewmodel.GameUiState
) {
    NavHost(navController = navController, startDestination = Route.START) {

        composable(Route.START) {
            StartScreen(
                hasExistingSave = state.gameState.hasStarted,
                onContinue = { navController.navigateSafe(Route.TOWN) { popUpTo(Route.START) { inclusive = true } } },
                onNewGame = {
                    vm.requestNewGame()
                    navController.navigateSafe(Route.TOWN) { popUpTo(Route.START) { inclusive = true } }
                },
                onExit = { }
            )
        }

        composable(Route.TOWN) {
            LaunchedEffect(Unit) { vm.executePendingNewGame() }
            TownScreen(
                playerName = state.gameState.playerName,
                gold = state.gameState.guild.gold,
                guildHasUnreadQuest = state.gameState.hasUnreadQuestUpdate,
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

        composable(Route.GUILD) {
            GuildScreen(
                guildLevel = state.gameState.guild.guildLevel,
                quests = state.quests,
                currentQuestIndex = state.gameState.mainQuestIndex,
                roomsExplored = state.gameState.roomsExplored,
                onMarkRead = { vm.markQuestUpdateRead() },
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
                onBack = { navController.popBackStackSafe() }
            )
        }

        composable(Route.ROSTER) {
            RosterScreen(
                heroes = state.heroes,
                onHeroClick = { navController.navigateSafe(Route.heroDetail(it)) },
                onBack = { navController.popBackStackSafe() }
            )
        }

        composable(Route.HERO_DETAIL) { backStackEntry ->
            val heroId = backStackEntry.arguments?.getString("heroId") ?: return@composable
            val hero = state.heroes.find { it.id == heroId }
                ?: state.recruitableHeroes.find { it.id == heroId }
                ?: return@composable
            val isRecruited = state.heroes.any { it.id == heroId }
            HeroDetailScreen(
                hero = hero,
                inventory = if (isRecruited) state.inventory else emptyList(),
                onBack = { navController.popBackStackSafe() },
                onRename = { newName -> if (isRecruited) vm.renameHero(heroId, newName) },
                onPortraitSelected = { uri -> if (isRecruited) vm.setHeroPortrait(heroId, uri) },
                onUpgradeSpell = { tid -> if (isRecruited) vm.upgradeSpellTier(heroId, tid) },
                onLearnSpell = { tid -> if (isRecruited) vm.learnSpell(heroId, tid) },
                onEquip = { item -> if (isRecruited) vm.equipItem(heroId, item) },
                onUnequip = { slot -> if (isRecruited) vm.unequipItem(heroId, slot) },
                meetsRequirements = { h, i -> vm.meetsRequirements(h, i) },
                onAllocateStat = { stat -> vm.allocateStatPoint(heroId, stat) },
                onAllocateHp = { vm.allocateHpPoint(heroId) },
                onAllocateMp = { vm.allocateMpPoint(heroId) }
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
                monsterKills = state.gameState.monsterKills,
                onBack = { navController.popBackStackSafe() }
            )
        }

        composable(Route.DUNGEON) {
            DungeonScreen(
                floor = state.currentFloor,
                expedition = state.expedition,
                heroes = state.heroes,
                deepestFloor = state.gameState.deepestFloorReached,
                onDepart = { navController.navigateSafe(Route.PARTY_SETUP) },
                onBack = { navController.popBackStackSafe() }
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
                defaultSupply = state.gameState.guild.defaultExpeditionSupply,
                purchasedSupply = state.purchasedExtraSupply,
                gold = state.gameState.guild.gold,
                onTacticChange = { vm.setTactic(it) },
                onSecondaryTacticChange = { vm.setSecondaryTactic(it) },
                onAddItem = { vm.addCarryItem(it) },
                onRemoveItem = { vm.removeCarryItem(it) },
                onBuySupply = { vm.buyExtraSupply() },
                onRefundSupply = { vm.refundExtraSupply() },
                onConfirm = {
                    vm.startExpedition()
                    navController.popBackStack(Route.DUNGEON, inclusive = false)
                },
                onBack = { navController.popBackStackSafe() }
            )
        }
    }
}
