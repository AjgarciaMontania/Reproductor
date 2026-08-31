package com.alvaro.tvplayer.data

/**
 * Una pelicula del archivo de dominio publico de Internet Archive.
 *
 * La URL de reproduccion no se conoce hasta consultar los ficheros del item,
 * asi que se resuelve solo cuando el usuario elige verla.
 */
data class Movie(
    val identifier: String,
    val title: String,
    val year: String? = null,
    val description: String? = null
) {
    /** Caratula generada por Archive.org para cualquier item. */
    val posterUrl: String
        get() = "https://archive.org/services/img/$identifier"
}
