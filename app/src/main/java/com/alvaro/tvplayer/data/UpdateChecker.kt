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

/** Resultado detallado de una comprobacion, para poder explicar que paso. */
sealed class UpdateResult {
    data class Disponible(val info: UpdateInfo) : UpdateResult()
    data object AlDia : UpdateResult()
    data object SinPublicaciones : UpdateResult()
    data class Error(val mensaje: String) : UpdateResult()
}

/**
 * Comprueba si hay una version nueva publicada en los Releases del repositorio.
 *
 * IMPORTANTE: no basta con hacer push. La app lee el fichero latest.json que
 * el workflow "Publicar version" adjunta a cada Release. Mientras no exista
 * ningun Release publicado, esta URL devuelve 404 y no hay nada que actualizar:
 *
 *   https://github.com/<owner>/<repo>/releases/latest/download/latest.json
 *
 * Es una descarga por HTTPS desde tu propio repositorio. Android verifica ademas
 * la firma del APK antes de instalarlo: si no lleva tu misma llave, el sistema
 * rechaza la actualizacion. Y toda instalacion pide confirmacion en pantalla.
 */
object UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val manifestUrl: String
        get() = "https://github.com/${BuildConfig.UPDATE_OWNER}/" +
                "${BuildConfig.UPDATE_REPO}/releases/latest/download/latest.json"

    /**
     * Comprobacion silenciosa del arranque: devuelve la actualizacion solo si
     * hay una version mas nueva. Cualquier fallo se traga, porque no poder
     * comprobar nunca debe impedir usar la app.
     */
    suspend fun check(currentVersionCode: Int): UpdateInfo? =
        (comprobar(currentVersionCode) as? UpdateResult.Disponible)?.info

    /** Comprobacion explicita: informa de lo que ha ocurrido. */
    suspend fun comprobar(currentVersionCode: Int): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(manifestUrl)
                .header("User-Agent", "MiReproductorTV")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return@withContext UpdateResult.SinPublicaciones
                }
                if (!response.isSuccessful) {
                    return@withContext UpdateResult.Error(
                        "El servidor respondio ${response.code}."
                    )
                }

                val cuerpo = response.body?.string().orEmpty()
                if (cuerpo.isBlank()) {
                    return@withContext UpdateResult.Error("La respuesta llego vacia.")
                }

                val json = JSONObject(cuerpo)
                val info = UpdateInfo(
                    versionCode = json.getInt("versionCode"),
                    versionName = json.optString("versionName", "?"),
                    apkUrl = json.getString("apkUrl"),
                    notes = json.optString("notes", "")
                )

                // Solo se aceptan APKs servidos por GitHub sobre HTTPS.
                val fiable = info.apkUrl.startsWith("https://github.com/") ||
                             info.apkUrl.startsWith("https://objects.githubusercontent.com/")
                if (!fiable) {
                    return@withContext UpdateResult.Error(
                        "La direccion de descarga no es de GitHub; se ignora por seguridad."
                    )
                }

                if (info.versionCode > currentVersionCode) UpdateResult.Disponible(info)
                else UpdateResult.AlDia
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "No se pudo conectar.")
        }
    }
}
