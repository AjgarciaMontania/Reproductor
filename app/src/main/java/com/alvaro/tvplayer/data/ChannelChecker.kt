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
 * Un canal caido puede estarlo por bloqueo geografico, por enlace muerto o
 * porque el servidor estaba saturado en ese momento. Por eso los caidos se
 * reintentan periodicamente en segundo plano: muchos vuelven solos.
 */
object ChannelChecker {

    val status = mutableStateMapOf<String, ChannelStatus>()

    var verificando by mutableStateOf(false)
        private set
    var progreso by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    /** Ronda de reintento de caidos en curso (no muestra barra de progreso). */
    var reintentando by mutableStateOf(false)
        private set

    private var prefs: Prefs? = null
    private var listaUrl: String? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(false)
        .build()

    fun statusOf(channel: Channel): ChannelStatus =
        status[channel.id] ?: ChannelStatus.DESCONOCIDO

    fun estaCaido(channel: Channel) = statusOf(channel) == ChannelStatus.CAIDO

    /**
     * Se llama al cargar una lista: limpia lo anterior y recupera de disco el
     * resultado de la ultima verificacion de ESA lista, para no empezar de cero.
     */
    fun iniciar(prefs: Prefs, listaUrl: String, canales: List<Channel>) {
        this.prefs = prefs
        this.listaUrl = listaUrl
        status.clear()
        progreso = 0
        total = 0
        verificando = false

        val (ok, caidos) = prefs.estadosGuardados(listaUrl)
        if (ok.isEmpty() && caidos.isEmpty()) return
        canales.forEach { c ->
            val h = prefs.hashDe(c.id)
            when (h) {
                in ok -> status[c.id] = ChannelStatus.OK
                in caidos -> status[c.id] = ChannelStatus.CAIDO
            }
        }
    }

    fun reset() {
        status.clear()
        progreso = 0
        total = 0
        verificando = false
        reintentando = false
        prefs = null
        listaUrl = null
    }

    fun marcarCaido(channel: Channel) {
        status[channel.id] = ChannelStatus.CAIDO
        persistir()
    }

    fun marcarOk(channel: Channel) {
        status[channel.id] = ChannelStatus.OK
        persistir()
    }

    private fun persistir() {
        val p = prefs ?: return
        val url = listaUrl ?: return
        val ok = status.filterValues { it == ChannelStatus.OK }.keys.toSet()
        val caidos = status.filterValues { it == ChannelStatus.CAIDO }.keys.toSet()
        runCatching { p.guardarEstados(url, ok, caidos) }
    }

    /**
     * Comprueba si el servidor del canal sigue vivo.
     *
     * NO se envia cabecera Range: una emision en directo no es un fichero con
     * posiciones, y muchisimos servidores responden 400, 405 o 416 a un Range.
     * Pedirlo marcaba como caidos canales que funcionaban perfectamente.
     *
     * Y solo se da por caido lo que lo demuestra: un fallo de conexion, o un
     * 404/410 que dice que ahi ya no hay nada. Un 403 puede ser geobloqueo o
     * falta de cabeceras y aun asi reproducirse, asi que no basta para
     * esconder el canal.
     */
    private suspend fun probar(channel: Channel): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder()
                .url(channel.url)
                .header("User-Agent", channel.headers["User-Agent"] ?: "MiReproductorTV/1.0")
            channel.headers.forEach { (k, v) ->
                if (!k.equals("User-Agent", ignoreCase = true)) builder.header(k, v)
            }
            // Se cierra en cuanto llegan las cabeceras: no se descarga video.
            client.newCall(builder.build()).execute().use { r ->
                when (r.code) {
                    404, 410 -> false
                    else -> true
                }
            }
        }.getOrDefault(false)   // sin conexion, DNS fallido o timeout
    }

    /**
     * Verificacion completa con barra de progreso. Si soloDesconocidos es true
     * se saltan los canales cuyo estado ya se conoce de una sesion anterior.
     */
    suspend fun verificar(
        canales: List<Channel>,
        concurrencia: Int = 6,
        soloDesconocidos: Boolean = false
    ) = coroutineScope {
        if (verificando) return@coroutineScope

        val objetivo =
            if (soloDesconocidos) canales.filter { statusOf(it) == ChannelStatus.DESCONOCIDO }
            else canales
        if (objetivo.isEmpty()) return@coroutineScope

        verificando = true
        total = objetivo.size
        progreso = 0

        val hechos = AtomicInteger(0)
        val permisos = Semaphore(concurrencia)

        try {
            objetivo.map { canal ->
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
            objetivo.forEach { c ->
                if (status[c.id] == ChannelStatus.PROBANDO) status.remove(c.id)
            }
            verificando = false
            persistir()
        }
    }

    /**
     * Reintenta en segundo plano los canales marcados como caidos, por si han
     * vuelto. Silencioso: no toca la barra de progreso ni molesta al usuario.
     */
    suspend fun reintentarCaidos(canales: List<Channel>, concurrencia: Int = 4) = coroutineScope {
        if (verificando || reintentando) return@coroutineScope
        val caidos = canales.filter { statusOf(it) == ChannelStatus.CAIDO }
        if (caidos.isEmpty()) return@coroutineScope

        reintentando = true
        val permisos = Semaphore(concurrencia)
        try {
            caidos.map { canal ->
                async {
                    permisos.withPermit {
                        // Solo se sube a OK; si sigue fallando se queda como estaba,
                        // para no hacerlo parpadear en la grilla.
                        if (probar(canal)) status[canal.id] = ChannelStatus.OK
                    }
                }
            }.awaitAll()
        } finally {
            reintentando = false
            persistir()
        }
    }
}
