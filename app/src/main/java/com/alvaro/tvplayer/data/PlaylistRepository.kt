package com.alvaro.tvplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object PlaylistRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Descarga y parsea una lista M3U. */
    suspend fun load(url: String): Playlist = withContext(Dispatchers.IO) {
        val clean = url.trim()
        require(clean.startsWith("http://") || clean.startsWith("https://")) {
            "La URL debe empezar por http:// o https://"
        }

        val request = Request.Builder()
            .url(clean)
            .header("User-Agent", "MiReproductorTV/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("El servidor respondio ${response.code}. Revisa la URL o tus credenciales.")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) error("La lista llego vacia.")

            val playlist = M3uParser.parse(body)
            if (playlist.channels.isEmpty()) {
                error("No se encontro ningun canal. El archivo no parece una lista M3U valida.")
            }
            playlist
        }
    }

    /**
     * Descarga TODOS los catalogos a la vez y los fusiona en una sola lista.
     *
     * Los canales repetidos entre catalogos se descartan comparando la URL del
     * stream, que es lo unico realmente identificativo: el mismo canal aparece
     * con nombres distintos segun la lista. Se conserva la primera aparicion,
     * y como Free-TV va primero en Catalogs, sus entradas (mejor curadas)
     * tienen prioridad sobre las de los catalogos masivos.
     *
     * Un catalogo que falle no rompe la carga: se ignora y siguen los demas.
     */
    suspend fun loadAll(
        catalogos: List<Catalog>,
        onProgress: (hechos: Int, total: Int) -> Unit = { _, _ -> }
    ): Playlist = coroutineScope {
        val hechos = AtomicInteger(0)
        onProgress(0, catalogos.size)

        val resultados = catalogos.map { cat ->
            async(Dispatchers.IO) {
                val r = runCatching { load(cat.url) }.getOrNull()
                onProgress(hechos.incrementAndGet(), catalogos.size)
                r
            }
        }.awaitAll()

        val vistas = HashSet<String>()
        val canales = ArrayList<Channel>()
        var epg: String? = null

        resultados.filterNotNull().forEach { lista ->
            if (epg == null) epg = lista.epgUrl
            lista.channels.forEach { c ->
                if (vistas.add(c.url)) canales += c
            }
        }

        if (canales.isEmpty()) {
            error("No se pudo cargar ningun catalogo. Revisa tu conexion a internet.")
        }
        Playlist(canales, epg)
    }
}
