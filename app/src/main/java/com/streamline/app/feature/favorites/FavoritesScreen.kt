package com.streamline.app.feature.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streamline.app.core.designsystem.Artwork
import com.streamline.app.core.designsystem.CinematicBackground
import com.streamline.app.core.designsystem.GlassIconButton
import com.streamline.app.core.designsystem.StreamlineColors
import com.streamline.app.core.designsystem.isTelevision
import com.streamline.app.core.di.AppContainer
import com.streamline.app.core.logging.AppLog
import com.streamline.app.core.playback.FavoriteEntry

@Composable
fun FavoritesRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenPlayer: (com.streamline.app.domain.model.PlaybackRequest) -> Unit,
) {
    val items by container.favoriteStore.items.collectAsStateWithLifecycle()
    FavoritesScreen(
        items = items,
        onBack = {
            AppLog.event("UI", "favorites_back")
            onBack()
        },
        onPlay = { entry ->
            AppLog.event("FAVORITES", "open", fields = mapOf("id" to entry.item.id))
            onOpenPlayer(entry.request())
        },
        onRemove = { entry ->
            container.favoriteStore.remove(entry.item.id)
            AppLog.event("FAVORITES", "remove", fields = mapOf("id" to entry.item.id))
        },
    )
}

@Composable
fun FavoritesScreen(
    items: List<FavoriteEntry>,
    onBack: () -> Unit,
    onPlay: (FavoriteEntry) -> Unit,
    onRemove: (FavoriteEntry) -> Unit,
) {
    val isTv = isTelevision()
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(isTv) {
        if (isTv) runCatching { backFocus.requestFocus() }
    }
    CinematicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    modifier = Modifier.focusRequester(backFocus),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "FAVORITES",
                        color = StreamlineColors.TextPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = items.size.toString() + " saved",
                        color = StreamlineColors.TextTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = StreamlineColors.Gold,
                            modifier = Modifier.size(42.dp),
                        )
                        Text(
                            text = "No favorites yet",
                            color = StreamlineColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            text = "Tap the star on any torrent card.",
                            color = StreamlineColors.TextTertiary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { it.item.id }) { entry ->
                        FavoriteCard(
                            entry = entry,
                            onPlay = { onPlay(entry) },
                            onRemove = { onRemove(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(
    entry: FavoriteEntry,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    val item = entry.item
    val shape = RoundedCornerShape(16.dp)
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(StreamlineColors.Surface.copy(alpha = 0.86f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) StreamlineColors.Gold else StreamlineColors.Hairline.copy(alpha = 0.7f),
                shape = shape,
            )
            .clickable(onClick = onPlay),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            Artwork(
                url = item.posterUrl,
                contentDescription = item.title,
                fallbackSeed = item.title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.6f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = "Remove favorite",
                    tint = StreamlineColors.Gold,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = StreamlineColors.Gold,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = item.quality ?: "Play",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = item.title,
                color = StreamlineColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(item.source.label, item.year?.toString()).joinToString(" · "),
                color = StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
