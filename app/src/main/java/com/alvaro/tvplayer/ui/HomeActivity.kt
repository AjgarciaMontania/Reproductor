@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.alvaro.tvplayer.ui

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
    EXPLORAR("EXPLORAR", Icons.Filled.Search),
    LISTAS("LISTAS", Icons.Filled.Settings)
}

/**
 * Pantalla unica y punto de entrada.
 *
 * Al abrir carga TODOS los catalogos abiertos a la vez, los fusiona en una
 * sola lista sin duplicados y verifica cuales responden. No hay pantalla
 * previa de seleccion: se entra directo al reproductor, con el canal de
 * fondo y el menu flotando encima.
 */
class HomeActivity : ComponentActivity() {

    private var verifyJob: Job? = null
    private var manejadorTeclas: ((Int) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { TvTheme { Raiz() } }
    }

    override fun onDestroy() {
        super.onDestroy()
        verifyJob?.cancel()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (manejadorTeclas?.invoke(keyCode) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    // ------------------------------------------------------------------ raiz

    @Composable
    private fun Raiz() {
        var playlist by remember { mutableStateOf(PlaylistHolder.current) }
        var cargando by remember { mutableStateOf(playlist == null) }
        var hechos by remember { mutableIntStateOf(0) }
        var totalCat by remember { mutableIntStateOf(Catalogs.presets.size) }
        var errorCarga by remember { mutableStateOf<String?>(null) }

        fun cargarTodo() {
            cargando = true
            errorCarga = null
            hechos = 0
            lifecycleScope.launch {
                runCatching {
                    PlaylistRepository.loadAll(Catalogs.presets) { h, t ->
                        hechos = h; totalCat = t
                    }
                }.onSuccess {
                    PlaylistHolder.current = it
                    PlaylistHolder.sourceUrl = "catalogos-abiertos"
                    playlist = it
                    cargando = false
                }.onFailure {
                    cargando = false
                    errorCarga = it.message ?: "No se pudieron cargar los catalogos."
                }
            }
        }

        fun cargarUna(url: String) {
            cargando = true
            errorCarga = null
            lifecycleScope.launch {
                runCatching { PlaylistRepository.load(url) }
                    .onSuccess {
                        Prefs(this@HomeActivity).addRecentPlaylist(url)
                        PlaylistHolder.current = it
                        PlaylistHolder.sourceUrl = url
                        playlist = it
                        cargando = false
                    }
                    .onFailure {
                        cargando = false
                        errorCarga = it.message ?: "No se pudo cargar la lista."
                    }
            }
        }

        LaunchedEffect(Unit) { if (playlist == null) cargarTodo() }

        val lista = playlist
        if (lista == null) {
            PantallaCarga(cargando, hechos, totalCat, errorCarga) { cargarTodo() }
        } else {
            HomeScreen(lista, cargando, ::cargarTodo, ::cargarUna)
        }
    }

    @Composable
    private fun PantallaCarga(
        cargando: Boolean,
        hechos: Int,
        total: Int,
        error: String?,
        onReintentar: () -> Unit
    ) {
        Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).background(Accent, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text("M", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black) }
                    Spacer(Modifier.width(13.dp))
                    Text("MI TV", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(22.dp))

                if (error != null) {
                    Text(error, color = Color(0xFFFF8A7E), fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                    FocusableCard(
                        onClick = onReintentar,
                        containerColor = Surface2, focusedContainerColor = Accent
                    ) {
                        Text("Reintentar", color = Color.White, fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                    }
                } else {
                    Text("Cargando catalogos... $hechos de $total", color = Accent, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.width(260.dp).height(5.dp)
                            .background(Surface2, RoundedCornerShape(3.dp))
                    ) {
                        val frac = if (total > 0) hechos.toFloat() / total else 0f
                        Box(
                            Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(5.dp)
                                .background(Accent, RoundedCornerShape(3.dp))
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Se unen todas las listas abiertas en una sola",
                        color = TextMuted, fontSize = 11.5.sp)
                }
            }
        }
    }

    // ------------------------------------------------------------- pantalla

    @Composable
    private fun HomeScreen(
        playlist: Playlist,
        recargando: Boolean,
        onRecargarTodo: () -> Unit,
        onCargarUna: (String) -> Unit
    ) {
        val prefs = remember { Prefs(this) }
        val actividad = this

        var seccion by remember { mutableStateOf(Seccion.TV) }
        var menuVisible by remember { mutableStateOf(true) }
        var favoritos by remember { mutableStateOf(prefs.favorites()) }
        var categoria by remember(playlist) { mutableStateOf(playlist.groups.firstOrNull() ?: "") }
        var consulta by remember { mutableStateOf("") }

        var canalActual by remember { mutableStateOf<Channel?>(null) }
        var tituloActual by remember { mutableStateOf("") }
        var estado by remember { mutableStateOf<String?>(null) }
        var cargando by remember { mutableStateOf(false) }

        // Zapping y controles sobre el video a pantalla completa
        var cola by remember { mutableStateOf<List<Channel>>(emptyList()) }
        var controles by remember { mutableStateOf(false) }
        var buscadorFs by remember { mutableStateOf(false) }
        var consultaFs by remember { mutableStateOf("") }

        var coleccion by remember { mutableStateOf(ArchiveMovies.colecciones.first().id) }
        var peliculas by remember { mutableStateOf<List<Movie>>(emptyList()) }
        var paginaPelis by remember { mutableIntStateOf(1) }
        var cargandoPelis by remember { mutableStateOf(false) }

        // Actualizaciones
        var update by remember { mutableStateOf<UpdateInfo?>(null) }
        var updateMsg by remember { mutableStateOf<String?>(null) }
        var progresoDescarga by remember { mutableIntStateOf(-1) }
        val versionNombre = remember {
            runCatching { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" }
                .getOrDefault("?")
        }
        val versionCodigo = remember {
            runCatching {
                val i = packageManager.getPackageInfo(packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) i.longVersionCode.toInt()
                else @Suppress("DEPRECATION") i.versionCode
            }.getOrDefault(1)
        }

        val exo = remember { ExoPlayer.Builder(actividad).build().apply { playWhenReady = true } }

        DisposableEffect(Unit) {
            val l = object : Player.Listener {
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
            exo.addListener(l)
            val receptor = UpdateInstaller.registerStatusReceiver(actividad) {
                updateMsg = it; progresoDescarga = -1
            }
            onDispose {
                exo.removeListener(l); exo.release()
                runCatching { actividad.unregisterReceiver(receptor) }
            }
        }

        fun reproducir(url: String, cabeceras: Map<String, String>) {
            estado = null; cargando = true
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent(cabeceras["User-Agent"] ?: "MiReproductorTV/1.0")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20_000).setReadTimeoutMs(20_000)
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

        fun verCanal(c: Channel, contexto: List<Channel> = emptyList()) {
            // El contexto es la lista por la que se movera el zapping: la
            // categoria desde la que se eligio el canal.
            if (contexto.isNotEmpty()) cola = contexto
            canalActual = c; tituloActual = c.name
            prefs.addRecentChannel(c)
            reproducir(c.url, c.headers)
        }

        /** Avanza o retrocede dentro de la cola, dando la vuelta al llegar al final. */
        fun cambiarCanal(paso: Int) {
            val lista = cola.ifEmpty {
                playlist.channels.filter { !ChannelChecker.estaCaido(it) }
            }
            if (lista.isEmpty()) return
            val i = lista.indexOfFirst { it.id == canalActual?.id }
            val siguiente = if (i < 0) 0 else (((i + paso) % lista.size) + lista.size) % lista.size
            verCanal(lista[siguiente], lista)
            controles = true
        }

        fun verPelicula(p: Movie) {
            canalActual = null; tituloActual = p.title
            estado = "Buscando el video..."; cargando = true
            lifecycleScope.launch {
                val url = ArchiveMovies.urlDeVideo(p.identifier)
                if (url == null) { cargando = false; estado = "Sin video reproducible." }
                else reproducir(url, emptyMap())
            }
        }

        fun instalarUpdate(info: UpdateInfo) {
            if (!UpdateInstaller.canInstall(actividad)) {
                updateMsg = "Autoriza a esta app a instalar aplicaciones."
                runCatching { startActivity(UpdateInstaller.permissionSettingsIntent(actividad)) }
                return
            }
            progresoDescarga = 0; updateMsg = null
            lifecycleScope.launch {
                runCatching {
                    val apk = UpdateInstaller.download(actividad, info) { progresoDescarga = it }
                    UpdateInstaller.install(actividad, apk)
                }.onFailure { progresoDescarga = -1; updateMsg = it.message ?: "Fallo la actualizacion." }
            }
        }

        var autoIniciada by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { update = UpdateChecker.check(versionCodigo) }
        LaunchedEffect(update) {
            val i = update
            // Solo se descarga sola si la firma coincide; si no, instalarla
            // fracasaria igualmente y solo generaria ruido.
            if (i != null && !autoIniciada && UpdateInstaller.canInstall(actividad)
                && AppSignature.esLlavePropia(actividad)) {
                autoIniciada = true
                instalarUpdate(i)
            }
        }

        // Verificacion automatica y reintento de caidos
        LaunchedEffect(playlist) {
            ChannelChecker.iniciar(prefs, PlaylistHolder.sourceUrl ?: "lista", playlist.channels)
            verifyJob?.cancel()
            verifyJob = lifecycleScope.launch {
                ChannelChecker.verificar(playlist.channels, soloDesconocidos = true)
            }
        }
        LaunchedEffect(playlist) {
            while (true) {
                delay(4 * 60 * 1000L)
                ChannelChecker.reintentarCaidos(playlist.channels)
            }
        }
        LaunchedEffect(playlist) {
            if (canalActual == null) {
                playlist.channels.firstOrNull { !ChannelChecker.estaCaido(it) }?.let { verCanal(it) }
            }
        }
        LaunchedEffect(seccion, coleccion) {
            if (seccion == Seccion.PELICULAS && peliculas.isEmpty()) {
                cargandoPelis = true; paginaPelis = 1
                peliculas = runCatching { ArchiveMovies.listar(coleccion, 1) }.getOrDefault(emptyList())
                cargandoPelis = false
            }
        }

        // Los controles se ocultan solos para no tapar el video.
        LaunchedEffect(controles, canalActual) {
            if (controles && !buscadorFs) {
                delay(6000)
                controles = false
            }
        }

        // Mando a distancia en pantalla completa: arriba/abajo cambia de canal,
        // OK muestra los controles. Con el menu o el buscador abiertos no se
        // interfiere, para que las flechas sirvan para navegar por la lista.
        DisposableEffect(menuVisible, buscadorFs, cola, canalActual) {
            manejadorTeclas = { codigo ->
                if (menuVisible || buscadorFs) false
                else when (codigo) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                        cambiarCanal(-1); true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        cambiarCanal(1); true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        controles = !controles; true
                    }
                    KeyEvent.KEYCODE_SEARCH -> {
                        buscadorFs = true; consultaFs = ""; true
                    }
                    else -> false
                }
            }
            onDispose { manejadorTeclas = null }
        }

        BackHandler(enabled = buscadorFs || controles || menuVisible) {
            when {
                buscadorFs -> buscadorFs = false
                controles -> controles = false
                menuVisible -> menuVisible = false
            }
        }

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
            else -> emptyList()
        }

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
                modifier = Modifier.fillMaxSize().clickable {
                    // Con el menu abierto, tocar lo cierra. En pantalla completa,
                    // tocar muestra los controles de canal sin abrir todo el menu.
                    if (menuVisible) menuVisible = false
                    else controles = !controles
                }
            )

            if (menuVisible) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0f to Color(0xE6000000), 0.45f to Color(0x66000000),
                            0.7f to Color(0x99000000), 1f to Color(0xE6000000)
                        )
                    )
                )
            }

            AnimatedVisibility(menuVisible, enter = fadeIn(), exit = fadeOut()) {
                Column(Modifier.fillMaxSize()) {

                    BarraSuperior(playlist.channels.size, recargando)

                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Column(
                            Modifier.width(225.dp).fillMaxHeight().padding(start = 20.dp, top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Seccion.entries.forEach { s ->
                                ItemMenu(s, s == seccion) { seccion = s }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        Column(Modifier.width(335.dp).fillMaxHeight().padding(end = 16.dp, top = 2.dp)) {
                            when (seccion) {
                                Seccion.PELICULAS -> PanelPeliculas(
                                    peliculas, cargandoPelis, coleccion,
                                    { coleccion = it; peliculas = emptyList() },
                                    { verPelicula(it); menuVisible = false },
                                    {
                                        if (!cargandoPelis) {
                                            cargandoPelis = true
                                            lifecycleScope.launch {
                                                val sig = paginaPelis + 1
                                                val mas = runCatching {
                                                    ArchiveMovies.listar(coleccion, sig)
                                                }.getOrDefault(emptyList())
                                                if (mas.isNotEmpty()) {
                                                    peliculas = peliculas + mas; paginaPelis = sig
                                                }
                                                cargandoPelis = false
                                            }
                                        }
                                    }
                                )

                                Seccion.EXPLORAR -> PanelExplorar(
                                    consulta, { consulta = it }, canalesPanel,
                                    playlist.groups.filter {
                                        consulta.isNotBlank() &&
                                            Search.coincide(it, Search.normalizar(consulta))
                                    },
                                    { categoria = it; seccion = Seccion.TV; consulta = "" },
                                    { verCanal(it, canalesPanel); menuVisible = false },
                                    { prefs.toggleFavorite(it); favoritos = prefs.favorites() }
                                )

                                Seccion.LISTAS -> PanelListas(
                                    totalCanales = playlist.channels.size,
                                    categorias = playlist.groups.size,
                                    versionNombre = versionNombre,
                                    versionCodigo = versionCodigo,
                                    diagnosticoFirma = AppSignature.diagnostico(actividad),
                                    firmaOk = AppSignature.esLlavePropia(actividad),
                                    update = update,
                                    updateMsg = updateMsg,
                                    progreso = progresoDescarga,
                                    onRecargar = onRecargarTodo,
                                    onCargarUrl = onCargarUna,
                                    onActualizar = { update?.let { instalarUpdate(it) } },
                                    onComprobar = {
                                        lifecycleScope.launch {
                                            when (val r = UpdateChecker.comprobar(versionCodigo)) {
                                                is UpdateResult.Disponible -> { update = r.info; updateMsg = null }
                                                is UpdateResult.AlDia ->
                                                    updateMsg = "Ya tienes la ultima version ($versionNombre)."
                                                is UpdateResult.SinPublicaciones ->
                                                    updateMsg = "No hay ninguna version publicada todavia."
                                                is UpdateResult.Error ->
                                                    updateMsg = "No se pudo comprobar: ${r.mensaje}"
                                            }
                                        }
                                    }
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
                                    onCanal = { verCanal(it, canalesPanel); menuVisible = false },
                                    onFavorito = { prefs.toggleFavorite(it); favoritos = prefs.favorites() }
                                )
                            }
                        }
                    }

                    BarraInferior(tituloActual, estado, cargando,
                        ChannelChecker.verificando, ChannelChecker.progreso, ChannelChecker.total
                    ) { menuVisible = false }
                }
            }

            // ============ PANTALLA COMPLETA ============
            if (!menuVisible) {

                if (!controles && !buscadorFs) {
                    Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                        estado?.let {
                            Text(it, color = Color(0xFFFF8A7E), fontSize = 13.sp)
                            Spacer(Modifier.height(5.dp))
                        }
                        if (cargando) Text("Cargando...", color = Accent, fontSize = 12.sp)
                        Text(tituloActual, color = Color.White.copy(alpha = 0.85f),
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Toca la pantalla para los controles",
                            color = TextMuted, fontSize = 11.sp)
                    }
                }

                // ---- controles de canal sobre el video ----
                AnimatedVisibility(
                    visible = controles && !buscadorFs,
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xE6000000))))
                            .padding(horizontal = 26.dp, vertical = 20.dp)
                    ) {
                        Text(tituloActual, color = Color.White, fontSize = 19.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        canalActual?.let {
                            val pos = cola.indexOfFirst { c -> c.id == it.id }
                            Text(
                                if (pos >= 0) "${it.group}   ·   ${pos + 1} de ${cola.size}"
                                else it.group,
                                color = TextMuted, fontSize = 11.5.sp
                            )
                        }
                        estado?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it.replace("\n", "  "), color = Color(0xFFFF8A7E), fontSize = 11.sp)
                        }

                        Spacer(Modifier.height(13.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BotonControl(Icons.Filled.SkipPrevious, "Anterior") { cambiarCanal(-1) }
                            Spacer(Modifier.width(9.dp))
                            BotonControl(Icons.Filled.SkipNext, "Siguiente") { cambiarCanal(1) }
                            Spacer(Modifier.width(9.dp))
                            BotonControl(Icons.Filled.Search, "Buscar") {
                                buscadorFs = true; consultaFs = ""
                            }
                            Spacer(Modifier.width(9.dp))
                            BotonControl(Icons.Filled.Menu, "Menu") {
                                menuVisible = true; controles = false
                            }
                        }
                    }
                }

                // ---- buscador sin salir de pantalla completa ----
                if (buscadorFs) {
                    val q = Search.normalizar(consultaFs)
                    val hallados = if (q.isBlank()) emptyList()
                        else Search.ordenarPorRelevancia(
                            playlist.channels.filter {
                                Search.coincide(it.name, q) && !ChannelChecker.estaCaido(it)
                            }.take(200), q
                        ) { it.name }

                    Box(
                        Modifier.align(Alignment.CenterEnd)
                            .width(360.dp).fillMaxHeight()
                            .background(Color(0xF00B0E13))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Buscar canal", color = Color.White, fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                FocusableCard(
                                    onClick = { buscadorFs = false },
                                    containerColor = Color(0x33FFFFFF),
                                    focusedContainerColor = Accent,
                                    shape = RoundedCornerShape(7.dp)
                                ) {
                                    Text("Cerrar", color = Color.White, fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                                }
                            }
                            Spacer(Modifier.height(9.dp))
                            CampoTexto(consultaFs, { consultaFs = it }, "Nombre del canal...")
                            Spacer(Modifier.height(9.dp))

                            when {
                                consultaFs.isBlank() -> Text(
                                    "Escribe para buscar entre todos los canales activos.",
                                    color = TextMuted, fontSize = 11.5.sp)
                                hallados.isEmpty() -> Text(
                                    "Ningun canal activo con ese nombre.",
                                    color = TextMuted, fontSize = 11.5.sp)
                                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(hallados, key = { it.id }) { c ->
                                        FocusableCard(
                                            onClick = {
                                                verCanal(c, hallados)
                                                buscadorFs = false
                                                controles = true
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            containerColor = Color(0x26FFFFFF),
                                            focusedContainerColor = Accent,
                                            shape = RoundedCornerShape(8.dp)
                                        ) { enf ->
                                            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                                Text(c.name,
                                                    color = if (enf) Color.White else Color(0xFFDCE1E7),
                                                    fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(c.group,
                                                    color = if (enf) Color.White else TextMuted,
                                                    fontSize = 9.5.sp, maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------- componentes

    @Composable
    private fun BarraSuperior(totalCanales: Int, recargando: Boolean) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(29.dp).background(Accent, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center) {
                Text("M", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(9.dp))
            Column {
                Text("MI TV", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(
                    if (recargando) "actualizando listas..." else "$totalCanales canales",
                    color = if (recargando) Accent else TextMuted, fontSize = 9.sp
                )
            }
            Spacer(Modifier.weight(1f))
            listOf(Icons.Filled.Search, Icons.Filled.Star, Icons.Filled.History, Icons.Filled.Wifi)
                .forEach {
                    Icon(it, null, tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 15.dp).size(18.dp))
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
                Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(seccion.icono, null,
                    tint = if (activa || enfocado) Color.White else TextMuted,
                    modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(10.dp))
                Text(seccion.etiqueta,
                    color = if (activa || enfocado) Color.White else Color(0xFFC3CAD3),
                    fontSize = 13.sp,
                    fontWeight = if (activa) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }

    @Composable
    private fun PanelCanales(
        titulo: String, categorias: List<String>, categoriaActual: String,
        onCategoria: (String) -> Unit, canales: List<Channel>, favoritos: Set<String>,
        actual: Channel?, onCanal: (Channel) -> Unit, onFavorito: (Channel) -> Unit
    ) {
        Column {
            Text(titulo.ifBlank { "Canales" }, color = Color.White, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 7.dp))

            if (categorias.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(categorias) { g ->
                        FocusableCard(
                            onClick = { onCategoria(g) },
                            containerColor = if (g == categoriaActual) Accent else Color(0x33FFFFFF),
                            focusedContainerColor = Accent, shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(g, color = Color.White, fontSize = 10.5.sp, maxLines = 1,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (canales.isEmpty()) {
                Text("No hay canales aqui todavia.", color = TextMuted, fontSize = 12.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(canales, key = { _, c -> c.id }) { i, c ->
                        FilaCanal(i + 1, c, c.id in favoritos, c.id == actual?.id,
                            { onCanal(c) }, { onFavorito(c) })
                    }
                }
            }
        }
    }

    @Composable
    private fun FilaCanal(
        numero: Int, canal: Channel, esFavorito: Boolean, enEmision: Boolean,
        onClick: () -> Unit, onLongClick: () -> Unit
    ) {
        FocusableCard(
            onClick = onClick, onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (enEmision) Color(0x4D4C8DFF) else Color(0x26FFFFFF),
            focusedContainerColor = Accent, shape = RoundedCornerShape(8.dp)
        ) { enfocado ->
            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(37.dp).background(Color(0xCC0B0E13), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center) {
                    if (!canal.logo.isNullOrBlank()) {
                        AsyncImage(model = canal.logo, contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp))
                    } else {
                        Text(canal.name.take(2).uppercase(), color = Accent,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (enEmision) "▶  ${canal.name}" else "$numero  ${canal.name}",
                    color = if (enfocado || enEmision) Color.White else Color(0xFFDCE1E7),
                    fontSize = 12.sp,
                    fontWeight = if (enEmision) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (esFavorito) {
                    Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC93C),
                        modifier = Modifier.size(12.dp))
                }
                if (ChannelChecker.statusOf(canal) == ChannelStatus.OK) {
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(6.dp).background(Color(0xFF4CD07E), CircleShape))
                }
            }
        }
    }

    @Composable
    private fun PanelPeliculas(
        peliculas: List<Movie>, cargando: Boolean, coleccionActual: String,
        onColeccion: (String) -> Unit, onVer: (Movie) -> Unit, onMas: () -> Unit
    ) {
        Column {
            Text("Peliculas de dominio publico", color = Color.White,
                fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Internet Archive · libre distribucion", color = TextMuted, fontSize = 9.5.sp,
                modifier = Modifier.padding(bottom = 7.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items(ArchiveMovies.colecciones) { c ->
                    FocusableCard(
                        onClick = { onColeccion(c.id) },
                        containerColor = if (c.id == coleccionActual) Accent else Color(0x33FFFFFF),
                        focusedContainerColor = Accent, shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(c.titulo, color = Color.White, fontSize = 10.5.sp,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                cargando && peliculas.isEmpty() ->
                    Text("Cargando catalogo...", color = Accent, fontSize = 12.sp)
                peliculas.isEmpty() ->
                    Text("No se pudo cargar el catalogo.", color = TextMuted, fontSize = 12.sp)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(peliculas, key = { it.identifier }) { p ->
                        FocusableCard(
                            onClick = { onVer(p) }, modifier = Modifier.fillMaxWidth(),
                            containerColor = Color(0x26FFFFFF), focusedContainerColor = Accent,
                            shape = RoundedCornerShape(8.dp)
                        ) { enf ->
                            Row(Modifier.fillMaxWidth().padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(model = p.posterUrl, contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(width = 32.dp, height = 43.dp)
                                        .background(Color(0xCC0B0E13), RoundedCornerShape(4.dp)))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.title,
                                        color = if (enf) Color.White else Color(0xFFDCE1E7),
                                        fontSize = 11.5.sp, fontWeight = FontWeight.Medium,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    p.year?.let { Text(it, color = TextMuted, fontSize = 9.5.sp) }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(5.dp))
                        FocusableCard(onClick = onMas, modifier = Modifier.fillMaxWidth(),
                            containerColor = Color(0x33FFFFFF), focusedContainerColor = Accent,
                            shape = RoundedCornerShape(8.dp)) {
                            Text(if (cargando) "Cargando..." else "Cargar mas peliculas",
                                color = Color.White, fontSize = 11.5.sp,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 13.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PanelExplorar(
        consulta: String, onConsulta: (String) -> Unit, resultados: List<Channel>,
        categorias: List<String>, onCategoria: (String) -> Unit,
        onCanal: (Channel) -> Unit, onFavorito: (Channel) -> Unit
    ) {
        Column {
            Text("Buscar en toda la lista", color = Color.White, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 7.dp))

            CampoTexto(consulta, onConsulta, "Canal o categoria...")

            if (categorias.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Categorias", color = TextMuted, fontSize = 9.5.sp)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(categorias) { g ->
                        FocusableCard(onClick = { onCategoria(g) },
                            containerColor = Color(0x33FFFFFF), focusedContainerColor = Accent,
                            shape = RoundedCornerShape(20.dp)) {
                            Text(g, color = Color.White, fontSize = 10.5.sp, maxLines = 1,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                consulta.isBlank() ->
                    Text("Escribe para buscar en todos los canales.", color = TextMuted, fontSize = 12.sp)
                resultados.isEmpty() ->
                    Text("Ningun canal con ese nombre.", color = TextMuted, fontSize = 12.sp)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(resultados, key = { it.id }) { c ->
                        FocusableCard(onClick = { onCanal(c) }, onLongClick = { onFavorito(c) },
                            modifier = Modifier.fillMaxWidth(), containerColor = Color(0x26FFFFFF),
                            focusedContainerColor = Accent, shape = RoundedCornerShape(8.dp)) { enf ->
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(c.name, color = if (enf) Color.White else Color(0xFFDCE1E7),
                                    fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(c.group, color = if (enf) Color.White else TextMuted,
                                    fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PanelListas(
        totalCanales: Int, categorias: Int,
        versionNombre: String, versionCodigo: Int,
        diagnosticoFirma: String, firmaOk: Boolean,
        update: UpdateInfo?, updateMsg: String?, progreso: Int,
        onRecargar: () -> Unit, onCargarUrl: (String) -> Unit,
        onActualizar: () -> Unit, onComprobar: () -> Unit
    ) {
        var url by remember { mutableStateOf("") }
        var xHost by remember { mutableStateOf("") }
        var xUser by remember { mutableStateOf("") }
        var xPass by remember { mutableStateOf("") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            item {
                Text("Listas y ajustes", color = Color.White, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold)
                Text("$totalCanales canales · $categorias categorias",
                    color = TextMuted, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                Boton("Recargar todos los catalogos", onRecargar)
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text("Tu propia lista M3U", color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                CampoTexto(url, { url = it }, "http://servidor/lista.m3u")
                Spacer(Modifier.height(5.dp))
                Boton("Cargar esta lista") { if (url.isNotBlank()) onCargarUrl(url.trim()) }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text("Xtream Codes", color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                CampoTexto(xHost, { xHost = it }, "http://servidor:8080")
                Spacer(Modifier.height(4.dp))
                CampoTexto(xUser, { xUser = it }, "usuario")
                Spacer(Modifier.height(4.dp))
                CampoTexto(xPass, { xPass = it }, "clave")
                Spacer(Modifier.height(5.dp))
                Boton("Conectar Xtream") {
                    if (xHost.isNotBlank() && xUser.isNotBlank() && xPass.isNotBlank()) {
                        onCargarUrl(M3uParser.xtreamToM3u(xHost, xUser, xPass))
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("Version", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Instalada: $versionNombre (codigo $versionCodigo)",
                    color = TextMuted, fontSize = 10.5.sp)
                Spacer(Modifier.height(6.dp))

                // Diagnostico de firma: resuelve el error "signatures do not match"
                Box(
                    Modifier.fillMaxWidth()
                        .background(
                            if (firmaOk) Color(0x1A4CD07E) else Color(0x333A1714),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    Text(diagnosticoFirma,
                        color = if (firmaOk) Color(0xFF4CD07E) else Color(0xFFFFCF5C),
                        fontSize = 10.5.sp)
                }

                updateMsg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Color(0xFFFFCF5C), fontSize = 10.5.sp)
                }

                Spacer(Modifier.height(6.dp))
                when {
                    progreso in 0..99 ->
                        Text("Descargando actualizacion... $progreso%", color = Accent, fontSize = 11.sp)
                    update != null -> {
                        Text("Nueva version: ${update.versionName}", color = Accent, fontSize = 11.5.sp)
                        Spacer(Modifier.height(5.dp))
                        Boton("Instalar actualizacion", onActualizar)
                    }
                    else -> Boton("Buscar actualizaciones", onComprobar)
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    /** Boton redondo de los controles sobre el video. */
    @Composable
    private fun BotonControl(icono: ImageVector, etiqueta: String, onClick: () -> Unit) {
        FocusableCard(
            onClick = onClick,
            containerColor = Color(0x40FFFFFF),
            focusedContainerColor = Accent,
            shape = RoundedCornerShape(24.dp)
        ) { enfocado ->
            Row(
                Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icono, null, tint = Color.White, modifier = Modifier.size(17.dp))
                if (enfocado) {
                    Spacer(Modifier.width(6.dp))
                    Text(etiqueta, color = Color.White, fontSize = 11.5.sp)
                }
            }
        }
    }

    @Composable
    private fun Boton(texto: String, onClick: () -> Unit) {
        FocusableCard(
            onClick = onClick, modifier = Modifier.fillMaxWidth(),
            containerColor = Surface2, focusedContainerColor = Accent,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(texto, color = Color.White, fontSize = 11.5.sp,
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 13.dp))
        }
    }

    @Composable
    private fun CampoTexto(valor: String, onCambio: (String) -> Unit, marcador: String) {
        var enfocado by remember { mutableStateOf(false) }
        val foco = remember { FocusRequester() }
        Box(
            Modifier.fillMaxWidth()
                .background(if (enfocado) Accent.copy(alpha = 0.3f) else Color(0x33FFFFFF),
                    RoundedCornerShape(8.dp))
                .clickable { runCatching { foco.requestFocus() } }
                .padding(horizontal = 11.dp, vertical = 10.dp)
        ) {
            if (valor.isEmpty()) Text(marcador, color = TextMuted, fontSize = 11.5.sp)
            BasicTextField(
                value = valor, onValueChange = onCambio, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 11.5.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth().focusRequester(foco)
                    .onFocusChanged { enfocado = it.isFocused }
            )
        }
    }

    @Composable
    private fun BarraInferior(
        titulo: String, estado: String?, cargando: Boolean,
        verificando: Boolean, progreso: Int, total: Int,
        onPantallaCompleta: () -> Unit
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(titulo.ifBlank { "Sin canal" }, color = Color.White, fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        estado != null -> estado.replace("\n", "  ")
                        cargando -> "Cargando..."
                        verificando -> "Verificando canales: $progreso de $total"
                        else -> "Toca la pantalla o pulsa ATRAS para ver a pantalla completa"
                    },
                    color = when {
                        estado != null -> Color(0xFFFF8A7E)
                        cargando || verificando -> Accent
                        else -> TextMuted
                    },
                    fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            FocusableCard(onClick = onPantallaCompleta, containerColor = Color(0x33FFFFFF),
                focusedContainerColor = Accent, shape = RoundedCornerShape(7.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Fullscreen, null, tint = Color.White,
                        modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Pantalla completa", color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}
