package com.example.idledungeon.ui.screen

import android.graphics.Paint as NativePaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.idledungeon.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DungeonScreen(
    floor: DungeonFloor?,
    expedition: ExpeditionSession?,
    heroes: List<Hero>,
    deepestFloor: Int,
    onDepart: () -> Unit,
    onBack: () -> Unit,
    onDebugRefresh: (() -> Unit)? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dungeon / 地下城") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (onDebugRefresh != null) {
                        TextButton(onClick = onDebugRefresh) { Text("[DEBUG]\nRefresh") }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // ── 上半: 地图 ──
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (floor != null) {
                    DungeonMapCanvas(floor, expedition)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No map data / 暂无地图数据",
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
                            Text("Supply:${expedition.currentSupply}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── 下半: 日志区 ──
            val logEntries = expedition?.combatLog ?: emptyList()
            val eventCards = remember(logEntries.size) { parseLogToCards(logEntries) }

            if (expedition != null && (expedition.party.isNotEmpty() || logEntries.isNotEmpty())) {
                val listState = rememberLazyListState()
                LaunchedEffect(eventCards.size) {
                    if (eventCards.isNotEmpty()) {
                        listState.animateScrollToItem(eventCards.size) // +1 for status header
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // ── 置顶: 队伍状态栏 ──
                    item(key = "party_status") {
                        PartyStatusBar(expedition, heroes)
                    }

                    // ── 事件卡片列表 ──
                    items(eventCards, key = { it.index }) { card ->
                        EventCard(card)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No expedition log / 暂无冒险记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline)
                }
            }

            // ── 出发按钮 ──
            if (expedition == null || !expedition.isExploring) {
                Button(
                    onClick = onDepart,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) { Text("Depart / 出发") }
            }
        }
    }
}

// ============================
// 队伍状态栏
// ============================

@Composable
private fun PartyStatusBar(expedition: ExpeditionSession, allHeroes: List<Hero>) {
    val partyHeroes = expedition.party.mapNotNull { slot ->
        allHeroes.find { it.id == slot.heroId }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(10.dp).fillMaxWidth()) {
            Text("Party Status / 队伍状态", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            if (partyHeroes.isEmpty()) {
                Text("No party / 无队伍", style = MaterialTheme.typography.bodySmall)
            } else {
                partyHeroes.forEach { hero ->
                    val hpRatio = if (hero.maxHp > 0) hero.currentHp.toFloat() / hero.maxHp else 0f
                    val hpColor = when {
                        hpRatio > 0.6f -> MaterialTheme.colorScheme.primary
                        hpRatio > 0.3f -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${hero.name} (${hero.heroClass.name})",
                            style = MaterialTheme.typography.bodySmall)
                        Text(
                            "HP:${hero.currentHp}/${hero.maxHp}  MP:${hero.currentMp}/${hero.maxMp}",
                            style = MaterialTheme.typography.bodySmall,
                            color = hpColor
                        )
                    }
                }
            }

            // 携带物品
            if (expedition.carriedItems.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                // 统计同名物品数量
                val itemCounts = expedition.carriedItems.groupingBy { it }.eachCount()
                val itemText = itemCounts.entries.joinToString("  ") { (id, count) ->
                    val name = try { com.example.idledungeon.data.ItemRegistry.get(id).name } catch (_: Exception) { id }
                    if (count > 1) "${name}x$count" else name
                }
                Text("Items / 携带: $itemText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 背包
            if (expedition.backpack.isNotEmpty()) {
                val lootCounts = expedition.backpack.groupingBy { it }.eachCount()
                val lootText = lootCounts.entries.joinToString("  ") { (id, count) ->
                    val name = try { com.example.idledungeon.data.ItemRegistry.get(id).name } catch (_: Exception) { id }
                    if (count > 1) "${name}x$count" else name
                }
                Text("Backpack / 背包: $lootText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

// ============================
// 日志解析 & 事件卡片
// ============================

/** 一个事件卡片的数据 */
private data class EventCardData(
    val index: Int,
    val header: String,        // 标题 (房间名 + 事件类型)
    val lines: List<String>,   // 内容行
    val cardType: CardType
)

private enum class CardType { FLOOR_TITLE, COMBAT, LOOT, TRAP, STAIR, ROOM, SYSTEM }

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
        firstLine.contains("BOSS") || firstLine.contains("ENCOUNTER") -> CardType.COMBAT
        firstLine.contains("LOOT") || firstLine.contains("宝箱") -> CardType.LOOT
        firstLine.contains("TRAP") || firstLine.contains("陷阱") -> CardType.TRAP
        firstLine.contains(">>") || firstLine.contains("STAIRS") -> CardType.STAIR
        firstLine.startsWith("[") -> CardType.ROOM
        else -> CardType.SYSTEM
    }

    // 对战斗卡片，提取摘要: 去掉逐回合明细，只保留开头/结果/掉落
    val displayLines = if (type == CardType.COMBAT) summarizeCombatLog(lines) else lines

    return EventCardData(
        index = index,
        header = firstLine,
        lines = displayLines.drop(1), // header 已经单独显示
        cardType = type
    )
}

/** 从战斗日志中提取摘要 (敌方列表 + 结果 + 掉落) */
private fun summarizeCombatLog(lines: List<String>): List<String> {
    val result = mutableListOf<String>()
    // 第一行 = 房间头
    result.add(lines.first())

    for (line in lines) {
        val trimmed = line.trimStart()
        when {
            // 保留: 敌方信息
            trimmed.startsWith("Enemies") || trimmed.startsWith("Party") -> result.add(line)
            // 保留: 药水使用
            trimmed.contains("uses") && (trimmed.contains("药水") || trimmed.contains("potion") || trimmed.contains("Potion")) -> result.add(line)
            // 保留: 法术施放
            trimmed.contains("casts [") -> result.add(line)
            // 保留: 结果行
            trimmed.startsWith("=== Victory") || trimmed.startsWith("=== Defeat") -> result.add(line)
            // 保留: 掉落
            trimmed.startsWith("Loot") || trimmed.startsWith("战利品") -> result.add(line)
            // 保留: Boss 击败
            trimmed.contains("Boss defeated") || trimmed.contains("Boss被击败") -> result.add(line)
            // 保留: 全灭
            trimmed.contains("wiped") || trimmed.contains("全灭") || trimmed.contains("Defeated by Boss") -> result.add(line)
            // 丢弃: 逐回合攻击明细 (--- Round, → 伤害行)
        }
    }
    return result
}

@Composable
private fun EventCard(card: EventCardData) {
    val containerColor = when (card.cardType) {
        CardType.FLOOR_TITLE -> MaterialTheme.colorScheme.primaryContainer
        CardType.COMBAT -> MaterialTheme.colorScheme.errorContainer
        CardType.LOOT -> MaterialTheme.colorScheme.tertiaryContainer
        CardType.TRAP -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        CardType.STAIR -> MaterialTheme.colorScheme.secondaryContainer
        CardType.ROOM -> MaterialTheme.colorScheme.surfaceVariant
        CardType.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant
    }
    val headerColor = when (card.cardType) {
        CardType.FLOOR_TITLE -> MaterialTheme.colorScheme.onPrimaryContainer
        CardType.COMBAT -> MaterialTheme.colorScheme.onErrorContainer
        CardType.LOOT -> MaterialTheme.colorScheme.onTertiaryContainer
        CardType.TRAP -> MaterialTheme.colorScheme.error
        CardType.STAIR -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(card.header, style = MaterialTheme.typography.labelLarge, color = headerColor)
            if (card.lines.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                card.lines.forEach { line ->
                    val lineColor = when {
                        line.contains("Victory") || line.contains("胜利") -> MaterialTheme.colorScheme.primary
                        line.contains("Defeat") || line.contains("败北") || line.contains("wiped") -> MaterialTheme.colorScheme.error
                        line.contains("Loot") || line.contains("Obtained") || line.contains("获得") -> MaterialTheme.colorScheme.tertiary
                        line.contains("casts") -> MaterialTheme.colorScheme.primary
                        line.contains("uses") -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(line.trimStart(), style = MaterialTheme.typography.bodySmall, color = lineColor)
                }
            }
        }
    }
}

// ============================
// 地图 Canvas
// ============================

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

        val textPaint = NativePaint().apply {
            color = android.graphics.Color.WHITE
            textSize = cellSize * 0.45f
            textAlign = NativePaint.Align.CENTER
            isFakeBoldText = true
        }

        for (y in floor.grid.indices) {
            for (x in floor.grid[y].indices) {
                val node = floor.grid[y][x]
                val rawLabel = node.label
                val isVisited = rawLabel.startsWith("v")
                val displayLabel = if (isVisited) rawLabel.removePrefix("v") else rawLabel
                val hasLabel = displayLabel.isNotEmpty()

                val color = if (!node.explored) {
                    Color(0xFF555565)
                } else when (node.type) {
                    NodeType.WALL -> Color(0xFF1A1A2E)
                    NodeType.ROOM,
                    NodeType.CORRIDOR -> when {
                        hasLabel && isVisited -> Color(0xFFDD9933)
                        hasLabel -> Color(0xFF776644)
                        else -> Color(0xFFB0B0B8)
                    }
                    NodeType.BOSS_ROOM ->
                        if (isVisited) Color(0xFFCC3333) else Color(0xFF882222)
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

                if (hasLabel && node.explored) {
                    drawContext.canvas.nativeCanvas.drawText(
                        displayLabel,
                        x * cellSize + cellSize / 2,
                        y * cellSize + cellSize / 2 + textPaint.textSize / 3,
                        textPaint
                    )
                }
            }
        }
    }
}
