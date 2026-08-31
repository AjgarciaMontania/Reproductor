package com.alvaro.tvplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Cliente de la API de iptv-org.
 *
 * En vez de llevar una decena de listas escritas a mano, se consultan los
 * indices publicados y se arman los catalogos al vuelo. Asi aparecen todas
 * las categorias y todos los paises que el proyecto publique en cada momento,
 * sin tener que actualizar la app.
 *
 * Endpoints (verificados contra el servicio real):
 *   https://iptv-org.github.io/api/categories.json -> [{id, name, description}]
 *   https://iptv-org.github.io/api/countries.json  -> [{name, code, languages, flag}]
 *
 * Las listas M3U correspondientes viven en:
 *   https://iptv-org.github.io/iptv/categories/<id>.m3u
 *   https://iptv-org.github.io/iptv/countries/<codigo en minusculas>.m3u
 */
object IptvOrgApi {

    private const val API = "https://iptv-org.github.io/api"
    private const val LISTAS = "https://iptv-org.github.io/iptv"

    /** Cajon tecnico sin contenido real: nunca se ofrece. */
    private val EXCLUIDAS = setOf("auto")

    /** Categoria de contenido adulto: solo si se activa expresamente. */
    private const val ADULTOS = "xxx"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private suspend fun pedirArray(url: String): JSONArray? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "MiReproductorTV/1.0")
                .build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                JSONArray(r.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    /**
     * Todas las categorias publicadas.
     * La de adultos solo se incluye si se pide explicitamente.
     */
    suspend fun categorias(incluirAdultos: Boolean = false): List<Catalog> {
        val arr = pedirArray("$API/categories.json") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").lowercase()
                if (id.isBlank() || id in EXCLUIDAS) continue
                if (id == ADULTOS && !incluirAdultos) continue
                val nombre = o.optString("name").ifBlank { id.replaceFirstChar { c -> c.uppercase() } }
                val desc = o.optString("description").take(70)
                add(Catalog(nombre, desc.ifBlank { "Categoria $nombre" }, "$LISTAS/categories/$id.m3u"))
            }
        }.sortedBy { it.title }
    }

    /** Todos los paises publicados. El codigo llega en mayusculas y la URL lo usa en minusculas. */
    suspend fun paises(): List<Catalog> {
        val arr = pedirArray("$API/countries.json") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val codigo = o.optString("code").lowercase()
                if (codigo.isBlank()) continue
                val nombre = o.optString("name").ifBlank { codigo.uppercase() }
                val bandera = o.optString("flag")
                add(
                    Catalog(
                        if (bandera.isBlank()) nombre else "$bandera  $nombre",
                        "Canales de $nombre",
                        "$LISTAS/countries/$codigo.m3u"
                    )
                )
            }
        }.sortedBy { it.title }
    }
}
