package com.alvaro.tvplayer.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.alvaro.tvplayer.data.Channel
import com.alvaro.tvplayer.data.Prefs
import kotlinx.coroutines.delay

class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_INDEX = "index"

        /** La cola de reproduccion se pasa por memoria, no por Intent. */
        @Volatile
        var queue: List<Channel> = emptyList()
    }

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (queue.isEmpty()) {
            finish()
            return
        }

        val startIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, queue.lastIndex)
        setContent { TvTheme { PlayerScreen(startIndex) } }
    }

    @Composable
    private fun PlayerScreen(startIndex: Int) {
        val prefs = remember { Prefs(this) }
        var index by remember { mutableIntStateOf(startIndex) }
        var showInfo by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        var buffering by remember { mutableStateOf(true) }

        val channel = queue[index]

        val exo = remember {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("MiReproductorTV/1.0")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20_000)
                .setReadTimeoutMs(20_000)

            ExoPlayer.Builder(this)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory))
                )
                .build()
                .also { p ->
                    p.playWhenReady = true
                    player = p
                }
        }

        // Cambio de canal
        LaunchedEffect(index) {
            error = null
            buffering = true
            showInfo = true

            val ch = queue[index]
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(ch.headers["User-Agent"] ?: "MiReproductorTV/1.0")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20_000)
                .setReadTimeoutMs(20_000)
                .apply {
                    val extra = ch.headers.filterKeys { it != "User-Agent" }
                    if (extra.isNotEmpty()) setDefaultRequestProperties(extra)
                }

            exo.setMediaSource(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(this@PlayerActivity, httpFactory))
                    .createMediaSource(MediaItem.fromUri(ch.url))
            )
            exo.prepare()
            prefs.setLastChannel(ch.url)
        }

        DisposableEffect(Unit) {
            val listener = object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    buffering = false
                    error = "No se pudo reproducir este canal.\n${e.errorCodeName}"
                }

                override fun onPlaybackStateChanged(state: Int) {
                    buffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) error = null
                }
            }
            exo.addListener(listener)
            onDispose {
                exo.removeListener(listener)
                exo.release()
                player = null
            }
        }

        // El panel de informacion se oculta solo
        LaunchedEffect(showInfo, index) {
            if (showInfo) {
                delay(4000)
                showInfo = false
            }
        }

        // Zapping con el D-pad
        DisposableEffect(Unit) {
            keyHandler = { keyCode ->
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                        index = if (index == 0) queue.lastIndex else index - 1; true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        index = if (index == queue.lastIndex) 0 else index + 1; true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> {
                        showInfo = !showInfo; true
                    }
                    else -> false
                }
            }
            onDispose { keyHandler = null }
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
                modifier = Modifier.fillMaxSize()
            )

            if (buffering && error == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Cargando...", color = Color.White, fontSize = 16.sp)
                }
            }

            error?.let { msg ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(msg, color = Color(0xFFFF8A7E), fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Arriba / abajo para cambiar de canal",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showInfo,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xE6000000))
                            )
                        )
                        .padding(horizontal = 40.dp, vertical = 28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!channel.logo.isNullOrBlank()) {
                        AsyncImage(
                            model = channel.logo,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(64.dp)
                                .padding(end = 16.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            channel.name,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${channel.group}   ·   canal ${index + 1} de ${queue.size}",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        "OK: info   ·   ARRIBA/ABAJO: cambiar   ·   ATRAS: salir",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    private var keyHandler: ((Int) -> Boolean)? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyHandler?.invoke(keyCode) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }
}
