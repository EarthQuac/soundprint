plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "digital.anchorstudio.soundprint"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.anchorstudios.soundprint"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.1.5"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    androidResources {
        noCompress += "onnx"
    }

    assetPacks += listOf(":clapmodels")

    sourceSets {
        // Standalone debug APKs (Appetize/device testing) include a local fallback.
        // Play releases receive these same files only through the on-demand asset pack.
        getByName("debug").assets.srcDir(rootProject.file("clapmodels/src/main/assets"))
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)

    implementation(platform("io.github.jan-tennert.supabase:bom:3.2.3"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-android:3.2.3")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    // Fallback Google account flow when the device has no Credential Manager provider.
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    androidTestImplementation(composeBom)

    implementation("androidx.core:core:1.17.0") // NotificationCompat progress notifications
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    implementation("com.google.android.play:asset-delivery:2.3.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Single-source Lucide icon pack for every app UI icon.
    implementation("com.composables:icons-lucide-android:2.2.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}


// Remove malformed ancillary PNG metadata and repair chunk CRCs before AAPT2
// parses the app logo. The pixel data (IHDR/PLTE/IDAT) is retained byte-for-byte.
val normalizeSoundprintLogoPng = tasks.register("normalizeSoundprintLogoPng") {
    doLast {
        val logo = file("src/main/res/drawable/soundprint_logo.png")
        if (!logo.exists()) return@doLast

        val input = logo.readBytes()
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        if (input.size < signature.size || !input.copyOfRange(0, signature.size).contentEquals(signature)) {
            throw GradleException("soundprint_logo.png has an invalid PNG signature")
        }

        val output = java.io.ByteArrayOutputStream(input.size)
        output.write(signature)
        var offset = signature.size
        var sawHeader = false
        var sawImageData = false
        var sawEnd = false

        while (offset < input.size) {
            if (input.size - offset < 12) throw GradleException("Truncated PNG chunk in soundprint_logo.png")
            val chunkLength = java.nio.ByteBuffer.wrap(input, offset, 4).int.toLong() and 0xffffffffL
            if (chunkLength > Int.MAX_VALUE || chunkLength > input.size - offset - 12L) {
                throw GradleException("Invalid PNG chunk length in soundprint_logo.png")
            }

            val length = chunkLength.toInt()
            val typeStart = offset + 4
            val dataStart = typeStart + 4
            val dataEnd = dataStart + length
            val typeBytes = input.copyOfRange(typeStart, dataStart)
            val type = String(typeBytes, Charsets.US_ASCII)
            val data = input.copyOfRange(dataStart, dataEnd)
            val isCritical = type[0] in 'A'..'Z'

            // Ancillary chunks carry metadata (profiles, timestamps, text) and are
            // intentionally discarded; critical image chunks preserve the artwork.
            if (isCritical) {
                if (type == "IHDR") {
                    if (sawHeader || length != 13) throw GradleException("Invalid PNG IHDR")
                    sawHeader = true
                }
                if (type == "IDAT") sawImageData = true
                if (type == "IEND") {
                    if (length != 0) throw GradleException("Invalid PNG IEND")
                    sawEnd = true
                }

                val crc = java.util.zip.CRC32().apply {
                    update(typeBytes)
                    update(data)
                }.value
                output.write(byteArrayOf(
                    (length ushr 24).toByte(), (length ushr 16).toByte(),
                    (length ushr 8).toByte(), length.toByte()
                ))
                output.write(typeBytes)
                output.write(data)
                output.write(byteArrayOf(
                    (crc ushr 24).toByte(), (crc ushr 16).toByte(),
                    (crc ushr 8).toByte(), crc.toByte()
                ))
            }

            offset = dataEnd + 4 // skip original CRC; recompute it for retained chunks
            if (sawEnd) break
        }

        if (!sawHeader || !sawImageData || !sawEnd) {
            throw GradleException("soundprint_logo.png is missing required PNG chunks")
        }

        val normalized = output.toByteArray()
        val temporary = logo.resolveSibling("soundprint_logo.normalized.png")
        temporary.writeBytes(normalized)
        if (temporary.length() == 0L) throw GradleException("Normalized Soundprint logo is empty")
        if (!temporary.renameTo(logo)) {
            temporary.copyTo(logo, overwrite = true)
            temporary.delete()
        }
    }
}

tasks.matching { it.name == "mergeDebugResources" || it.name == "mergeReleaseResources" }.configureEach {
    dependsOn(normalizeSoundprintLogoPng)
}

tasks.matching { it.name == "mergeDebugAssets" || (it.name.startsWith("assetPack") && it.name.endsWith("PreBundleTask")) || it.name == "bundleRelease" }.configureEach {
    dependsOn(rootProject.tasks.named("prepareClapModelAssets"))
}
