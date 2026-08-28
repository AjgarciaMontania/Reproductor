package com.alvaro.tvplayer.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.alvaro.tvplayer.data.Channel
import com.alvaro.tvplayer.data.PlaylistHolder
import com.alvaro.tvplayer.data.Prefs

private const val FAVORITES = "★ Favoritos"

class BrowseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val playlist = PlaylistHolder.current
        if (playlist == null) {
            finish()
            return
        }
        setContent { TvTheme { BrowseScreen() } }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    private fun BrowseScreen() {
        val playlist = PlaylistHolder.current ?: return
        val prefs = remember { Prefs(this) }

        var favorites by remember { mutableStateOf(prefs.favorites()) }
        var query by remember { mutableStateOf("") }

        val groups = remember(playlist, favorites) {
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

        Row(
            Modifier
                .fillMaxSize()
                .background(Bg)
        ) {
            // ---- Panel lateral de categorias ----
            Column(
                Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .background(Surface1)
                    .padding(vertical = 20.dp)
            ) {
                Text(
                    "Categorias",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
                )
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(groups) { group ->
                        val count = if (group == FAVORITES) {
                            playlist.channels.count { it.id in favorites }
                        } else {
                            playlist.channels.count { it.group == group }
                        }
                        GroupItem(
                            label = group,
                            count = count,
                            selected = group == selectedGroup,
                            onClick = { selectedGroup = group }
                        )
                    }
                }
            }

            // ---- Contenido ----
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 32.dp, vertical = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            selectedGroup,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${visible.size} canales  ·  ${playlist.channels.size} en total",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                    SearchField(query) { query = it }
                }

                Spacer(Modifier.height(18.dp))

                if (visible.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (selectedGroup == FAVORITES)
                                "Aun no tienes favoritos.\nMarca uno manteniendo OK sobre un canal."
                            else "Sin resultados.",
                            color = TextMuted,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 190.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
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

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    private fun GroupItem(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            colors = CardDefaults.colors(
                containerColor = if (selected) Surface2 else Color.Transparent,
                focusedContainerColor = Accent
            )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    color = if (focused) Color.White else if (selected) Accent else TextMuted,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$count",
                    color = if (focused) Color.White else TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    private fun ChannelCard(
        channel: Channel,
        isFavorite: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit
    ) {
        var focused by remember { mutableStateOf(false) }
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.onFocusChanged { focused = it.isFocused },
            colors = CardDefaults.colors(
                containerColor = Surface1,
                focusedContainerColor = Surface2
            ),
            scale = CardDefaults.scale(focusedScale = 1.06f)
        ) {
            Column(Modifier.padding(10.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .background(Color(0xFF0B0E13), RoundedCorner12),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel.logo.isNullOrBlank()) {
                        AsyncImage(
                            model = channel.logo,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        )
                    } else {
                        Text(
                            channel.name.take(2).uppercase(),
                            color = Accent,
                            fontSize = 26.sp,
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
                                .padding(6.dp)
                                .size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    channel.name,
                    color = if (focused) Color.White else Color(0xFFD4D9E0),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(34.dp)
                )
            }
        }
    }

    @Composable
    private fun SearchField(value: String, onValueChange: (String) -> Unit) {
        var focused by remember { mutableStateOf(false) }
        Box(
            Modifier
                .width(260.dp)
                .background(if (focused) Surface2 else Surface1, RoundedCorner12)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (value.isEmpty()) {
                Text("Buscar canal...", color = TextMuted, fontSize = 13.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
            )
        }
    }
}
