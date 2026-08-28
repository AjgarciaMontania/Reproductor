package com.alvaro.tvplayer.data

/**
 * Un canal o entrada VOD de una lista M3U.
 *
 * headers guarda las cabeceras que algunas listas exigen (User-Agent, Referer),
 * declaradas en la lista con #EXTVLCOPT.
 */
data class Channel(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String = "Sin categoria",
    val tvgId: String? = null,
    val headers: Map<String, String> = emptyMap()
) {
    val id: String get() = "$name|$url"
}

data class Playlist(
    val channels: List<Channel>,
    val epgUrl: String? = null
) {
    val groups: List<String>
        get() = channels.map { it.group }.distinct().sorted()
}
