package com.example.idledungeon.ui.screen

import android.graphics.Paint as NativePaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
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

/**
 * 地下城界面
 * - 主内容: Canvas 绘制的网格迷雾地图 (可缩放/拖动)
 * - 左上角: 当前冒险状态 (时间/补给)
 * - 底部: 出发按钮 → PartySetupScreen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DungeonScreen(
    floor: DungeonFloor?,
    expedition: ExpeditionSession?,
    deepestFloor: Int,
    onDepart: () -> Unit,
    onBack: () -> Unit,
    onDebugRefresh: (() -> Unit)? = null  // [DEBUG] 发布前删除
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dungeon / 地下城  Floor $deepestFloor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // [DEBUG] 刷新地图按钮 — 发布前删除
                    if (onDebugRefresh != null) {
                        IconButton(onClick = onDebugRefresh) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Refresh")
                            Text("Re", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            // ── 地图区域 ──
            if (floor != null) {
                DungeonMapCanvas(floor, expedition)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No map data / 暂无地图数据")
                }
            }

            // ── 冒险状态浮窗 ──
            if (expedition?.isExploring == true) {
                Card(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Exploring... / 探索中...", style = MaterialTheme.typography.labelMedium)
                        Text("Supply / 补给: ${expedition.currentSupply}")
                        Text("Floor / 层: ${expedition.currentLayer}")
                    }
                }
            }

            // ── 出发按钮 ──
            if (expedition == null || !expedition.isExploring) {
                Button(
                    onClick = onDepart,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
                ) {
                    Text("Depart / 出发")
                }
            }
        }
    }
}

/**
 * Canvas 绘制地牢网格地图
 * 支持双指缩放和拖动
 */
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
        // 自动缩放: 让整张地图刚好填满屏幕
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
                    Color(0xFF555565)                                     // 迷雾: 中灰
                } else when (node.type) {
                    NodeType.WALL -> Color(0xFF1A1A2E)                   // 墙壁: 深蓝黑
                    NodeType.ROOM,
                    NodeType.CORRIDOR -> when {
                        hasLabel && isVisited -> Color(0xFFDD9933)        // 已探索房间: 亮橙
                        hasLabel -> Color(0xFF776644)                     // 未探索房间: 暗橙
                        else -> Color(0xFFB0B0B8)                        // 走廊: 灰白
                    }
                    NodeType.BOSS_ROOM ->
                        if (isVisited) Color(0xFFCC3333) else Color(0xFF882222) // Boss
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

                // 绿点: 队伍位置
                if (expedition != null && expedition.posX == x && expedition.posY == y) {
                    drawCircle(
                        color = Color.Green,
                        radius = cellSize / 3,
                        center = Offset(x * cellSize + cellSize / 2, y * cellSize + cellSize / 2)
                    )
                }

                // [DEBUG] 房间编号 (已揭示时显示)
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