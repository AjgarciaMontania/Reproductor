package com.alvaro.tvplayer.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Almacenamiento local: favoritos, listas usadas, canales vistos y el
 * resultado de la ultima verificacion.
 *
 * Todo queda en el dispositivo. La app no envia nada a ningun servidor propio.
 *
 * Los identificadores de canal se guardan como hash: un id es "nombre|url" y
 * puede ser larguisimo; con miles de canales, guardarlos enteros engorda
 * SharedPreferences sin necesidad.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("mitv", Context.MODE_PRIVATE)

    private fun clave(id: String) = id.hashCode().toString()

    // --- Favoritos ---

    fun favorites(): Set<String> = sp.getStringSet(KEY_FAVS, emptySet()) ?: emptySet()

    fun isFavorite(channel: Channel): Boolean = channel.id in favorites()

    fun toggleFavorite(channel: Channel) {
        val current = favorites().toMutableSet()
        if (!current.add(channel.id)) current.remove(channel.id)
        sp.edit().putStringSet(KEY_FAVS, current).apply()
    }

    // --- Listas recientes ---

    fun recentPlaylists(): List<String> =
        sp.getString(KEY_RECENT, "")!!.split("\n").filter { it.isNotBlank() }

    fun addRecentPlaylist(url: String) {
        val list = (listOf(url) + recentPlaylists()).distinct().take(8)
        sp.edit().putString(KEY_RECENT, list.joinToString("\n")).apply()
    }

    fun removeRecentPlaylist(url: String) {
        sp.edit().putString(KEY_RECENT, recentPlaylists().filterNot { it == url }.joinToString("\n")).apply()
    }

    // --- Canales vistos recientemente ---

    fun recentChannels(): List<String> =
        sp.getString(KEY_RECENT_CH, "")!!.split("\n").filter { it.isNotBlank() }

    fun addRecentChannel(channel: Channel) {
        val list = (listOf(channel.id) + recentChannels()).distinct().take(30)
        sp.edit().putString(KEY_RECENT_CH, list.joinToString("\n")).apply()
    }

    fun setLastChannel(url: String) = sp.edit().putString(KEY_LAST, url).apply()

    fun lastChannel(): String? = sp.getString(KEY_LAST, null)

    // --- Resultado de la verificacion ---

    /** Guarda que canales respondieron y cuales no, por lista. */
    fun guardarEstados(listaUrl: String, ok: Set<String>, caidos: Set<String>) {
        val p = clave(listaUrl)
        sp.edit()
            .putStringSet("$KEY_OK$p", ok.map(::clave).toSet())
            .putStringSet("$KEY_DEAD$p", caidos.map(::clave).toSet())
            .putLong("$KEY_CHECKED$p", System.currentTimeMillis())
            .apply()
    }

    /** Devuelve (hashes OK, hashes caidos) de la ultima verificacion de esa lista. */
    fun estadosGuardados(listaUrl: String): Pair<Set<String>, Set<String>> {
        val p = clave(listaUrl)
        return (sp.getStringSet("$KEY_OK$p", emptySet()) ?: emptySet()) to
               (sp.getStringSet("$KEY_DEAD$p", emptySet()) ?: emptySet())
    }

    fun hashDe(id: String) = clave(id)

    // --- Listas que el usuario añadio a mano ---
    //
    // Se guardan aparte de las "recientes" porque cumplen otra funcion: al
    // recargar los catalogos hay que volver a fusionarlas, o se pierden todas
    // las listas que la persona habia ido sumando.

    fun listasExtra(): List<String> =
        sp.getString(KEY_EXTRA, "")!!.split("\n").filter { it.isNotBlank() }

    fun anadirListaExtra(url: String) {
        val lista = (listasExtra() + url).distinct().takeLast(20)
        sp.edit().putString(KEY_EXTRA, lista.joinToString("\n")).apply()
    }

    fun quitarListaExtra(url: String) {
        sp.edit().putString(KEY_EXTRA, listasExtra().filterNot { it == url }.joinToString("\n")).apply()
    }


    // --- Progreso de peliculas y series (no aplica a TV en vivo) ---

    /** Guarda por donde va una pelicula. Se ignora si aun no hay duracion. */
    fun guardarProgreso(id: String, posicionMs: Long, duracionMs: Long) {
        if (duracionMs <= 0L || posicionMs < 0L) return
        sp.edit().putString("$KEY_PROG${clave(id)}", "$posicionMs|$duracionMs").apply()
    }

    /** Devuelve (posicion, duracion) en milisegundos, o null si nunca se vio. */
    fun progreso(id: String): Pair<Long, Long>? {
        val bruto = sp.getString("$KEY_PROG${clave(id)}", null) ?: return null
        val partes = bruto.split("|")
        if (partes.size != 2) return null
        val pos = partes[0].toLongOrNull() ?: return null
        val dur = partes[1].toLongOrNull() ?: return null
        return if (dur > 0) pos to dur else null
    }

    /** Fraccion vista, de 0 a 1. Cero si no hay registro. */
    fun fraccionVista(id: String): Float {
        val (pos, dur) = progreso(id) ?: return 0f
        return (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Posicion desde la que reanudar. Se descarta el principio (menos de 30 s,
     * no merece la pena) y el final (mas del 95%, ya se termino de ver).
     */
    fun posicionParaReanudar(id: String): Long {
        val (pos, dur) = progreso(id) ?: return 0L
        if (pos < 30_000L) return 0L
        if (pos > dur * 0.95) return 0L
        return pos
    }

    fun olvidarProgreso(id: String) {
        sp.edit().remove("$KEY_PROG${clave(id)}").apply()
    }

    private companion object {
        const val KEY_FAVS = "favorites"
        const val KEY_RECENT = "recent_playlists"
        const val KEY_RECENT_CH = "recent_channels"
        const val KEY_LAST = "last_channel"
        // El sufijo 2 descarta los resultados guardados con el criterio
        // anterior, que usaba cabecera Range y marcaba como caidos muchos
        // canales que si funcionaban.
        const val KEY_OK = "ok2_"
        const val KEY_DEAD = "dead2_"
        const val KEY_CHECKED = "checked_"
        const val KEY_PROG = "prog_"
        const val KEY_EXTRA = "listas_extra"
    }
}
