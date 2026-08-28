# Mi Reproductor TV

Reproductor IPTV para Android TV / TV Box. Carga listas M3U — las que trae precargadas
o la que tú le pongas — y las reproduce con navegación por control remoto.

Sin publicidad, sin telemetría, sin backend propio. La app no aloja ni redistribuye
contenido: solo reproduce la lista que elijas.

---

## Qué hace

- **Catálogos abiertos precargados**: Colombia, noticias, infantil, educativo,
  documentales, música, películas y todo el catálogo en español. Provienen del proyecto
  open-source [iptv-org](https://github.com/iptv-org/iptv) (licencia CC0), que recopila
  señales publicadas de forma abierta por sus propios emisores.
- **Tu propia lista**: pega cualquier URL M3U / M3U8.
- **Xtream Codes**: servidor + usuario + clave, se convierte solo a la URL M3U.
- **Listas recientes**: recuerda las últimas 8 que usaste.
- **Navegación TV**: panel lateral de categorías, grilla de canales con logos, buscador.
- **Favoritos**: mantén OK sobre un canal para marcarlo.
- **Zapping**: arriba/abajo cambia de canal sin salir del reproductor.
- **Formatos**: HLS (.m3u8), DASH (.mpd) y MPEG-TS, vía Media3/ExoPlayer.
- **Cabeceras por canal**: respeta `#EXTVLCOPT:http-user-agent` y `http-referrer`,
  que muchas listas necesitan para no dar 403.

## Permisos que pide

Solo tres: `INTERNET`, `ACCESS_NETWORK_STATE` y `WAKE_LOCK` (para que no se apague la
pantalla durante la reproducción). Nada de ubicación, teléfono, almacenamiento ni ID de
publicidad.

---

## Cómo compilar el APK

El proyecto no viene compilado: necesitas Android Studio una sola vez.

1. Instala [Android Studio](https://developer.android.com/studio).
2. `File > Open` y selecciona esta carpeta (`MiReproductorTV`).
3. Espera a que Gradle sincronice y descargue dependencias (primera vez, unos minutos).
4. **APK de prueba**: `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
   Sale en `app/build/outputs/apk/debug/app-debug.apk`.

### APK firmado (recomendado para el TV Box)

Genera tu propia llave — esta llave es tuya, nadie más puede publicar
actualizaciones de tu app:

```bash
keytool -genkey -v -keystore mi-tv.jks -alias mitv \
        -keyalg RSA -keysize 2048 -validity 10000
```

Guarda `mi-tv.jks` en la raíz del proyecto, descomenta el bloque `signingConfigs` en
`app/build.gradle.kts`, pon tu contraseña, descomenta también la línea
`signingConfig = ...` dentro de `release`, y compila:

```bash
./gradlew assembleRelease
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`.

> En Android Studio también puedes usar `Build > Generate Signed Bundle / APK`
> y te guía por el asistente sin tocar el archivo Gradle.

## Cómo instalarlo en el TV Box

**Opción A — ADB (la más limpia):**

```bash
adb connect IP_DEL_TVBOX:5555
adb install -r app-release.apk
```

**Opción B — sideload:** copia el APK a una USB, y en el TV Box usa un explorador de
archivos con "orígenes desconocidos" habilitado para esa app.

La app aparece en la fila de aplicaciones de Android TV (declara
`LEANBACK_LAUNCHER`, así que sale con su banner propio).

---

## Estructura del código

```
app/src/main/java/com/alvaro/tvplayer/
├── data/
│   ├── Channel.kt             Modelo: canal y lista
│   ├── M3uParser.kt           Parser M3U + conversor Xtream → M3U
│   ├── Catalogs.kt            Catálogos abiertos precargados
│   ├── PlaylistRepository.kt  Descarga con OkHttp
│   ├── PlaylistHolder.kt      Traspaso entre activities
│   └── Prefs.kt               Favoritos y recientes (local)
└── ui/
    ├── SourceActivity.kt      Pantalla de inicio: elegir/pegar lista
    ├── BrowseActivity.kt      Categorías + grilla de canales
    ├── PlayerActivity.kt      Reproducción y zapping
    └── Theme.kt               Colores
```

## Notas sobre las listas

Un `.m3u` es solo texto: una línea `#EXTINF` con los datos del canal y debajo su URL.
El parser tolera listas mal formadas (URLs sin `#EXTINF` se descartan, directivas
desconocidas como `#KODIPROP` se ignoran) y maneja correctamente nombres y atributos
que contienen comas.

Si un canal no carga, casi siempre es una de tres cosas: el emisor cambió la URL,
el stream exige un `User-Agent` concreto, o requiere DRM (esta app no reproduce
contenido con DRM propietario). Prueba con otro canal antes de dar la lista por mala.

Las URLs de los catálogos precargados apuntan a listas que se actualizan solas del lado
de iptv-org, así que no hace falta actualizar la app para que los canales sigan al día.
