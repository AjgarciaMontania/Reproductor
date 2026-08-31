package com.alvaro.tvplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente del archivo de dominio publico de Internet Archive.
 *
 * Son peliculas cuyo copyright ha expirado o que se distribuyen libremente,
 * alojadas y servidas por la propia Internet Archive. No se redistribuye nada:
 * la app reproduce directamente desde archive.org.
 *
 * Endpoints (verificados contra el servicio real):
 *   Busqueda : https://archive.org/advancedsearch.php?...&output=json
 *              devuelve {"response":{"numFound":N,"docs":[{...}]}}
 *   Metadatos: https://archive.org/metadata/<identifier>
 *              devuelve {"server":...,"dir":...,"files":[...],"metadata":{...}}
 *   Caratula : https://archive.org/services/img/<identifier>
 *
 * La URL de descarga se arma como https://archive.org/download/<id>/<fichero>,
 * que es la ruta estable y redirige al servidor que toque en cada momento.
 */
object ArchiveMovies {

    data class Coleccion(val id: String, val titulo: String)

    /** Colecciones comprobadas: ambas existen y tienen miles de items. */
    val colecciones = listOf(
        Coleccion("feature_films", "Cine clasico"),
        Coleccion("animationandcartoons", "Animacion")
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val POR_PAGINA = 48

    /**
     * Lista peliculas de una coleccion. Si se pasa consulta, filtra por titulo.
     * `pagina` empieza en 1.
     */
    suspend fun listar(
        coleccion: String,
        pagina: Int = 1,
        consulta: String? = null
    ): List<Movie> = withContext(Dispatchers.IO) {
        val q = buildString {
            append("collection:$coleccion AND mediatype:movies")
            if (!consulta.isNullOrBlank()) {
                // Se escapan las comillas para no romper la sintaxis de la consulta.
                val limpio = consulta.trim().replace("\"", " ")
                append(" AND title:(\"$limpio\")")
            }
        }

        val url = "https://archive.org/advancedsearch.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("fl[]", "identifier")
            .addQueryParameter("fl[]", "title")
            .addQueryParameter("fl[]", "year")
            .addQueryParameter("fl[]", "description")
            .addQueryParameter("rows", POR_PAGINA.toString())
            .addQueryParameter("page", pagina.coerceAtLeast(1).toString())
            .addQueryParameter("output", "json")
            .addQueryParameter("sort[]", "downloads desc")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MiReproductorTV/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Archive.org respondio ${response.code}")
            val raiz = JSONObject(response.body?.string().orEmpty())
            val docs = raiz.optJSONObject("response")?.optJSONArray("docs")
                ?: return@withContext emptyList()

            buildList {
                for (i in 0 until docs.length()) {
                    val d = docs.optJSONObject(i) ?: continue
                    val id = d.optString("identifier").takeIf { it.isNotBlank() } ?: continue
                    add(
                        Movie(
                            identifier = id,
                            // Si el buscador no devuelve titulo, el identificador sirve de apaño.
                            title = textoDe(d, "title").ifBlank { id.replace('_', ' ') },
                            year = textoDe(d, "year").takeIf { it.isNotBlank() },
                            description = textoDe(d, "description").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }
    }

    /**
     * Resuelve la URL reproducible de una pelicula consultando sus ficheros.
     * Devuelve null si el item no tiene ningun video utilizable.
     */
    suspend fun urlDeVideo(identifier: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://archive.org/metadata/$identifier")
                .header("User-Agent", "MiReproductorTV/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val raiz = JSONObject(response.body?.string().orEmpty())
                val ficheros = raiz.optJSONArray("files") ?: return@withContext null

                val candidatos = mutableListOf<Pair<String, Int>>()
                for (i in 0 until ficheros.length()) {
                    val f = ficheros.optJSONObject(i) ?: continue
                    val nombre = f.optString("name")
                    if (nombre.isBlank()) continue
                    // Las miniaturas viven en una carpeta aparte.
                    if (nombre.contains("/.") || nombre.contains(".thumbs/")) continue

                    val formato = f.optString("format").lowercase()
                    val ext = nombre.substringAfterLast('.', "").lowercase()

                    // Preferencia: mp4 (lo que mejor decodifica un TV Box), luego el resto.
                    val prioridad = when {
                        ext == "mp4" && formato.contains("h.264") -> 0
                        ext == "mp4" -> 1
                        formato.contains("mpeg4") -> 2
                        ext == "webm" -> 3
                        ext == "ogv" -> 4
                        ext == "mpg" || ext == "mpeg" -> 5
                        ext == "avi" -> 6
                        else -> continue
                    }
                    candidatos += nombre to prioridad
                }

                val elegido = candidatos.minByOrNull { it.second }?.first ?: return@withContext null

                // Ruta estable: archive.org redirige al servidor correspondiente.
                "https://archive.org/download/".toHttpUrl().newBuilder()
                    .addPathSegment(identifier)
                    .apply { elegido.split('/').forEach { addPathSegment(it) } }
                    .build()
                    .toString()
            }
        }.getOrNull()
    }

    /** Lee un campo que Archive.org puede devolver como texto o como lista. */
    private fun textoDe(obj: JSONObject, clave: String): String {
        val valor = obj.opt(clave) ?: return ""
        return when (valor) {
            is JSONArray -> (0 until valor.length())
                .mapNotNull { valor.opt(it)?.toString() }
                .joinToString(" ")
                .trim()
            else -> valor.toString().trim()
        }
    }
}
