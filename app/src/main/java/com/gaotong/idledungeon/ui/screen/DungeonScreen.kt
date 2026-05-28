package com.gaotong.idledungeon.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gaotong.idledungeon.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DungeonScreen(
    floor: DungeonFloor?,
    expedition: ExpeditionSession?,
    heroes: List<Hero>,
    deepestFloor: Int,
    onDepart: () -> Unit,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // 订阅事件总线: 探险结束时弹 Snackbar 通知玩家
    LaunchedEffect(Unit) {
        com.gaotong.idledungeon.notification.ExpeditionEventBus.events.collect { event ->
            if (event is com.gaotong.idledungeon.notification.ExpeditionEvent.Retreated) {
                snackbarHostState.showSnackbar(
                    message = "Expedition ended: ${event.reason} — returned to town",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Dungeon") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 上半: 地图
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (floor != null) {
                    DungeonMapCanvas(floor, expedition)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No map data",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                // 状态浮窗 (仅层数/补给，角色状态移到日志顶部)
                if (expedition != null) {
                    Card(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        )
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val status = if (expedition.isExploring) "Exploring" else "Idle"
                            Text(status, style = MaterialTheme.typography.labelMedium)
                            Text("F${expedition.currentLayer}", style = MaterialTheme.typography.labelMedium)
                            Text("Supply:${expedition.currentSupply}/${expedition.maxSupply}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            HorizontalDivider()

            // 中间: 队伍血蓝条 (固定在地图与日志之间)
            if (expedition != null && expedition.party.isNotEmpty()) {
                PartyHpMpBars(expedition, heroes)
                HorizontalDivider()
            }

            // 下半: 日志区
            val logEntries = expedition?.combatLog ?: emptyList()
            val eventCards = remember(logEntries.size) { parseLogToCards(logEntries) }
            var detailCard by remember { mutableStateOf<EventCardData?>(null) }

            if (expedition != null && logEntries.isNotEmpty()) {
                val listState = rememberLazyListState()
                LaunchedEffect(eventCards.size) {
                    if (eventCards.isNotEmpty()) {
                        listState.animateScrollToItem(eventCards.size - 1)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(eventCards, key = { it.index }) { card ->
                        EventCard(card) { detailCard = card }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No expedition log",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline)
                }
            }

            // 出发按钮
            if (expedition == null || !expedition.isExploring) {
                Button(
                    onClick = onDepart,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) { Text("Depart") }
            }

            // 事件详情临时页
            detailCard?.let { card ->
                EventDetailDialog(card) { detailCard = null }
            }
        }
    }
}

// 队伍血蓝条 (固定在地图和日志之间)

@Composable
private fun PartyHpMpBars(expedition: ExpeditionSession, allHeroes: List<Hero>) {
    val partyHeroes = expedition.party.mapNotNull { slot ->
        allHeroes.find { it.id == slot.heroId }
    }
    if (partyHeroes.isEmpty()) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            partyHeroes.forEach { hero ->
                HeroHpMpRow(hero)
            }
            // 携带物品 + 背包 (压缩成单行)
            val carryText = expedition.carriedItems.takeIf { it.isNotEmpty() }
                ?.groupingBy { it }?.eachCount()
                ?.entries?.joinToString("  ") { (id, c) ->
                    val n = try { com.gaotong.idledungeon.data.ItemRegistry.get(id).name } catch (_: Exception) { id }
                    if (c > 1) "${n}x$c" else n
                }
            val lootText = expedition.backpack.takeIf { it.isNotEmpty() }
                ?.groupingBy { it }?.eachCount()
                ?.entries?.joinToString("  ") { (id, c) ->
                    val n = try { com.gaotong.idledungeon.data.ItemRegistry.get(id).name } catch (_: Exception) { id }
                    if (c > 1) "${n}x$c" else n
                }
            if (carryText != null) {
                Text("Carry: $carryText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (lootText != null) {
                Text("Backpack: $lootText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun HeroHpMpRow(hero: Hero) {
    val hpRatio = if (hero.maxHp > 0) hero.currentHp.toFloat() / hero.maxHp else 0f
    val mpRatio = if (hero.maxMp > 0) hero.currentMp.toFloat() / hero.maxMp else 0f
    val hpColor = when {
        hpRatio > 0.6f -> Color(0xFF4CAF50)
        hpRatio > 0.3f -> Color(0xFFFFA726)
        else -> Color(0xFFE53935)
    }
    val mpColor = Color(0xFF2196F3)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.gaotong.idledungeon.ui.component.HeroAvatar(hero = hero, size = 24.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            "${hero.name.take(8)} ${hero.heroClass.name.take(3)}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(96.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            // HP 条
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { hpRatio },
                    color = hpColor,
                    trackColor = hpColor.copy(alpha = 0.2f),
                    modifier = Modifier.weight(1f).height(8.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("${hero.currentHp}/${hero.maxHp}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(70.dp))
            }
            // MP 条 (没有 MP 上限的英雄不画)
            if (hero.maxMp > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { mpRatio },
                        color = mpColor,
                        trackColor = mpColor.copy(alpha = 0.2f),
                        modifier = Modifier.weight(1f).height(6.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${hero.currentMp}/${hero.maxMp}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(70.dp))
                }
            }
        }
    }
}

// 日志解析 & 事件卡片

/** 一个事件卡片的数据 */
private data class EventCardData(
    val index: Int,
    val header: String,        // 标题 (房间名 + 事件类型)
    val lines: List<String>,   // 内容行
    val cardType: CardType
)

private enum class CardType {
    FLOOR_TITLE, COMBAT, GUARDED, LOOT, PUZZLE, TRAP, STAIR, ROOM, SYSTEM
}

/** 将平面日志按空行分隔解析为卡片列表 */
private fun parseLogToCards(log: List<String>): List<EventCardData> {
    if (log.isEmpty()) return emptyList()
    val cards = mutableListOf<EventCardData>()
    var current = mutableListOf<String>()

    for (line in log) {
        if (line.isEmpty()) {
            if (current.isNotEmpty()) {
                cards.add(buildCard(cards.size, current))
                current = mutableListOf()
            }
        } else {
            current.add(line)
        }
    }
    if (current.isNotEmpty()) {
        cards.add(buildCard(cards.size, current))
    }
    return cards
}

private fun buildCard(index: Int, lines: List<String>): EventCardData {
    val firstLine = lines.first()
    val type = when {
        firstLine.startsWith("==") -> CardType.FLOOR_TITLE
        firstLine.contains("GUARDED") -> CardType.GUARDED
        firstLine.contains("PUZZLE") -> CardType.PUZZLE
        firstLine.contains("BOSS") || firstLine.contains("ENCOUNTER") -> CardType.COMBAT
        firstLine.contains("LOOT") -> CardType.LOOT
        firstLine.contains("TRAP") -> CardType.TRAP
        firstLine.contains(">>") || firstLine.contains("STAIRS") -> CardType.STAIR
        firstLine.startsWith("[") -> CardType.ROOM
        else -> CardType.SYSTEM
    }
    return EventCardData(
        index = index,
        header = firstLine,
        lines = lines.drop(1),  // 全量明细 (不再过滤), header 单独显示
        cardType = type
    )
}

@Composable
private fun EventCard(card: EventCardData, onClick: () -> Unit) {
    val containerColor = cardContainerColor(card.cardType)
    val headerColor = cardHeaderColor(card.cardType)
    val hasDetail = card.lines.isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth()
            .then(if (hasDetail) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                card.header,
                style = MaterialTheme.typography.labelLarge,
                color = headerColor,
                modifier = Modifier.weight(1f)
            )
            if (hasDetail) {
                Text("›", style = MaterialTheme.typography.titleMedium, color = headerColor)
            }
        }
    }
}

@Composable
private fun cardContainerColor(t: CardType): Color = when (t) {
    CardType.FLOOR_TITLE -> MaterialTheme.colorScheme.primaryContainer
    CardType.COMBAT      -> Color(0xFFFFCDD2)  // 浅红 — 普通战斗
    CardType.GUARDED     -> Color(0xFFFFE0B2)  // 暖橙 — 宝物战斗 (战利品+战斗)
    CardType.LOOT        -> MaterialTheme.colorScheme.tertiaryContainer  // 普通宝箱
    CardType.PUZZLE      -> Color(0xFFB3E5FC)  // 浅蓝 — 解谜 (智力)
    CardType.TRAP        -> Color(0xFFD7CCC8)  // 浅棕 — 陷阱 (不再半透明)
    CardType.STAIR       -> MaterialTheme.colorScheme.secondaryContainer
    CardType.ROOM        -> MaterialTheme.colorScheme.surfaceVariant
    CardType.SYSTEM      -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun cardHeaderColor(t: CardType): Color = when (t) {
    CardType.FLOOR_TITLE -> MaterialTheme.colorScheme.onPrimaryContainer
    CardType.COMBAT      -> Color(0xFFB71C1C)  // 深红
    CardType.GUARDED     -> Color(0xFFE65100)  // 深橙
    CardType.LOOT        -> MaterialTheme.colorScheme.onTertiaryContainer
    CardType.PUZZLE      -> Color(0xFF01579B)  // 深蓝
    CardType.TRAP        -> Color(0xFF4E342E)  // 深棕
    CardType.STAIR       -> MaterialTheme.colorScheme.onSecondaryContainer
    else                 -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * 事件详情临时页 - 点击日志条目后展开, 显示该事件的全部内容
 * (战斗的逐回合伤害 / 陷阱解除判定全过程 / 解谜判定 / 全部 buff/debuff 触发)
 */
@Composable
private fun EventDetailDialog(card: EventCardData, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.large,
            color = cardContainerColor(card.cardType)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶栏
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        card.header,
                        style = MaterialTheme.typography.titleMedium,
                        color = cardHeaderColor(card.cardType),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider()
                // 全量明细
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(card.lines) { line ->
                        val baseColor = when {
                            line.contains("Victory") -> MaterialTheme.colorScheme.primary
                            line.contains("Defeat") || line.contains("Injured") || line.contains("wiped") -> MaterialTheme.colorScheme.error
                            line.contains("Loot") || line.contains("Obtained") -> MaterialTheme.colorScheme.tertiary
                            line.contains("casts") -> MaterialTheme.colorScheme.primary
                            line.contains("used") || line.contains("uses") -> MaterialTheme.colorScheme.secondary
                            line.contains("Round") -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = colorizedLogLine(line.trimStart()),
                            style = MaterialTheme.typography.bodySmall,
                            color = baseColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * 把日志行里的 `HP:n/m` / `MP:n/m` / `CP:n` 串单独染色, 其它内容保持基础色.
 */
private fun colorizedLogLine(line: String): AnnotatedString {
    val hpColor = Color(0xFFE53935)   // 红
    val mpColor = Color(0xFF1E88E5)   // 蓝
    val cpColor = Color(0xFF6A1B9A)   // 紫
    val pattern = Regex("""(HP|MP|CP):\d+(?:/\d+)?""")
    val matches = pattern.findAll(line).toList()
    if (matches.isEmpty()) return AnnotatedString(line)
    return buildAnnotatedString {
        var lastEnd = 0
        for (m in matches) {
            if (m.range.first > lastEnd) append(line.substring(lastEnd, m.range.first))
            val tone = when (m.value.take(2)) {
                "HP" -> hpColor
                "MP" -> mpColor
                else -> cpColor
            }
            withStyle(SpanStyle(color = tone, fontWeight = FontWeight.SemiBold)) {
                append(m.value)
            }
            lastEnd = m.range.last + 1
        }
        if (lastEnd < line.length) append(line.substring(lastEnd))
    }
}

// 地图 Canvas

@Composable
private fun DungeonMapCanvas(floor: DungeonFloor, expedition: ExpeditionSession?) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.8f, 5f)
                    offset += pan
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        val cellSize = minOf(size.width / floor.width, size.height / floor.height)
        val gap = (cellSize * 0.08f).coerceAtMost(2f)

        for (y in floor.grid.indices) {
            for (x in floor.grid[y].indices) {
                val node = floor.grid[y][x]
                val isRoomCell = node.roomNumber != null

                val color = if (!node.explored) {
                    Color(0xFF555565)
                } else when (node.type) {
                    NodeType.WALL -> Color(0xFF1A1A2E)
                    NodeType.ROOM,
                    NodeType.CORRIDOR -> when {
                        isRoomCell && node.visited -> Color(0xFFDD9933)
                        isRoomCell -> Color(0xFF776644)
                        else -> Color(0xFFB0B0B8)
                    }
                    NodeType.BOSS_ROOM ->
                        if (node.visited) Color(0xFFCC3333) else Color(0xFF882222)
                    NodeType.STAIRS -> when (node.event) {
                        NodeEvent.STAIRS_UP -> Color(0xFF33AACC)
                        NodeEvent.STAIRS_DOWN -> Color(0xFF3366CC)
                        else -> Color.Cyan
                    }
                }

                drawRect(
                    color = color,
                    topLeft = Offset(x * cellSize, y * cellSize),
                    size = Size(cellSize - gap, cellSize - gap)
                )

                if (expedition != null && expedition.posX == x && expedition.posY == y) {
                    drawCircle(
                        color = Color.Green,
                        radius = cellSize / 3,
                        center = Offset(x * cellSize + cellSize / 2, y * cellSize + cellSize / 2)
                    )
                }
            }
        }
    }
}
