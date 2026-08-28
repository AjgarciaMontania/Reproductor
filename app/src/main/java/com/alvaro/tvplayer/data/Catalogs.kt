package com.alvaro.tvplayer.data

/**
 * Catalogos abiertos precargados.
 *
 * Son las listas publicadas por el proyecto open-source iptv-org (licencia CC0),
 * que recopila enlaces a señales que los propios emisores publican de forma abierta.
 * No se incluye ningun servicio de pago ni contenido con licencia restringida.
 *
 * Fuente: https://github.com/iptv-org/iptv
 */
data class Catalog(
    val title: String,
    val subtitle: String,
    val url: String
)

object Catalogs {
    private const val BASE = "https://iptv-org.github.io/iptv"

    val presets: List<Catalog> = listOf(
        Catalog(
            "Colombia",
            "Canales nacionales y regionales con señal abierta",
            "$BASE/countries/co.m3u"
        ),
        Catalog(
            "Noticias",
            "Informativos internacionales en varios idiomas",
            "$BASE/categories/news.m3u"
        ),
        Catalog(
            "Infantil",
            "Canales para niños",
            "$BASE/categories/kids.m3u"
        ),
        Catalog(
            "Educativo",
            "Canales educativos y culturales",
            "$BASE/categories/education.m3u"
        ),
        Catalog(
            "Documentales",
            "Canales de documentales y divulgacion",
            "$BASE/categories/documentary.m3u"
        ),
        Catalog(
            "Musica",
            "Canales musicales",
            "$BASE/categories/music.m3u"
        ),
        Catalog(
            "Peliculas",
            "Canales de cine de emision abierta",
            "$BASE/categories/movies.m3u"
        ),
        Catalog(
            "En español",
            "Todo el catalogo en idioma español",
            "$BASE/languages/spa.m3u"
        )
    )
}
