import java.io.File
import java.net.URL
import java.security.MessageDigest

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.asset-pack") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
}

val prepareClapModelAssets by tasks.registering {
    val assetsDirectory = layout.projectDirectory.dir("clapmodels/src/main/assets")
    outputs.dir(assetsDirectory)

    doLast {
        val root = assetsDirectory.asFile
        val baseUrl = "https://huggingface.co/Xenova/larger_clap_music_and_speech/resolve/56bcfdb/"
        val assets = listOf(
            Triple("clap/onnx/audio_model_quantized.onnx", "021dd4fb962b4ed20cc3a6730b09e0ccf9f9c49931047032118a3b64828513a6", 78_155_433L),
            Triple("clap/onnx/text_model_quantized.onnx", "8f9f29c5f6adee917553d4b3a70729c731c0d18b88efca0ae67c1a1fc278f3b6", 126_000_000L),
            Triple("clap/vocab.json", "", 1L),
            Triple("clap/merges.txt", "", 1L)
        )

        fun digest(file: File): String {
            val sha = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(1024 * 1024).use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    sha.update(buffer, 0, count)
                }
            }
            return sha.digest().joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
        }

        assets.forEach { (relativePath, expectedSha, minimumBytes) ->
            val destination = File(root, relativePath)
            val valid = destination.isFile && destination.length() >= minimumBytes &&
                (expectedSha.isEmpty() || digest(destination) == expectedSha)
            if (!valid) {
                destination.parentFile.mkdirs()
                val temporary = File(destination.parentFile, destination.name + ".part")
                temporary.delete()
                println("Preparing Play-delivered CLAP asset: $relativePath")
                val connection = URL(baseUrl + relativePath.removePrefix("clap/")).openConnection().apply {
                    connectTimeout = 20_000
                    readTimeout = 180_000
                    setRequestProperty("User-Agent", "Soundprint-Android-Build")
                }
                connection.getInputStream().use { input ->
                    temporary.outputStream().buffered(1024 * 1024).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
                check(temporary.length() >= minimumBytes) {
                    "CLAP asset is incomplete: $relativePath"
                }
                if (expectedSha.isNotEmpty()) {
                    check(digest(temporary) == expectedSha) {
                        "CLAP asset SHA-256 mismatch: $relativePath"
                    }
                }
                check(temporary.renameTo(destination)) {
                    "Could not place CLAP asset: $relativePath"
                }
            }
        }

        File(root, "clap/MODEL-NOTICE.txt").apply {
            parentFile.mkdirs()
            writeText(
                "Soundprint includes the quantized ONNX export of LAION's larger_clap_music_and_speech model. " +
                    "The upstream model is licensed under the Apache License, Version 2.0. " +
                    "Upstream model card: https://huggingface.co/laion/larger_clap_music_and_speech " +
                    "ONNX export: https://huggingface.co/Xenova/larger_clap_music_and_speech " +
                    "Pinned export revision: 56bcfdb. Soundprint uses CLAP for on-device zero-shot audio mood and genre ranking. " +
                    "Inference is performed locally on this device."
            )
        }
        // Apache 2.0 section 4(a) requires recipients to receive a copy of the license.
        File(root, "clap/LICENSE.txt").writeText(
            URL("https://www.apache.org/licenses/LICENSE-2.0.txt").readText()
        )
    }
}

