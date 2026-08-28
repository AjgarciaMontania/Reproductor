package com.alvaro.tvplayer.data

/**
 * Traspaso de la lista cargada entre activities sin serializarla en el Intent
 * (una lista grande revienta el limite de 1 MB de un Bundle).
 */
object PlaylistHolder {
    @Volatile
    var current: Playlist? = null

    @Volatile
    var sourceUrl: String? = null
}
