plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.studio64.android"
    compileSdk = 34
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.studio64.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 247
        versionName = "0.2.4-beta.7"

        // Solo arm64-v8a (libnode.so de nodejs-mobile para esa ABI)
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                // libnode.so depende de libc++_shared.so
                arguments += "-DANDROID_STL=c++_shared"
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets.getByName("main") {
        // libnode.so (nodejs-mobile) se empaqueta como libreria nativa
        jniLibs.srcDirs("libnode/bin")
        // studio64-app.zip generado por la tarea packStudio64App
        assets.srcDir(layout.buildDirectory.dir("generated/studio64/assets"))
    }

    packaging {
        jniLibs {
            // APK mas pequeno (las libs van comprimidas y se extraen al instalar)
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Firmado con la clave de depuracion para que sea instalable directamente
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint { checkReleaseBuilds = false; abortOnError = false }
}

// Empaqueta el servidor de Studio 64 (JS + node_modules) en un unico asset.
val packApp = tasks.register<Zip>("packStudio64App") {
    from(rootProject.file("studio64-app"))
    archiveFileName.set("studio64-app.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated/studio64/assets"))
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) dependsOn(packApp)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
}
