package com.alvaro.tvplayer.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Almacenamiento local: favoritos y listas usadas recientemente.
 * Todo queda en el dispositivo. La app no envia nada a ningun servidor propio.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("mitv", Context.MODE_PRIVATE)

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
        sp.getString(KEY_RECENT, "")!!
            .split("\n")
            .filter { it.isNotBlank() }

    fun addRecentPlaylist(url: String) {
        val list = (listOf(url) + recentPlaylists()).distinct().take(8)
        sp.edit().putString(KEY_RECENT, list.joinToString("\n")).apply()
    }

    fun removeRecentPlaylist(url: String) {
        val list = recentPlaylists().filterNot { it == url }
        sp.edit().putString(KEY_RECENT, list.joinToString("\n")).apply()
    }

    // --- Ultimo canal visto ---

    fun setLastChannel(url: String) = sp.edit().putString(KEY_LAST, url).apply()

    fun lastChannel(): String? = sp.getString(KEY_LAST, null)

    private companion object {
        const val KEY_FAVS = "favorites"
        const val KEY_RECENT = "recent_playlists"
        const val KEY_LAST = "last_channel"
    }
}
