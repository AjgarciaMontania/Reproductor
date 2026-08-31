package com.alvaro.tvplayer.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.alvaro.tvplayer.BuildConfig
import java.security.MessageDigest

/**
 * Huella del certificado con el que esta firmada la app INSTALADA.
 *
 * Sirve para resolver de un vistazo el error mas confuso del ciclo de
 * actualizacion: "signatures do not match". Si la huella no coincide con la
 * del keystore propio, es que se instalo el APK equivocado (el de depuracion
 * que genera Actions como artefacto, en vez del publicado en Releases).
 */
object AppSignature {

    /** SHA-256 del certificado instalado, en mayusculas y separado por ':'. */
    fun sha256(context: Context): String? = runCatching {
        val pm = context.packageManager
        val firmas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(
                context.packageName, PackageManager.GET_SIGNING_CERTIFICATES
            )
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }

        val cert = firmas?.firstOrNull()?.toByteArray() ?: return@runCatching null
        MessageDigest.getInstance("SHA-256")
            .digest(cert)
            .joinToString(":") { "%02X".format(it) }
    }.getOrNull()

    /** ¿Esta firmada con la llave propia (la que permite actualizar en sitio)? */
    fun esLlavePropia(context: Context): Boolean {
        val esperada = BuildConfig.FIRMA_ESPERADA.replace(":", "").uppercase()
        val actual = sha256(context)?.replace(":", "")?.uppercase() ?: return false
        return esperada.isNotBlank() && esperada == actual
    }

    /** Explicacion lista para mostrar. */
    fun diagnostico(context: Context): String {
        val actual = sha256(context) ?: return "No se pudo leer la firma de la app."
        return if (esLlavePropia(context)) {
            "Firma correcta: las actualizaciones se instalaran encima."
        } else {
            "ATENCION: esta app NO esta firmada con tu llave.\n" +
            "Seguramente se instalo el APK de depuracion (artefacto de Actions) " +
            "en vez del publicado en Releases. Desinstalala e instala el APK " +
            "que aparece en la pestaña Releases del repositorio.\n" +
            "Huella instalada: ${actual.take(23)}..."
        }
    }
}
