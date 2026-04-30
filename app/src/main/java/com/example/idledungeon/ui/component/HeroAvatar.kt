package com.example.idledungeon.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.idledungeon.model.Hero

/**
 * 通用英雄头像组件: 有 portraitUri 就用 Coil 加载, 否则显示带圆形底色的人形图标.
 * 自动裁剪成圆形, 中心裁剪.
 */
@Composable
fun HeroAvatar(
    hero: Hero,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val portraitUri = hero.portraitUri
    // URI 加载失败 (图被删 / 权限丢 / 网络错) 退回默认图标. key = portraitUri 让换头像后重置
    var loadFailed by remember(portraitUri) { mutableStateOf(false) }
    if (!portraitUri.isNullOrBlank() && !loadFailed) {
        AsyncImage(
            model = portraitUri,
            contentDescription = hero.name,
            modifier = modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
            onError = { loadFailed = true }
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = hero.name,
                modifier = Modifier.size(size * 0.6f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
