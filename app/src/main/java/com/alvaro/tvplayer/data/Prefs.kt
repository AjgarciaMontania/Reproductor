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

    private companion object {
        const val KEY_FAVS = "favorites"
        const val KEY_RECENT = "recent_playlists"
        const val KEY_RECENT_CH = "recent_channels"
        const val KEY_LAST = "last_channel"
        const val KEY_OK = "ok_"
        const val KEY_DEAD = "dead_"
        const val KEY_CHECKED = "checked_"
    }
}
