# Cómo obtener el APK

El proyecto se entrega en código, no compilado. Aquí tienes tres caminos, del que menos
te exige al que más control te da.

---

## Opción 1 — GitHub lo compila por ti (no instalas nada)

La más cómoda si no quieres bajar Android Studio (que pesa varios GB). GitHub compila el
APK en sus servidores, gratis, y te lo deja para descargar.

1. Crea una cuenta en [github.com](https://github.com) si no la tienes.
2. Crea un repositorio nuevo. Puede ser **privado**, funciona igual.
3. Sube el contenido de esta carpeta al repositorio. Importante: sube lo que está
   *dentro* de `tvplayer/`, de modo que `settings.gradle.kts` y la carpeta `.github`
   queden en la raíz del repositorio, no dentro de otra subcarpeta.

   Puedes arrastrar los archivos con el botón `Add file > Upload files` de la web.
   Asegúrate de incluir la carpeta oculta `.github` — si tu explorador no la muestra,
   activa "mostrar archivos ocultos".

   O por consola, desde la carpeta del proyecto:

   ```bash
   git init
   git add .
   git commit -m "Reproductor IPTV para Android TV"
   git branch -M main
   git remote add origin https://github.com/TU_USUARIO/TU_REPO.git
   git push -u origin main
   ```

4. Entra a la pestaña **Actions** del repositorio. Verás el flujo "Compilar APK"
   ejecutándose solo. Tarda unos 5-10 minutos la primera vez.
5. Cuando termine (marca verde), entra a esa ejecución y baja el archivo
   **MiReproductorTV-apk** de la sección *Artifacts*. Adentro está el `.apk`.

Si el flujo no arrancó solo, en Actions selecciona "Compilar APK" a la izquierda y usa
el botón **Run workflow**.

---

## Opción 2 — Android Studio (si vas a tocar el código)

1. Instala [Android Studio](https://developer.android.com/studio).
2. `File > Open` y elige la carpeta del proyecto.
3. Espera la sincronización de Gradle (baja dependencias la primera vez).
4. `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
5. El APK sale en `app/build/outputs/apk/debug/app-debug.apk`.

---

## Opción 3 — Línea de comandos con el SDK ya instalado

Si ya tienes el SDK de Android y la variable `ANDROID_HOME` configurada:

```bash
gradle assembleDebug
```

O para una versión firmada con tu propia llave (ver README.md):

```bash
gradle assembleRelease
```

---

## Instalar el APK en el TV Box

**Con ADB**, desde tu PC en la misma red:

```bash
adb connect IP_DEL_TVBOX:5555
adb install -r app-debug.apk
```

La IP la ves en el TV Box en `Ajustes > Red`. Necesitas tener activada la
*depuración por USB / ADB* en las opciones de desarrollador del TV Box (se activan
pulsando 7 veces sobre "Número de compilación" en `Ajustes > Información`).

**Sin ADB**: copia el APK a una memoria USB, conéctala al TV Box y ábrelo con un
explorador de archivos, autorizando "orígenes desconocidos" para esa app.

Una vez instalada, aparece en la fila de aplicaciones de Android TV con su propio banner.

---

## Sobre el APK de depuración

El que produce GitHub va firmado con la llave de depuración estándar de Android. Se
instala y funciona perfectamente para uso personal. La diferencia con uno de *release*
firmado por ti es que este último te permite publicar actualizaciones sobre la misma
app y va optimizado. Para instalarlo en tu TV Box y usarlo, el de depuración basta.

Si más adelante quieres el firmado, el README.md tiene los pasos para generar tu llave
con `keytool` y activar el bloque `signingConfigs`.
