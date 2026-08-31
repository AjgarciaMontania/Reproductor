package com.alvaro.tvplayer.data

/**
 * Catalogos abiertos precargados.
 *
 * Todos apuntan a proyectos de codigo abierto que recopilan enlaces a señales
 * que los propios emisores publican de forma abierta. No se incluye ningun
 * servicio de pago ni contenido con licencia restringida.
 *
 * Fuentes:
 *   https://github.com/Free-TV/IPTV   (lista curada, una URL por canal)
 *   https://github.com/iptv-org/iptv  (catalogo masivo, licencia CC0)
 */
data class Catalog(
    val title: String,
    val subtitle: String,
    val url: String
)

object Catalogs {
    private const val IPTV_ORG = "https://iptv-org.github.io/iptv"
    private const val FREE_TV = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"

    val presets: List<Catalog> = listOf(
        // Va primero: al ser una lista curada (una sola URL por canal, y solo
        // canales ofrecidos gratis oficialmente), falla mucho menos que un
        // catalogo masivo.
        Catalog(
            "Free-TV (recomendada)",
            "Lista curada, menos canales pero mas estables",
            FREE_TV
        ),
        Catalog(
            "Colombia",
            "Canales nacionales y regionales con señal abierta",
            "$IPTV_ORG/countries/co.m3u"
        ),
        Catalog(
            "Noticias",
            "Informativos internacionales en varios idiomas",
            "$IPTV_ORG/categories/news.m3u"
        ),
        Catalog(
            "Deportes",
            "Canales deportivos de emision abierta",
            "$IPTV_ORG/categories/sports.m3u"
        ),
        Catalog(
            "Infantil",
            "Canales para niños",
            "$IPTV_ORG/categories/kids.m3u"
        ),
        Catalog(
            "Educativo",
            "Canales educativos y culturales",
            "$IPTV_ORG/categories/education.m3u"
        ),
        Catalog(
            "Documentales",
            "Canales de documentales y divulgacion",
            "$IPTV_ORG/categories/documentary.m3u"
        ),
        Catalog(
            "Musica",
            "Canales musicales",
            "$IPTV_ORG/categories/music.m3u"
        ),
        Catalog(
            "Peliculas",
            "Canales de cine de emision abierta",
            "$IPTV_ORG/categories/movies.m3u"
        ),
        Catalog(
            "En español",
            "Todo el catalogo en idioma español",
            "$IPTV_ORG/languages/spa.m3u"
        )
    )
}
