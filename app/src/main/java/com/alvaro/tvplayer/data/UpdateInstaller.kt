package com.alvaro.tvplayer.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Descarga el APK de la nueva version y lanza la instalacion.
 *
 * Se usa PackageInstaller (la API vigente; ACTION_INSTALL_PACKAGE esta obsoleta).
 * El sistema SIEMPRE muestra una pantalla de confirmacion: la app no puede instalar
 * nada a espaldas del usuario. Y como el APK va firmado con la misma llave que el
 * instalado, la actualizacion se aplica encima sin perder favoritos ni ajustes.
 */
object UpdateInstaller {

    const val ACTION_INSTALL_STATUS = "com.alvaro.tvplayer.INSTALL_STATUS"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Descarga el APK a la cache interna informando del progreso (0..100).
     * Devuelve el fichero descargado.
     */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "update-${info.versionCode}.apk")
        if (target.exists()) target.delete()

        val request = Request.Builder()
            .url(info.apkUrl)
            .header("User-Agent", "MiReproductorTV")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Descarga fallida (${response.code})")
            val body = response.body ?: error("Respuesta vacia")
            val total = body.contentLength()

            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
        }

        if (target.length() < 1024) {
            target.delete()
            error("El archivo descargado no es valido.")
        }
        target
    }

    /**
     * Entrega el APK al instalador del sistema. Al terminar de escribir la sesion,
     * Android abre la pantalla de confirmacion de instalacion.
     */
    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        val sessionId = installer.createSession(params)

        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("payload", 0, apk.length()).use { output ->
                    input.copyTo(output, 64 * 1024)
                    session.fsync(output)
                }
            }

            val intent = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    /**
     * ¿Tiene la app permiso para instalar APKs? Desde Android 8 el usuario debe
     * concederlo explicitamente por app.
     */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    /** Pantalla de ajustes donde se concede ese permiso. */
    fun permissionSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
        }

    /**
     * Escucha el resultado de la instalacion. Si el sistema pide confirmacion,
     * reenvia la pantalla correspondiente.
     */
    fun registerStatusReceiver(context: Context, onMessage: (String) -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        @Suppress("DEPRECATION")
                        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        confirm?.let { ctx.startActivity(it) }
                    }
                    PackageInstaller.STATUS_SUCCESS ->
                        onMessage("Actualizacion instalada.")
                    else -> {
                        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        onMessage("No se pudo instalar: ${msg ?: "error desconocido"}")
                    }
                }
            }
        }
        val filter = IntentFilter(ACTION_INSTALL_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        return receiver
    }
}
