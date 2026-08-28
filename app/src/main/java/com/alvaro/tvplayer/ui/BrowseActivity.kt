@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.alvaro.tvplayer.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.alvaro.tvplayer.data.Channel
import com.alvaro.tvplayer.data.PlaylistHolder
import com.alvaro.tvplayer.data.Prefs

private const val FAVORITES = "★ Favoritos"

class BrowseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PlaylistHolder.current == null) {
            finish()
            return
        }
        setContent { TvTheme { BrowseScreen() } }
    }

    @Composable
    private fun BrowseScreen() {
        val playlist = PlaylistHolder.current ?: return
        val prefs = remember { Prefs(this) }

        var favorites by remember { mutableStateOf(prefs.favorites()) }
        var query by remember { mutableStateOf("") }

        val groups = remember(playlist) {
            buildList {
                add(FAVORITES)
                addAll(playlist.groups)
            }
        }
        var selectedGroup by remember { mutableStateOf(playlist.groups.firstOrNull() ?: FAVORITES) }

        val visible = remember(selectedGroup, query, favorites, playlist) {
            val base = when (selectedGroup) {
                FAVORITES -> playlist.channels.filter { it.id in favorites }
                else -> playlist.channels.filter { it.group == selectedGroup }
            }
            if (query.isBlank()) base
            else base.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }

        // Foco inicial para el control remoto
        val firstGroupFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { firstGroupFocus.requestFocus() } }

        Row(Modifier.fillMaxSize().background(Bg)) {

            // ---- Panel lateral de categorias ----
            Column(
                Modifier
                    .width(250.dp)
                    .fillMaxHeight()
                    .background(Surface1)
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    "Categorias",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 18.dp, bottom = 10.dp)
                )
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(groups) { index, group ->
                        val count = if (group == FAVORITES) {
                            playlist.channels.count { it.id in favorites }
                        } else {
                            playlist.channels.count { it.group == group }
                        }
                        GroupItem(
                            label = group,
                            count = count,
                            selected = group == selectedGroup,
                            onClick = { selectedGroup = group },
                            modifier = if (index == 0) Modifier.focusRequester(firstGroupFocus)
                                       else Modifier
                        )
                    }
                }
            }

            // ---- Contenido ----
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 22.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            selectedGroup,
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${visible.size} canales  ·  ${playlist.channels.size} en total",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    SearchField(query) { query = it }
                }

                Spacer(Modifier.height(14.dp))

                if (visible.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (selectedGroup == FAVORITES)
                                "Aun no tienes favoritos.\nManten pulsado un canal para marcarlo."
                            else "Sin resultados.",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 165.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(visible, key = { it.id }) { channel ->
                            ChannelCard(
                                channel = channel,
                                isFavorite = channel.id in favorites,
                                onClick = { play(channel, visible) },
                                onLongClick = {
                                    prefs.toggleFavorite(channel)
                                    favorites = prefs.favorites()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun play(channel: Channel, context: List<Channel>) {
        PlayerActivity.queue = context
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_INDEX, context.indexOf(channel).coerceAtLeast(0))
        )
    }

    @Composable
    private fun GroupItem(
        label: String,
        count: Int,
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        FocusableCard(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            containerColor = if (selected) Surface2 else Color.Transparent,
            focusedContainerColor = Accent
        ) { active ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    color = when {
                        active -> Color.White
                        selected -> Accent
                        else -> TextMuted
                    },
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$count",
                    color = if (active) Color.White else TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }

    @Composable
    private fun ChannelCard(
        channel: Channel,
        isFavorite: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit
    ) {
        FocusableCard(
            onClick = onClick,
            onLongClick = onLongClick,
            focusedScale = 1.06f
        ) { active ->
            Column(Modifier.padding(9.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(86.dp)
                        .background(Color(0xFF0B0E13), RoundedCorner12),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel.logo.isNullOrBlank()) {
                        AsyncImage(
                            model = channel.logo,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(9.dp)
                        )
                    } else {
                        Text(
                            channel.name.take(2).uppercase(),
                            color = Accent,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isFavorite) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Favorito",
                            tint = Color(0xFFFFC93C),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(5.dp)
                                .size(15.dp)
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    channel.name,
                    color = if (active) Color.White else Color(0xFFD4D9E0),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(32.dp)
                )
            }
        }
    }

    @Composable
    private fun SearchField(value: String, onValueChange: (String) -> Unit) {
        var focused by remember { mutableStateOf(false) }
        val fieldFocus = remember { FocusRequester() }
        Box(
            Modifier
                .width(220.dp)
                .background(if (focused) Surface2 else Surface1, RoundedCorner12)
                .clickable { runCatching { fieldFocus.requestFocus() } }
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            if (value.isEmpty()) {
                Text("Buscar canal...", color = TextMuted, fontSize = 13.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .onFocusChanged { focused = it.isFocused }
            )
        }
    }
}
