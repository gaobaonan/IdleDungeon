package com.gaotong.idledungeon.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 开始菜单 - 游戏入口
 * 首次: [开始游戏] [退出]
 * 之后: [继续游戏] [重新开始] [退出]
 */
@Composable
fun StartScreen(
    hasExistingSave: Boolean,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Idle Dungeon",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "An Idle RPG Adventure",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(64.dp))

        if (hasExistingSave) {
            Button(
                onClick = onContinue,
                modifier = Modifier.width(200.dp)
            ) { Text("Continue") }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedButton(
            onClick = onNewGame,
            modifier = Modifier.width(200.dp)
        ) { Text(if (hasExistingSave) "New Game" else "Start") }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onExit) { Text("Exit") }
    }
}