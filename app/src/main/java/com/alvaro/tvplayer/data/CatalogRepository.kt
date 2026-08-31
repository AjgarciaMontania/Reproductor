package com.alvaro.tvplayer.data

/**
 * Une las dos fuentes de catalogos para no depender de ninguna.
 *
 *   FIJAS: la decena de listas escritas dentro de la app. No necesitan red
 *          para enumerarse, siempre estan y sobreviven a cualquier caida.
 *          Incluyen Free-TV, que no pertenece a iptv-org y por tanto la API
 *          nunca la devolveria.
 *
 *   API:   los indices de iptv-org. Muchas mas listas y siempre al dia, pero
 *          dependen de que el servicio responda.
 *
 * Estrategia: se piden las dos y se unen, descartando repetidos por URL. Si la
 * API no responde se sigue con las fijas y se avisa; nunca se queda sin nada.
 *
 * La carga inicial de la app (PlaylistRepository.loadAll) usa SOLO las fijas
 * a proposito: arrancar no debe depender de que un servicio externo conteste.
 */
object CatalogRepository {

    enum class Origen { MIXTO, SOLO_FIJAS }

    data class Resultado(
        val catalogos: List<Catalog>,
        val origen: Origen,
        val mensaje: String
    )

    /** Categorias: las de la API mas las fijas que la API no cubre. */
    suspend fun categorias(incluirAdultos: Boolean): Resultado {
        val api = runCatching { IptvOrgApi.categorias(incluirAdultos) }.getOrDefault(emptyList())
        return unir(api, Catalogs.presets)
    }

    /** Paises: los de la API mas las fijas que no esten ya cubiertas. */
    suspend fun paises(): Resultado {
        val api = runCatching { IptvOrgApi.paises() }.getOrDefault(emptyList())
        return unir(api, Catalogs.presets.filter { it.url.contains("/countries/") })
    }

    /** Las listas incluidas en la app, sin tocar la red. */
    fun fijas(): List<Catalog> = Catalogs.presets

    private fun unir(api: List<Catalog>, fijas: List<Catalog>): Resultado {
        if (api.isEmpty()) {
            return Resultado(
                fijas,
                Origen.SOLO_FIJAS,
                "La API no respondio. Se usan las ${fijas.size} listas incluidas en la app."
            )
        }
        val yaEstan = api.mapTo(HashSet()) { it.url }
        val extra = fijas.filter { it.url !in yaEstan }
        return Resultado(
            api + extra,
            Origen.MIXTO,
            "${api.size} desde la API" + if (extra.isNotEmpty()) " + ${extra.size} propias" else ""
        )
    }
}
