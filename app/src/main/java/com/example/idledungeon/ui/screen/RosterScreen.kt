package com.example.idledungeon.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.idledungeon.model.Hero
import com.example.idledungeon.model.HeroStatus
import com.example.idledungeon.ui.component.HeroAvatar

/**
 * 冒险者名录
 * - 所有已招募英雄列表
 * - 显示: 头像 / 名字 / 等级 / 职业 / 状态
 * - 筛选 & 排序
 * - 点击 → HeroDetailScreen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RosterScreen(
    heroes: List<Hero>,
    onHeroClick: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adventurer Roster") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                // TODO: 添加筛选/排序按钮
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(heroes) { hero ->
                HeroListCard(hero) { onHeroClick(hero.id) }
            }
        }
    }
}

@Composable
private fun HeroListCard(hero: Hero, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroAvatar(hero = hero, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(hero.name, style = MaterialTheme.typography.titleSmall)
                Text("Lv.${hero.level}  ${hero.heroClass.name}", style = MaterialTheme.typography.bodySmall)
            }
            StatusChip(hero.status)
        }
    }
}

@Composable
private fun StatusChip(status: HeroStatus) {
    val (text, color) = when (status) {
        HeroStatus.IDLE -> "Idle" to MaterialTheme.colorScheme.primary
        HeroStatus.DEPLOYED -> "Deployed" to MaterialTheme.colorScheme.tertiary
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color, style = MaterialTheme.typography.labelSmall)
    }
}