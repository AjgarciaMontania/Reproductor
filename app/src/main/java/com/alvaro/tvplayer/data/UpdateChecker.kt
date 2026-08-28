package com.alvaro.tvplayer.data

import com.alvaro.tvplayer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String
)

/**
 * Comprueba si hay una version nueva publicada en los Releases del repositorio.
 *
 * El workflow de GitHub Actions sube en cada release un fichero latest.json.
 * GitHub expone SIEMPRE el ultimo en esta URL estable, sin necesidad de API ni token:
 *
 *   https://github.com/<owner>/<repo>/releases/latest/download/latest.json
 *
 * Es una descarga por HTTPS desde tu propio repositorio. Ademas Android verifica la
 * firma del APK antes de instalarlo: si no esta firmado con tu misma llave, el sistema
 * rechaza la actualizacion. Y toda instalacion pide confirmacion explicita en pantalla.
 */
object UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val manifestUrl: String
        get() = "https://github.com/${BuildConfig.UPDATE_OWNER}/" +
                "${BuildConfig.UPDATE_REPO}/releases/latest/download/latest.json"

    /**
     * Devuelve la informacion de actualizacion solo si hay una version MAS NUEVA
     * que la instalada. Devuelve null si ya estas al dia o si algo falla:
     * un fallo al comprobar nunca debe impedir usar la app.
     */
    suspend fun check(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(manifestUrl)
                .header("User-Agent", "MiReproductorTV")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string().orEmpty())

                val info = UpdateInfo(
                    versionCode = json.getInt("versionCode"),
                    versionName = json.optString("versionName", "?"),
                    apkUrl = json.getString("apkUrl"),
                    notes = json.optString("notes", "")
                )

                // Solo aceptamos APKs servidos por GitHub sobre HTTPS.
                val trusted = info.apkUrl.startsWith("https://github.com/") ||
                              info.apkUrl.startsWith("https://objects.githubusercontent.com/")
                if (!trusted) return@withContext null

                if (info.versionCode > currentVersionCode) info else null
            }
        }.getOrNull()
    }
}
