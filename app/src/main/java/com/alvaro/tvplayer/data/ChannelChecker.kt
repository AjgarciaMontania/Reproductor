package com.alvaro.tvplayer.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class ChannelStatus { DESCONOCIDO, PROBANDO, OK, CAIDO }

/**
 * Comprueba si los canales responden, sin llegar a reproducirlos.
 *
 * Pide solo el primer kilobyte de cada URL con un tiempo de espera corto:
 * basta para saber si el servidor sigue ahi y acepta la peticion, y evita
 * gastar datos descargando video.
 *
 * Un canal marcado como caido puede fallar por estar bloqueado en tu pais,
 * porque la direccion ya no existe, o porque el servidor esta saturado en
 * ese momento. Por eso se puede volver a verificar cuando se quiera.
 */
object ChannelChecker {

    /** Estado por canal, observable desde Compose. */
    val status = mutableStateMapOf<String, ChannelStatus>()

    var verificando by mutableStateOf(false)
        private set
    var progreso by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(false)
        .build()

    fun statusOf(channel: Channel): ChannelStatus =
        status[channel.id] ?: ChannelStatus.DESCONOCIDO

    /** Se llama al cargar una lista nueva. */
    fun reset() {
        status.clear()
        progreso = 0
        total = 0
        verificando = false
    }

    /** Marca un canal como caido tras un fallo real de reproduccion. */
    fun marcarCaido(channel: Channel) {
        status[channel.id] = ChannelStatus.CAIDO
    }

    /** Marca un canal como bueno cuando ha llegado a reproducirse. */
    fun marcarOk(channel: Channel) {
        status[channel.id] = ChannelStatus.OK
    }

    private suspend fun probar(channel: Channel): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder()
                .url(channel.url)
                .header("User-Agent", channel.headers["User-Agent"] ?: "MiReproductorTV/1.0")
                // Solo el principio del recurso: no queremos descargar video.
                .header("Range", "bytes=0-1023")
            channel.headers.forEach { (k, v) ->
                if (!k.equals("User-Agent", ignoreCase = true)) builder.header(k, v)
            }
            client.newCall(builder.build()).execute().use { response ->
                // 2xx incluye el 206 de contenido parcial.
                response.isSuccessful
            }
        }.getOrDefault(false)   // URL invalida, timeout o DNS fallido = caido
    }

    /**
     * Verifica una lista de canales con paralelismo limitado, para no
     * saturar la red del televisor ni el propio dispositivo.
     */
    suspend fun verificar(canales: List<Channel>, concurrencia: Int = 6) = coroutineScope {
        if (verificando) return@coroutineScope
        verificando = true
        total = canales.size
        progreso = 0

        val hechos = AtomicInteger(0)
        val permisos = Semaphore(concurrencia)

        try {
            canales.map { canal ->
                async {
                    permisos.withPermit {
                        status[canal.id] = ChannelStatus.PROBANDO
                        val ok = probar(canal)
                        status[canal.id] = if (ok) ChannelStatus.OK else ChannelStatus.CAIDO
                        progreso = hechos.incrementAndGet()
                    }
                }
            }.awaitAll()
        } finally {
            // Si se cancela a medias, los que quedaron en PROBANDO vuelven a desconocido.
            canales.forEach { c ->
                if (status[c.id] == ChannelStatus.PROBANDO) status.remove(c.id)
            }
            verificando = false
        }
    }
}
