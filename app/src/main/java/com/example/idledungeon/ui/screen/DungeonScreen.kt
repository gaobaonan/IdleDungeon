package com.example.idledungeon.ui.screen

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
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dungeon / 地下城  Floor $deepestFloor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    scale = (scale * zoom).coerceIn(0.5f, 3f)
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
        val cellSize = 24.dp.toPx()
        for (y in floor.grid.indices) {
            for (x in floor.grid[y].indices) {
                val node = floor.grid[y][x]
                val color = when {
                    !node.explored -> Color.DarkGray          // 迷雾
                    node.type == NodeType.WALL -> Color.Black
                    node.type == NodeType.CORRIDOR -> Color.LightGray
                    node.type == NodeType.ROOM -> Color(0xFFBB9955)
                    node.type == NodeType.BOSS_ROOM -> Color.Red.copy(alpha = 0.6f)
                    node.type == NodeType.STAIRS -> Color.Cyan
                    else -> Color.Gray
                }
                drawRect(
                    color = color,
                    topLeft = Offset(x * cellSize, y * cellSize),
                    size = Size(cellSize - 2.dp.toPx(), cellSize - 2.dp.toPx())
                )
                // 绘制队伍位置
                if (expedition != null && expedition.posX == x && expedition.posY == y) {
                    drawCircle(
                        color = Color.Green,
                        radius = cellSize / 4,
                        center = Offset(x * cellSize + cellSize / 2, y * cellSize + cellSize / 2)
                    )
                }
            }
        }
    }
}