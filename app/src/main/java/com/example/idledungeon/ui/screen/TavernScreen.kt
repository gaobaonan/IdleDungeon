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

/**
 * 酒馆界面
 * - 可招募的冒险者列表 (每天刷新)
 * - 点击冒险者 → 查看详情 → 招募
 * - 投资升级按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TavernScreen(
    tavernLevel: Int,
    recruitableHeroes: List<Hero>,
    investCost: Int,
    onHeroClick: (String) -> Unit,
    onRecruit: (String) -> Unit,
    onInvest: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tavern / 酒馆  Lv.$tavernLevel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onInvest) { Text("Invest / 投资") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(recruitableHeroes) { hero ->
                HeroRecruitCard(hero, onHeroClick, onRecruit)
            }
        }
    }
}

@Composable
private fun HeroRecruitCard(
    hero: Hero,
    onHeroClick: (String) -> Unit,
    onRecruit: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onHeroClick(hero.id) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TODO: 头像 (预设头像系统)
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(hero.name, style = MaterialTheme.typography.titleSmall)
                Text("Lv.${hero.level} ${hero.heroClass.name}", style = MaterialTheme.typography.bodySmall)
                Text("HP: ${hero.maxHp}  ATK: ${hero.attack}  DEF: ${hero.defense}",
                    style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = { onRecruit(hero.id) }) {
                Text("Recruit\n招募")
            }
        }
    }
}