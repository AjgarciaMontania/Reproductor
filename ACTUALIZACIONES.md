# Actualizaciones automáticas

Cómo funciona el sistema, y qué tienes que configurar una sola vez.

---

## La llave de firma: por qué importa

Android identifica una app por dos cosas: su `applicationId` y **la llave con que está
firmada**. Para actualizar una app instalada, el APK nuevo debe venir firmado con la
**misma llave**. Si cambia, el sistema rechaza la instalación con *"aplicación no
instalada"* o *"conflicto de paquete"*, y la única salida es desinstalar — perdiendo
favoritos y listas guardadas.

Por eso la llave se genera **una vez** y se guarda para siempre. La tuya ya está creada:

- Archivo: `mi-tv.jks`
- Alias: `mitv`
- Algoritmo: RSA 4096 bits
- Válida hasta: julio de 2059

**Guárdala en un lugar seguro y con copia de respaldo.** Si la pierdes, no podrás volver
a publicar actualizaciones de esta app nunca más: tendrías que empezar con otro
`applicationId` y reinstalar desde cero en el TV Box.

**Nunca la subas al repositorio.** Ya está en `.gitignore` (`*.jks`), pero conviene que
lo sepas: quien tenga esa llave puede firmar un APK que tu TV Box aceptaría como
actualización legítima.

---

## Configuración inicial (una sola vez)

### 1. Crear los secrets en GitHub

Ve a tu repositorio → **Settings** → **Secrets and variables** → **Actions** →
botón **New repository secret**. Crea estos cuatro:

| Nombre | Valor |
|---|---|
| `KEYSTORE_BASE64` | El contenido de `keystore_base64.txt` (una sola línea larga) |
| `KEYSTORE_PASSWORD` | La contraseña del keystore |
| `KEY_ALIAS` | `mitv` |
| `KEY_PASSWORD` | La misma contraseña del keystore |

Los secrets quedan cifrados: ni siquiera tú puedes volver a leerlos desde la web, y no
aparecen en los logs de las compilaciones.

### 2. Compilar en local (opcional)

Si quieres compilar firmado desde tu PC, crea un archivo `keystore.properties` en la
raíz del proyecto:

```properties
storeFile=mi-tv.jks
storePassword=TU_PASSWORD
keyAlias=mitv
keyPassword=TU_PASSWORD
```

También está en `.gitignore`. Con eso, `gradle assembleRelease` produce el APK firmado.

---

## Publicar una versión nueva

Cada vez que quieras que el TV Box reciba cambios:

**Opción A — desde la web:** pestaña **Actions** → flujo **Publicar versión** →
botón **Run workflow**. Escribe el número de versión (`1.1`) y las notas del cambio.

**Opción B — con una etiqueta:**

```bash
git tag v1.1
git push origin v1.1
```

En ambos casos GitHub compila el APK firmado, verifica la firma y publica un *Release*
con dos archivos: el `.apk` y un `latest.json`.

### El versionCode

Es el número interno que Android compara para decidir si algo es "más nuevo". El
workflow lo toma del número de ejecución de Actions, así que **sube solo en cada
publicación** y nunca se repite. No tienes que tocarlo.

---

## Cómo se entera la app

Al abrirse, consulta esta URL — que GitHub mantiene siempre apuntando al último release:

```
https://github.com/AjgarciaMontania/Reproductor/releases/latest/download/latest.json
```

Si el `versionCode` de ahí es mayor que el instalado, muestra un aviso arriba con las
notas de la versión y dos botones: **Actualizar ahora** y **Ahora no**. Al aceptar,
descarga el APK mostrando el progreso y lanza la instalación.

La primera vez, Android pedirá que autorices a la app a instalar aplicaciones
(`Ajustes > Aplicaciones > Acceso especial`). La app te lleva sola a esa pantalla.
Es un permiso que se concede una vez.

---

## Sobre la seguridad de esto

Este es el mismo mecanismo que señalé como el mayor riesgo en el análisis de
OriginalPlayer, así que conviene ser explícito sobre en qué se diferencia:

- **La llave es tuya.** Nadie más puede publicar algo que tu TV Box acepte como
  actualización. Android verifica la firma antes de instalar y rechaza cualquier APK
  firmado con otra llave. En la app pirata, la llave era de un tercero anónimo.
- **El origen es tu repositorio**, por HTTPS, y la app solo acepta URLs de descarga de
  GitHub — está comprobado en el código antes de bajar nada.
- **Tú controlas qué se publica.** Ninguna actualización sale sin que tú lances el
  workflow.
- **Siempre se confirma en pantalla.** El sistema muestra el diálogo de instalación; la
  app no puede instalar en segundo plano.
- **El código es visible.** Puedes leer `UpdateChecker.kt` y `UpdateInstaller.kt`
  completos, que son unas 200 líneas entre los dos.

La diferencia no es el mecanismo, es quién tiene la llave y quién decide qué se publica.

Si prefieres no tener auto-actualización, borra el permiso
`REQUEST_INSTALL_PACKAGES` del manifest y el bloque `update?.let { ... }` de
`SourceActivity.kt`; el resto de la app funciona igual y actualizas a mano por ADB.

---

## Si quieres generar tu propia llave

La actual la generé yo en esta sesión. Si prefieres una creada por ti y que no haya
pasado por ningún otro sitio, es legítimo — hazlo **antes** de instalar la app en el TV
Box, porque cambiar de llave después obliga a desinstalar:

```bash
keytool -genkeypair -v -keystore mi-tv.jks -alias mitv \
        -keyalg RSA -keysize 4096 -validity 12000
```

Luego actualiza los cuatro secrets de GitHub con los datos nuevos. Para pasar el archivo
a base64:

```bash
# Linux / macOS
base64 -w0 mi-tv.jks > keystore_base64.txt

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("mi-tv.jks")) | Out-File keystore_base64.txt -NoNewline
```
