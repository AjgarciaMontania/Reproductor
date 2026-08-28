package com.alvaro.tvplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object PlaylistRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Descarga y parsea una lista M3U. Lanza excepcion con mensaje legible si falla. */
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
}
