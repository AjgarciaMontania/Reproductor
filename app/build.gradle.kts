import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/*
 * FIRMA
 * -----
 * La llave es lo que permite ACTUALIZAR la app sin desinstalarla: Android exige
 * que la version nueva este firmada con la misma llave que la instalada. Si cambia,
 * la instalacion falla con "aplicacion no instalada".
 *
 * Se lee de dos sitios, en este orden:
 *   1. keystore.properties en la raiz del proyecto  (compilacion local)
 *   2. variables de entorno                          (GitHub Actions)
 *
 * NUNCA subas el .jks ni keystore.properties al repositorio: estan en .gitignore.
 */
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey) ?: System.getenv(envKey)

val ksFile = signingValue("storeFile", "KEYSTORE_FILE")
val ksPassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
val ksAlias = signingValue("keyAlias", "KEY_ALIAS")
val ksKeyPassword = signingValue("keyPassword", "KEY_PASSWORD")
val hasSigning = !ksFile.isNullOrBlank() && !ksPassword.isNullOrBlank() &&
                 !ksAlias.isNullOrBlank() && !ksKeyPassword.isNullOrBlank()

// El versionCode debe SUBIR en cada release o el dispositivo rechaza la actualizacion.
// En CI lo inyecta el workflow con el numero de ejecucion; en local queda en 1.
val buildVersionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
val buildVersionName = System.getenv("VERSION_NAME") ?: "1.0"

android {
    namespace = "com.alvaro.tvplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alvaro.tvplayer"
        minSdk = 23
        targetSdk = 35
        versionCode = buildVersionCode
        versionName = buildVersionName

        // Datos del repositorio de donde se bajan las actualizaciones.
        buildConfigField("String", "UPDATE_OWNER", "\"AjgarciaMontania\"")
        buildConfigField("String", "UPDATE_REPO", "\"Reproductor\"")
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(ksFile!!)
                storePassword = ksPassword
                keyAlias = ksAlias
                keyPassword = ksKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Compose para TV (foco y navegacion por D-pad)
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")

    // Reproduccion: HLS, DASH, MPEG-TS progresivo
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
