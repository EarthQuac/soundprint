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
        // The logo is converted from its clean source asset to a canonical RGBA
        // drawable before AAPT2 compiles resources. This avoids AAPT2's crash on
        // the original indexed PNG and never mutates the source file.
        getByName("main").res.srcDir(layout.buildDirectory.dir("generated/soundprintLogo/res"))

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Generate a canonical Android drawable from the source logo without touching
// the source asset. ImageIO decodes the repaired palette PNG and writes RGBA.
val generatedSoundprintLogoRes = layout.buildDirectory.dir("generated/soundprintLogo/res")
val generateSoundprintLogoResource = tasks.register("generateSoundprintLogoResource") {
    val sourceLogo = file("src/main/assets/soundprint_logo.png")
    inputs.file(sourceLogo)
    outputs.dir(generatedSoundprintLogoRes)
    doLast {
        if (!sourceLogo.exists()) throw GradleException("Missing Soundprint logo source asset")
        val decoded = javax.imageio.ImageIO.read(sourceLogo)
            ?: throw GradleException("Could not decode Soundprint logo source asset")
        val rgba = java.awt.image.BufferedImage(
            decoded.width,
            decoded.height,
            java.awt.image.BufferedImage.TYPE_INT_ARGB
        )
        val graphics = rgba.createGraphics()
        try {
            graphics.drawImage(decoded, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val output = generatedSoundprintLogoRes.get().file("drawable/soundprint_logo.png").asFile
        output.parentFile.mkdirs()
        if (!javax.imageio.ImageIO.write(rgba, "png", output)) {
            throw GradleException("Could not generate canonical RGBA Soundprint logo")
        }
        if (output.length() == 0L) throw GradleException("Generated Soundprint logo is empty")
    }
}

tasks.matching { it.name == "mergeDebugResources" || it.name == "mergeReleaseResources" }.configureEach {
    dependsOn(generateSoundprintLogoResource)
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


tasks.matching { it.name == "mergeDebugAssets" || (it.name.startsWith("assetPack") && it.name.endsWith("PreBundleTask")) || it.name == "bundleRelease" }.configureEach {
    dependsOn(rootProject.tasks.named("prepareClapModelAssets"))
}
