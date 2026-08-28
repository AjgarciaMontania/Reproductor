package com.alvaro.tvplayer.data

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource

/**
 * Traduce los fallos de reproduccion a algo que se pueda leer y accionar.
 *
 * Lo importante para el usuario es distinguir tres situaciones:
 *   - el canal ya no existe o esta bloqueado  -> problema de la LISTA
 *   - el dispositivo no sabe decodificarlo    -> problema del APARATO
 *   - no hay conexion o el servidor falla     -> problema temporal
 */
object PlaybackErrors {

    fun describe(e: PlaybackException): String {
        // Si el fallo viene de una respuesta HTTP, el codigo es lo mas informativo.
        var cause: Throwable? = e.cause
        var depth = 0
        while (cause != null && depth < 8) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return httpMessage(cause.responseCode)
            }
            cause = cause.cause
            depth++
        }

        return when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                "Sin conexion con el servidor del canal.\nRevisa tu internet o prueba otro canal."

            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "El servidor tardo demasiado en responder.\nSuele ser saturacion: reintenta mas tarde."

            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "El canal ya no existe en esa direccion.\nLa lista esta desactualizada."

            PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                "El servidor rechazo la peticion.\nSuele ser bloqueo por pais."

            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
                "El canal usa HTTP sin cifrar y el sistema lo bloqueo."

            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
                "La direccion no devuelve un video.\nProbablemente el enlace ya no sirve."

            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                "La señal llega corrupta o incompleta.\nEl canal puede estar fuera de emision."

            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                "Formato de transmision no soportado por la app."

            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                "Tu dispositivo no puede decodificar este canal.\n" +
                "Suele pasar con emisiones en H.265/HEVC en equipos modestos."

            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                "La calidad de este canal supera lo que tu dispositivo admite."

            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED ->
                "Este canal esta protegido con DRM.\nLa app no reproduce contenido cifrado."

            else ->
                "No se pudo reproducir este canal.\n(${e.errorCodeName})"
        }
    }

    private fun httpMessage(code: Int): String = when (code) {
        401, 403 ->
            "Error $code: el servidor niega el acceso.\n" +
            "Casi siempre es bloqueo por pais, o el canal exige cabeceras que la lista no trae."

        404, 410 ->
            "Error $code: el canal ya no existe en esa direccion.\nLa lista esta desactualizada."

        451 ->
            "Error 451: bloqueado por razones legales en tu region."

        429 ->
            "Error 429: demasiadas peticiones al servidor.\nEspera un momento y reintenta."

        in 500..599 ->
            "Error $code: fallo del servidor del canal.\nNo es problema tuyo; reintenta mas tarde."

        else ->
            "El servidor respondio $code."
    }
}
