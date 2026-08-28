package com.alvaro.tvplayer.data

import java.text.Normalizer

/**
 * Utilidades de busqueda.
 *
 * Se normaliza el texto para que los acentos y las mayusculas no estorben:
 * escribir "senal" debe encontrar "Señal Colombia", y "TELEMEDELLIN" debe
 * encontrar "Telemedellín". En listas comunitarias los nombres vienen
 * escritos de mil maneras, asi que comparar en crudo pierde resultados.
 */
object Search {

    private val marcasDiacriticas = Regex("\\p{Mn}+")

    fun normalizar(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(marcasDiacriticas, "")
            .replace('ñ', 'n')
            .replace('Ñ', 'n')
            .lowercase()
            .trim()

    fun coincide(texto: String, consulta: String): Boolean =
        normalizar(texto).contains(consulta)

    /**
     * Ordena para que lo mas parecido salga primero:
     * 1) los que empiezan por lo buscado
     * 2) los que lo contienen antes en el nombre
     * 3) alfabetico
     */
    fun <T> ordenarPorRelevancia(
        items: List<T>,
        consulta: String,
        nombre: (T) -> String
    ): List<T> = items.sortedWith(
        compareBy(
            { if (normalizar(nombre(it)).startsWith(consulta)) 0 else 1 },
            { normalizar(nombre(it)).indexOf(consulta).let { i -> if (i < 0) Int.MAX_VALUE else i } },
            { normalizar(nombre(it)) }
        )
    )
}
