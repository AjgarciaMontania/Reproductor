package com.alvaro.tvplayer.data

/**
 * Parser de listas M3U / M3U8 extendidas.
 *
 * Soporta:
 *   #EXTM3U url-tvg="..."           -> URL del EPG (XMLTV)
 *   #EXTINF:-1 tvg-id="" tvg-name="" tvg-logo="" group-title="",Nombre
 *   #EXTGRP:Categoria               -> categoria alternativa
 *   #EXTVLCOPT:http-user-agent=...  -> cabeceras por canal
 *   #EXTVLCOPT:http-referrer=...
 *   #KODIPROP:...                   -> se ignora, no rompe el parseo
 */
object M3uParser {

    private val attrRegex = Regex("""([a-zA-Z0-9\-_]+)\s*=\s*"([^"]*)"""")

    fun parse(content: String): Playlist {
        val channels = mutableListOf<Channel>()
        var epgUrl: String? = null

        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var pendingTvgId: String? = null
        var pendingHeaders = mutableMapOf<String, String>()

        fun reset() {
            pendingName = null
            pendingLogo = null
            pendingGroup = null
            pendingTvgId = null
            pendingHeaders = mutableMapOf()
        }

        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    epgUrl = attrRegex.findAll(line)
                        .firstOrNull { it.groupValues[1].equals("url-tvg", true) ||
                                       it.groupValues[1].equals("x-tvg-url", true) }
                        ?.groupValues?.get(2)
                        ?.split(",")?.firstOrNull()?.trim()
                        ?.takeIf { it.isNotBlank() }
                }

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    reset()
                    val attrs = attrRegex.findAll(line).associate {
                        it.groupValues[1].lowercase() to it.groupValues[2]
                    }
                    pendingLogo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    pendingGroup = attrs["group-title"]?.takeIf { it.isNotBlank() }
                    pendingTvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }

                    // El nombre visible empieza tras la PRIMERA coma que no este entre comillas.
                    // Usar la ultima coma rompe nombres como: "Canal 5, El Pueblo".
                    val displayName = nameAfterAttributes(line)
                    pendingName = displayName?.takeIf { it.isNotBlank() }
                        ?: attrs["tvg-name"]?.takeIf { it.isNotBlank() }
                        ?: "Canal sin nombre"
                }

                line.startsWith("#EXTGRP", ignoreCase = true) -> {
                    val g = line.substringAfter(':').trim()
                    if (g.isNotBlank() && pendingGroup == null) pendingGroup = g
                }

                line.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                    val opt = line.substringAfter(':').trim()
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    if (value.isNotBlank()) {
                        when (key) {
                            "http-user-agent" -> pendingHeaders["User-Agent"] = value
                            "http-referrer", "http-referer" -> pendingHeaders["Referer"] = value
                            "http-origin" -> pendingHeaders["Origin"] = value
                        }
                    }
                }

                // Cualquier otra directiva (#KODIPROP, #EXTHTTP, comentarios) se ignora.
                line.startsWith("#") -> Unit

                else -> {
                    val name = pendingName
                    if (name != null) {
                        channels += Channel(
                            name = name,
                            url = line,
                            logo = pendingLogo,
                            group = pendingGroup ?: "Sin categoria",
                            tvgId = pendingTvgId,
                            headers = pendingHeaders.toMap()
                        )
                        reset()
                    }
                    // Una URL sin #EXTINF previo se descarta: lista mal formada.
                }
            }
        }

        return Playlist(channels, epgUrl)
    }

    /**
     * Devuelve el nombre visible de una linea #EXTINF: todo lo que sigue a la primera
     * coma que aparece fuera de comillas. Asi se respetan tanto los atributos que
     * contienen comas (group-title="Cine, clasico") como los nombres que las llevan.
     */
    private fun nameAfterAttributes(line: String): String? {
        var inQuotes = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return line.substring(i + 1).trim()
            }
        }
        return null
    }

    /**
     * Convierte credenciales Xtream Codes en la URL M3U equivalente.
     * host puede venir como "http://servidor:8080" o solo "servidor:8080".
     */
    fun xtreamToM3u(host: String, user: String, pass: String): String {
        val base = host.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
        }
        return "$base/get.php?username=${user.trim()}&password=${pass.trim()}&type=m3u_plus&output=ts"
    }
}
