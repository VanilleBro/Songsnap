plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------
// Spotify App Remote SDK: liegt nicht in Maven Central, sondern nur als .aar im
// GitHub-Repo von Spotify. Wird beim ersten Build automatisch heruntergeladen.
// ---------------------------------------------------------------------------
val appRemoteAar = file("libs/spotify-app-remote-release-0.8.0.aar")
if (!appRemoteAar.exists() || appRemoteAar.length() < 10_000) {
    appRemoteAar.parentFile.mkdirs()
    val sources = listOf(
        "https://raw.githubusercontent.com/spotify/android-sdk/master/app-remote-lib/spotify-app-remote-release-0.8.0.aar",
        "https://github.com/spotify/android-sdk/raw/master/app-remote-lib/spotify-app-remote-release-0.8.0.aar",
        "https://raw.githubusercontent.com/spotify/android-app-remote-sdk/master/app-remote-lib/spotify-app-remote-release-0.8.0.aar",
        "https://cdn.jsdelivr.net/gh/spotify/android-sdk@master/app-remote-lib/spotify-app-remote-release-0.8.0.aar",
    )
    var ok = false
    for (src in sources) {
        if (ok) break
        try {
            logger.lifecycle("Lade Spotify App Remote SDK: $src")
            val conn = uri(src).toURL().openConnection()
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            val bytes = conn.getInputStream().use { it.readBytes() }
            // .aar = ZIP-Datei -> muss mit "PK" beginnen
            if (bytes.size > 10_000 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                appRemoteAar.writeBytes(bytes)
                ok = true
            } else {
                logger.warn("  Antwort ist keine .aar-Datei")
            }
        } catch (e: Exception) {
            logger.warn("  fehlgeschlagen: ${e.message}")
        }
    }
    if (!ok) {
        throw GradleException(
            "Konnte spotify-app-remote-release-0.8.0.aar nicht herunterladen. " +
                "Lade sie manuell aus https://github.com/spotify/android-sdk (Ordner app-remote-lib) " +
                "und lege sie nach app/libs/."
        )
    }
}

android {
    namespace = "com.songsnap.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.songsnap.game"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }
        // Falls das Spotify-SDK Platzhalter im Manifest erwartet (absichtlich ein anderes Schema
        // als unser Login-Callback, damit es keine Doppel-Zuordnung gibt)
        manifestPlaceholders["redirectSchemeName"] = "songsnap-sdk"
        manifestPlaceholders["redirectHostName"] = "auth"
        manifestPlaceholders["redirectPathPattern"] = ".*"
    }

    // Fester Signaturschluessel -> der SHA1-Fingerprint bleibt bei jedem Build gleich
    // (Spotify verlangt ihn im Developer-Dashboard).
    signingConfigs {
        create("songsnap") {
            storeFile = file("songsnap.jks")
            storePassword = "songsnap"
            keyAlias = "songsnap"
            keyPassword = "songsnap"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("songsnap")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("songsnap")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation("com.google.code.gson:gson:2.11.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.browser:browser:1.8.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
