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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.alvaro.tvplayer.data.Channel
import com.alvaro.tvplayer.data.ChannelChecker
import com.alvaro.tvplayer.data.ChannelStatus
import com.alvaro.tvplayer.data.PlaylistHolder
import com.alvaro.tvplayer.data.Prefs
import com.alvaro.tvplayer.data.Search
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val FAVORITES = "★ Favoritos"

class BrowseActivity : ComponentActivity() {

    private var verifyJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PlaylistHolder.current == null) {
            finish()
            return
        }
        setContent { TvTheme { BrowseScreen() } }
    }

    override fun onDestroy() {
        super.onDestroy()
        verifyJob?.cancel()
    }

    @Composable
    private fun BrowseScreen() {
        val playlist = PlaylistHolder.current ?: return
        val prefs = remember { Prefs(this) }

        var favorites by remember { mutableStateOf(prefs.favorites()) }
        var query by remember { mutableStateOf("") }
        var ocultarCaidos by remember { mutableStateOf(false) }

        val groups = remember(playlist) {
            buildList {
                add(FAVORITES)
                addAll(playlist.groups)
            }
        }
        var selectedGroup by remember { mutableStateOf(playlist.groups.firstOrNull() ?: FAVORITES) }

        val buscando = query.isNotBlank()
        val consulta = remember(query) { Search.normalizar(query) }

        // ---- Categorias cuyo nombre coincide con lo buscado ----
        val categoriasCoincidentes = remember(consulta, playlist) {
            if (consulta.isBlank()) emptyList()
            else Search.ordenarPorRelevancia(
                playlist.groups.filter { Search.coincide(it, consulta) },
                consulta
            ) { it }
        }

        // ---- Canales que se muestran ----
        // derivedStateOf porque lee el mapa del verificador, que cambia en vivo.
        val mostrados by remember(selectedGroup, consulta, favorites, playlist, ocultarCaidos) {
            derivedStateOf {
                val base = if (consulta.isNotBlank()) {
                    // BUSQUEDA GLOBAL: toda la lista, no solo la categoria abierta.
                    Search.ordenarPorRelevancia(
                        playlist.channels.filter { Search.coincide(it.name, consulta) },
                        consulta
                    ) { it.name }
                } else {
                    when (selectedGroup) {
                        FAVORITES -> playlist.channels.filter { it.id in favorites }
                        else -> playlist.channels.filter { it.group == selectedGroup }
                    }
                }

                if (ocultarCaidos) {
                    base.filter { ChannelChecker.statusOf(it) != ChannelStatus.CAIDO }
                } else base
            }
        }

        val firstGroupFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { firstGroupFocus.requestFocus() } }

        Row(Modifier.fillMaxSize().background(Bg)) {

            // ---- Panel lateral de categorias ----
            Column(
                Modifier
                    .width(240.dp)
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
                            selected = !buscando && group == selectedGroup,
                            resaltado = buscando && Search.coincide(group, consulta),
                            onClick = {
                                selectedGroup = group
                                query = ""
                            },
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
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (buscando) "Resultados de \"$query\"" else selectedGroup,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            when {
                                ChannelChecker.verificando ->
                                    "Verificando ${ChannelChecker.progreso} de ${ChannelChecker.total}..."
                                buscando ->
                                    "${mostrados.size} canales en toda la lista" +
                                    if (categoriasCoincidentes.isNotEmpty())
                                        "  ·  ${categoriasCoincidentes.size} categorias" else ""
                                else ->
                                    "${mostrados.size} canales  ·  ${playlist.channels.size} en total"
                            },
                            color = if (ChannelChecker.verificando) Accent else TextMuted,
                            fontSize = 12.sp
                        )
                    }

                    SmallButton(
                        label = if (ChannelChecker.verificando) "Detener" else "Verificar",
                        active = ChannelChecker.verificando
                    ) {
                        if (ChannelChecker.verificando) {
                            verifyJob?.cancel()
                        } else {
                            val objetivo = mostrados.toList()
                            verifyJob = lifecycleScope.launch {
                                ChannelChecker.verificar(objetivo)
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    SmallButton(
                        label = if (ocultarCaidos) "Mostrar todos" else "Ocultar caidos",
                        active = ocultarCaidos
                    ) { ocultarCaidos = !ocultarCaidos }
                }

                Spacer(Modifier.height(10.dp))

                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    onClear = { query = "" }
                )

                // ---- Categorias encontradas: atajo para saltar a una ----
                if (buscando && categoriasCoincidentes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Categorias que coinciden",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categoriasCoincidentes) { grupo ->
                            val n = playlist.channels.count { it.group == grupo }
                            FocusableCard(
                                onClick = {
                                    selectedGroup = grupo
                                    query = ""
                                },
                                containerColor = Surface2,
                                focusedContainerColor = Accent
                            ) {
                                Text(
                                    "$grupo  ($n)",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (mostrados.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                buscando && categoriasCoincidentes.isNotEmpty() ->
                                    "Ningun canal se llama asi, pero hay categorias que coinciden.\n" +
                                    "Elige una de las de arriba."
                                buscando ->
                                    "No hay ningun canal con ese nombre en toda la lista.\n" +
                                    "Prueba con menos letras."
                                selectedGroup == FAVORITES ->
                                    "Aun no tienes favoritos.\nManten pulsado un canal para marcarlo."
                                ocultarCaidos ->
                                    "Todos los canales de esta categoria fallaron la verificacion."
                                else -> "Sin resultados."
                            },
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
                        items(mostrados, key = { it.id }) { channel ->
                            ChannelCard(
                                channel = channel,
                                isFavorite = channel.id in favorites,
                                estado = ChannelChecker.statusOf(channel),
                                // En busqueda se muestra a que categoria pertenece,
                                // que es justo lo que no se sabia de antemano.
                                categoria = if (buscando) channel.group else null,
                                onClick = { play(channel, mostrados) },
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
    private fun SmallButton(label: String, active: Boolean, onClick: () -> Unit) {
        FocusableCard(
            onClick = onClick,
            containerColor = if (active) Accent else Surface1,
            focusedContainerColor = Accent
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = 12.5.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
    }

    @Composable
    private fun GroupItem(
        label: String,
        count: Int,
        selected: Boolean,
        resaltado: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        FocusableCard(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            containerColor = when {
                selected -> Surface2
                resaltado -> Color(0xFF1B2E4D)   // coincide con la busqueda
                else -> Color.Transparent
            },
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
                        selected || resaltado -> Accent
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
        estado: ChannelStatus,
        categoria: String?,
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

                    val colorEstado = when (estado) {
                        ChannelStatus.OK -> Color(0xFF4CD07E)
                        ChannelStatus.CAIDO -> Color(0xFFFF6B5E)
                        ChannelStatus.PROBANDO -> Color(0xFFFFC93C)
                        ChannelStatus.DESCONOCIDO -> null
                    }
                    if (colorEstado != null) {
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(5.dp)
                                .size(9.dp)
                                .background(colorEstado, CircleShape)
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
                    color = when {
                        estado == ChannelStatus.CAIDO -> Color(0xFF7C848F)
                        active -> Color.White
                        else -> Color(0xFFD4D9E0)
                    },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (categoria == null) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(if (categoria == null) 32.dp else 17.dp)
                )

                // En modo busqueda: a que categoria pertenece este canal
                if (categoria != null) {
                    Text(
                        categoria,
                        color = if (active) Accent else TextMuted,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.height(15.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun SearchField(
        value: String,
        onValueChange: (String) -> Unit,
        onClear: () -> Unit
    ) {
        var focused by remember { mutableStateOf(false) }
        val fieldFocus = remember { FocusRequester() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .background(if (focused) Surface2 else Surface1, RoundedCorner12)
                    .clickable { runCatching { fieldFocus.requestFocus() } }
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        "Buscar en TODA la lista: canal o categoria...",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
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
            if (value.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                FocusableCard(
                    onClick = onClear,
                    containerColor = Surface1,
                    focusedContainerColor = Accent
                ) {
                    Text(
                        "Limpiar",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                    )
                }
            }
        }
    }
}
