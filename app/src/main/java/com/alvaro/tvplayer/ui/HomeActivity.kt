@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.alvaro.tvplayer.ui

import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.alvaro.tvplayer.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Seccion(val etiqueta: String, val icono: ImageVector) {
    TV("TV EN VIVO", Icons.Filled.LiveTv),
    PELICULAS("PELICULAS", Icons.Filled.Movie),
    FAVORITOS("FAVORITOS", Icons.Filled.Star),
    RECIENTE("RECIENTE", Icons.Filled.History),
    EXPLORAR("EXPLORAR", Icons.Filled.Search)
}

/**
 * Pantalla unica: el canal se reproduce a pantalla completa de fondo y el
 * menu flota encima, semitransparente. Elegir otro canal lo cambia detras
 * sin salir del menu. ATRAS oculta el menu; ATRAS de nuevo sale.
 */
class HomeActivity : ComponentActivity() {

    private var verifyJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (PlaylistHolder.current == null) { finish(); return }
        setContent { TvTheme { HomeScreen() } }
    }

    override fun onDestroy() {
        super.onDestroy()
        verifyJob?.cancel()
    }

    // ---------------------------------------------------------------- pantalla

    @Composable
    private fun HomeScreen() {
        val playlist = PlaylistHolder.current ?: return
        val prefs = remember { Prefs(this) }
        val actividad = this

        var seccion by remember { mutableStateOf(Seccion.TV) }
        var menuVisible by remember { mutableStateOf(true) }
        var favoritos by remember { mutableStateOf(prefs.favorites()) }
        var categoria by remember { mutableStateOf(playlist.groups.firstOrNull() ?: "") }
        var consulta by remember { mutableStateOf("") }

        var canalActual by remember { mutableStateOf<Channel?>(null) }
        var tituloActual by remember { mutableStateOf("") }
        var estado by remember { mutableStateOf<String?>(null) }
        var cargando by remember { mutableStateOf(false) }

        // Peliculas de dominio publico
        var coleccion by remember { mutableStateOf(ArchiveMovies.colecciones.first().id) }
        var peliculas by remember { mutableStateOf<List<Movie>>(emptyList()) }
        var paginaPelis by remember { mutableIntStateOf(1) }
        var cargandoPelis by remember { mutableStateOf(false) }

        // ---- reproductor de fondo ----
        val exo = remember {
            ExoPlayer.Builder(actividad).build().apply { playWhenReady = true }
        }

        DisposableEffect(Unit) {
            val listener = object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    cargando = false
                    estado = PlaybackErrors.describe(e)
                    canalActual?.let { ChannelChecker.marcarCaido(it) }
                }
                override fun onPlaybackStateChanged(s: Int) {
                    cargando = s == Player.STATE_BUFFERING
                    if (s == Player.STATE_READY) {
                        estado = null
                        canalActual?.let { ChannelChecker.marcarOk(it) }
                    }
                }
            }
            exo.addListener(listener)
            onDispose { exo.removeListener(listener); exo.release() }
        }

        fun reproducir(url: String, cabeceras: Map<String, String>) {
            estado = null
            cargando = true
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent(cabeceras["User-Agent"] ?: "MiReproductorTV/1.0")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20_000)
                .setReadTimeoutMs(20_000)
                .apply {
                    val extra = cabeceras.filterKeys { !it.equals("User-Agent", true) }
                    if (extra.isNotEmpty()) setDefaultRequestProperties(extra)
                }
            exo.setMediaSource(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(actividad, http))
                    .createMediaSource(MediaItem.fromUri(url))
            )
            exo.prepare()
        }

        fun verCanal(c: Channel) {
            canalActual = c
            tituloActual = c.name
            prefs.addRecentChannel(c)
            reproducir(c.url, c.headers)
        }

        fun verPelicula(p: Movie) {
            canalActual = null
            tituloActual = p.title
            estado = "Buscando el video de la pelicula..."
            cargando = true
            lifecycleScope.launch {
                val url = ArchiveMovies.urlDeVideo(p.identifier)
                if (url == null) {
                    cargando = false
                    estado = "Esta pelicula no tiene un video reproducible."
                } else reproducir(url, emptyMap())
            }
        }

        // ---- verificacion automatica al entrar, y reintento periodico ----
        LaunchedEffect(playlist) {
            ChannelChecker.iniciar(prefs, PlaylistHolder.sourceUrl ?: "lista", playlist.channels)
            verifyJob = lifecycleScope.launch {
                ChannelChecker.verificar(playlist.channels, soloDesconocidos = true)
            }
        }
        LaunchedEffect(playlist) {
            // Los caidos se reintentan cada pocos minutos: muchos vuelven solos.
            while (true) {
                delay(4 * 60 * 1000L)
                ChannelChecker.reintentarCaidos(playlist.channels)
            }
        }

        // Primer canal automatico, para que haya video de fondo desde el inicio
        LaunchedEffect(playlist) {
            if (canalActual == null) {
                playlist.channels.firstOrNull { !ChannelChecker.estaCaido(it) }
                    ?.let { verCanal(it) }
            }
        }

        LaunchedEffect(seccion, coleccion) {
            if (seccion == Seccion.PELICULAS && peliculas.isEmpty()) {
                cargandoPelis = true
                paginaPelis = 1
                peliculas = runCatching { ArchiveMovies.listar(coleccion, 1) }.getOrDefault(emptyList())
                cargandoPelis = false
            }
        }

        BackHandler(enabled = menuVisible) { menuVisible = false }

        // ---- canales visibles del panel derecho ----
        val canalesPanel: List<Channel> = when (seccion) {
            Seccion.TV -> playlist.channels.filter { it.group == categoria && !ChannelChecker.estaCaido(it) }
            Seccion.FAVORITOS -> playlist.channels.filter { it.id in favoritos }
            Seccion.RECIENTE -> {
                val orden = prefs.recentChannels()
                playlist.channels.filter { it.id in orden }.sortedBy { orden.indexOf(it.id) }
            }
            Seccion.EXPLORAR -> {
                val q = Search.normalizar(consulta)
                if (q.isBlank()) emptyList()
                else Search.ordenarPorRelevancia(
                    playlist.channels.filter { Search.coincide(it.name, q) }, q
                ) { it.name }
            }
            Seccion.PELICULAS -> emptyList()
        }

        // ================================ UI ================================
        Box(Modifier.fillMaxSize().background(Color.Black)) {

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        setPlayer(exo)
                    }
                },
                modifier = Modifier.fillMaxSize().clickable { menuVisible = !menuVisible }
            )

            // Oscurecido para que el menu se lea sobre cualquier imagen
            if (menuVisible) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0f to Color(0xE6000000),
                            0.45f to Color(0x66000000),
                            0.7f to Color(0x99000000),
                            1f to Color(0xE6000000)
                        )
                    )
                )
            }

            AnimatedVisibility(menuVisible, enter = fadeIn(), exit = fadeOut()) {
                Column(Modifier.fillMaxSize()) {

                    BarraSuperior()

                    Row(Modifier.weight(1f).fillMaxWidth()) {

                        // ---- menu lateral ----
                        AnimatedVisibility(
                            visible = true,
                            enter = slideInHorizontally(),
                            exit = slideOutHorizontally()
                        ) {
                            Column(
                                Modifier.width(230.dp).fillMaxHeight().padding(start = 22.dp, top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Seccion.entries.forEach { s ->
                                    ItemMenu(s, s == seccion) {
                                        seccion = s
                                        if (s == Seccion.TV && categoria.isBlank()) {
                                            categoria = playlist.groups.firstOrNull() ?: ""
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        // ---- panel derecho ----
                        Column(
                            Modifier.width(330.dp).fillMaxHeight().padding(end = 18.dp, top = 4.dp)
                        ) {
                            when (seccion) {
                                Seccion.PELICULAS -> PanelPeliculas(
                                    peliculas = peliculas,
                                    cargando = cargandoPelis,
                                    coleccionActual = coleccion,
                                    onColeccion = { coleccion = it; peliculas = emptyList() },
                                    onVer = { verPelicula(it); menuVisible = false },
                                    onMas = {
                                        if (!cargandoPelis) {
                                            cargandoPelis = true
                                            lifecycleScope.launch {
                                                val sig = paginaPelis + 1
                                                val mas = runCatching {
                                                    ArchiveMovies.listar(coleccion, sig)
                                                }.getOrDefault(emptyList())
                                                if (mas.isNotEmpty()) {
                                                    peliculas = peliculas + mas
                                                    paginaPelis = sig
                                                }
                                                cargandoPelis = false
                                            }
                                        }
                                    }
                                )

                                Seccion.EXPLORAR -> PanelExplorar(
                                    consulta = consulta,
                                    onConsulta = { consulta = it },
                                    resultados = canalesPanel,
                                    categorias = playlist.groups.filter {
                                        consulta.isNotBlank() && Search.coincide(it, Search.normalizar(consulta))
                                    },
                                    onCategoria = { categoria = it; seccion = Seccion.TV; consulta = "" },
                                    favoritos = favoritos,
                                    onCanal = { verCanal(it); menuVisible = false },
                                    onFavorito = { prefs.toggleFavorite(it); favoritos = prefs.favorites() }
                                )

                                else -> PanelCanales(
                                    titulo = when (seccion) {
                                        Seccion.TV -> categoria
                                        Seccion.FAVORITOS -> "Favoritos"
                                        else -> "Vistos recientemente"
                                    },
                                    categorias = if (seccion == Seccion.TV) playlist.groups else emptyList(),
                                    categoriaActual = categoria,
                                    onCategoria = { categoria = it },
                                    canales = canalesPanel,
                                    favoritos = favoritos,
                                    actual = canalActual,
                                    onCanal = { verCanal(it); menuVisible = false },
                                    onFavorito = { prefs.toggleFavorite(it); favoritos = prefs.favorites() }
                                )
                            }
                        }
                    }

                    BarraInferior(
                        titulo = tituloActual,
                        estado = estado,
                        cargando = cargando,
                        verificando = ChannelChecker.verificando,
                        progreso = ChannelChecker.progreso,
                        total = ChannelChecker.total,
                        onPantallaCompleta = { menuVisible = false }
                    )
                }
            }

            // Con el menu oculto, solo un rotulo discreto del canal
            if (!menuVisible) {
                Column(
                    Modifier.align(Alignment.BottomStart).padding(28.dp)
                ) {
                    estado?.let {
                        Text(it, color = Color(0xFFFF8A7E), fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                    if (cargando) {
                        Text("Cargando...", color = Accent, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        tituloActual,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Pulsa OK para abrir el menu", color = TextMuted, fontSize = 11.sp)
                }
            }
        }
    }

    // ------------------------------------------------------------- componentes

    @Composable
    private fun BarraSuperior() {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).background(Accent, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("M", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black) }
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("MI TV", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text("reproductor", color = TextMuted, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            listOf(Icons.Filled.Search, Icons.Filled.Star, Icons.Filled.History, Icons.Filled.Wifi)
                .forEach {
                    Icon(it, null, tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(start = 16.dp).size(19.dp))
                }
        }
    }

    @Composable
    private fun ItemMenu(seccion: Seccion, activa: Boolean, onClick: () -> Unit) {
        FocusableCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (activa) Accent.copy(alpha = 0.85f) else Color.Transparent,
            focusedContainerColor = Accent,
            shape = RoundedCornerShape(7.dp)
        ) { enfocado ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    seccion.icono, null,
                    tint = if (activa || enfocado) Color.White else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(11.dp))
                Text(
                    seccion.etiqueta,
                    color = if (activa || enfocado) Color.White else Color(0xFFC3CAD3),
                    fontSize = 14.sp,
                    fontWeight = if (activa) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }

    @Composable
    private fun PanelCanales(
        titulo: String,
        categorias: List<String>,
        categoriaActual: String,
        onCategoria: (String) -> Unit,
        canales: List<Channel>,
        favoritos: Set<String>,
        actual: Channel?,
        onCanal: (Channel) -> Unit,
        onFavorito: (Channel) -> Unit
    ) {
        Column {
            Text(
                titulo.ifBlank { "Canales" },
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (categorias.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categorias) { g ->
                        FocusableCard(
                            onClick = { onCategoria(g) },
                            containerColor = if (g == categoriaActual) Accent else Color(0x33FFFFFF),
                            focusedContainerColor = Accent,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                g, color = Color.White, fontSize = 11.sp, maxLines = 1,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
            }

            if (canales.isEmpty()) {
                Text("No hay canales aqui todavia.", color = TextMuted, fontSize = 12.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    itemsIndexed(canales, key = { _, c -> c.id }) { i, c ->
                        FilaCanal(
                            numero = i + 1,
                            canal = c,
                            esFavorito = c.id in favoritos,
                            enEmision = c.id == actual?.id,
                            onClick = { onCanal(c) },
                            onLongClick = { onFavorito(c) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun FilaCanal(
        numero: Int,
        canal: Channel,
        esFavorito: Boolean,
        enEmision: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit
    ) {
        FocusableCard(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (enEmision) Color(0x4D4C8DFF) else Color(0x26FFFFFF),
            focusedContainerColor = Accent,
            shape = RoundedCornerShape(8.dp)
        ) { enfocado ->
            Row(
                Modifier.fillMaxWidth().padding(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).background(Color(0xCC0B0E13), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!canal.logo.isNullOrBlank()) {
                        AsyncImage(
                            model = canal.logo, contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp)
                        )
                    } else {
                        Text(canal.name.take(2).uppercase(), color = Accent,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (enEmision) "▶  ${canal.name}" else "$numero  ${canal.name}",
                        color = if (enfocado || enEmision) Color.White else Color(0xFFDCE1E7),
                        fontSize = 12.5.sp,
                        fontWeight = if (enEmision) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (esFavorito) {
                    Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC93C),
                        modifier = Modifier.size(13.dp))
                }
                if (ChannelChecker.statusOf(canal) == ChannelStatus.OK) {
                    Spacer(Modifier.width(5.dp))
                    Box(Modifier.size(6.dp).background(Color(0xFF4CD07E), CircleShape))
                }
            }
        }
    }

    @Composable
    private fun PanelPeliculas(
        peliculas: List<Movie>,
        cargando: Boolean,
        coleccionActual: String,
        onColeccion: (String) -> Unit,
        onVer: (Movie) -> Unit,
        onMas: () -> Unit
    ) {
        Column {
            Text("Peliculas de dominio publico", color = Color.White,
                fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Internet Archive · libre distribucion", color = TextMuted, fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ArchiveMovies.colecciones) { c ->
                    FocusableCard(
                        onClick = { onColeccion(c.id) },
                        containerColor = if (c.id == coleccionActual) Accent else Color(0x33FFFFFF),
                        focusedContainerColor = Accent,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(c.titulo, color = Color.White, fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
            }
            Spacer(Modifier.height(9.dp))

            when {
                cargando && peliculas.isEmpty() ->
                    Text("Cargando catalogo...", color = Accent, fontSize = 12.sp)
                peliculas.isEmpty() ->
                    Text("No se pudo cargar el catalogo.", color = TextMuted, fontSize = 12.sp)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(peliculas, key = { it.identifier }) { p ->
                        FocusableCard(
                            onClick = { onVer(p) },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color(0x26FFFFFF),
                            focusedContainerColor = Accent,
                            shape = RoundedCornerShape(8.dp)
                        ) { enfocado ->
                            Row(Modifier.fillMaxWidth().padding(7.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = p.posterUrl, contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(width = 34.dp, height = 46.dp)
                                        .background(Color(0xCC0B0E13), RoundedCornerShape(4.dp))
                                )
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        p.title,
                                        color = if (enfocado) Color.White else Color(0xFFDCE1E7),
                                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                    p.year?.let {
                                        Text(it, color = TextMuted, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                        FocusableCard(
                            onClick = onMas,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color(0x33FFFFFF),
                            focusedContainerColor = Accent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                if (cargando) "Cargando..." else "Cargar mas peliculas",
                                color = Color.White, fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 11.dp, horizontal = 14.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PanelExplorar(
        consulta: String,
        onConsulta: (String) -> Unit,
        resultados: List<Channel>,
        categorias: List<String>,
        onCategoria: (String) -> Unit,
        favoritos: Set<String>,
        onCanal: (Channel) -> Unit,
        onFavorito: (Channel) -> Unit
    ) {
        var enfocado by remember { mutableStateOf(false) }
        val foco = remember { FocusRequester() }

        Column {
            Text("Buscar en toda la lista", color = Color.White,
                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp))

            Box(
                Modifier.fillMaxWidth()
                    .background(if (enfocado) Accent.copy(alpha = 0.3f) else Color(0x33FFFFFF),
                        RoundedCornerShape(8.dp))
                    .clickable { runCatching { foco.requestFocus() } }
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            ) {
                if (consulta.isEmpty()) {
                    Text("Canal o categoria...", color = TextMuted, fontSize = 12.5.sp)
                }
                BasicTextField(
                    value = consulta, onValueChange = onConsulta, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(foco)
                        .onFocusChanged { enfocado = it.isFocused }
                )
            }

            if (categorias.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                Text("Categorias", color = TextMuted, fontSize = 10.sp)
                Spacer(Modifier.height(5.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categorias) { g ->
                        FocusableCard(
                            onClick = { onCategoria(g) },
                            containerColor = Color(0x33FFFFFF),
                            focusedContainerColor = Accent,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(g, color = Color.White, fontSize = 11.sp, maxLines = 1,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(9.dp))

            if (consulta.isBlank()) {
                Text("Escribe para buscar en todos los canales.", color = TextMuted, fontSize = 12.sp)
            } else if (resultados.isEmpty()) {
                Text("Ningun canal con ese nombre.", color = TextMuted, fontSize = 12.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(resultados, key = { it.id }) { c ->
                        FocusableCard(
                            onClick = { onCanal(c) },
                            onLongClick = { onFavorito(c) },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color(0x26FFFFFF),
                            focusedContainerColor = Accent,
                            shape = RoundedCornerShape(8.dp)
                        ) { enf ->
                            Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                                Text(
                                    c.name,
                                    color = if (enf) Color.White else Color(0xFFDCE1E7),
                                    fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                // Justo lo que hacia falta: en que categoria esta
                                Text(c.group, color = if (enf) Color.White else TextMuted,
                                    fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun BarraInferior(
        titulo: String,
        estado: String?,
        cargando: Boolean,
        verificando: Boolean,
        progreso: Int,
        total: Int,
        onPantallaCompleta: () -> Unit
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    titulo.ifBlank { "Sin canal" },
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    when {
                        estado != null -> estado.replace("\n", "  ")
                        cargando -> "Cargando..."
                        verificando -> "Verificando canales: $progreso de $total"
                        else -> "OK: menu   ·   ATRAS: pantalla completa"
                    },
                    color = when {
                        estado != null -> Color(0xFFFF8A7E)
                        cargando || verificando -> Accent
                        else -> TextMuted
                    },
                    fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            FocusableCard(
                onClick = onPantallaCompleta,
                containerColor = Color(0x33FFFFFF),
                focusedContainerColor = Accent,
                shape = RoundedCornerShape(7.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Fullscreen, null, tint = Color.White,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pantalla completa", color = Color.White, fontSize = 11.5.sp)
                }
            }
        }
    }
}
