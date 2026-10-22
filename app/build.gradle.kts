import java.security.MessageDigest

plugins { id("com.android.application") }

val generatedEngine = layout.buildDirectory.dir("generated/engine")
val buildEngine = tasks.register<Exec>("buildEngine") {
    workingDir(rootProject.file("engine"))
    commandLine(providers.environmentVariable("SILPH_NODE").getOrElse("node"), "build.cjs")
    inputs.files(fileTree("../engine/src"), fileTree("../engine/location"),
        rootProject.file("engine/build.cjs"), rootProject.file("engine/package-lock.json"))
    outputs.dir(generatedEngine)
}

fun sha256(f: java.io.File): String {
    val md = MessageDigest.getInstance("SHA-256")
    f.inputStream().use { s ->
        val buf = ByteArray(32768); var n = s.read(buf)
        while (n != -1) { md.update(buf, 0, n); n = s.read(buf) }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

val ndkVer = "27.2.12479018"
val jniDir = file("src/main/jniLibs/arm64-v8a")
val sdkRoot = System.getenv("LOCALAPPDATA")?.let { "$it/Android/Sdk" }
    ?: "${System.getenv("USERPROFILE")}/AppData/Local/Android/Sdk"
val clangPath = "$sdkRoot/ndk/$ndkVer/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"

val buildLoader = tasks.register("buildLoader") {
    val nativeDir = rootProject.file("native")
    val outFile   = file("$jniDir/libsilph-loader.so")
    inputs.files(file("$nativeDir/loader-adapter.c"), file("$nativeDir/loader-policy.c"))
    outputs.file(outFile)
    doLast {
        val clang = file(clangPath)
        check(clang.exists()) { "NDK clang not found at $clangPath" }
        jniDir.mkdirs()
        val result = ProcessBuilder(
            clang.absolutePath,
            "--target=aarch64-linux-android29", "-shared", "-fPIC", "-O2", "-s",
            "-Wall", "-Wextra", "-Werror", "-fvisibility=hidden",
            "-fstack-protector-strong", "-D_FORTIFY_SOURCE=2",
            "-Wl,-z,relro,-z,now,-z,max-page-size=16384",
            "-Wl,--no-undefined", "-Wl,-soname,libsilph-loader.so",
            file("$nativeDir/loader-adapter.c").absolutePath,
            file("$nativeDir/loader-policy.c").absolutePath,
            "-ldl", "-o", outFile.absolutePath
        ).inheritIO().start().waitFor()
        check(result == 0) { "clang exited with $result" }
        logger.lifecycle("libsilph-loader.so built: ${outFile.length()} bytes")
    }
}

val checkNative = tasks.register("checkNativeRuntime") {
    val exactChecks = mapOf(
        "librootcatch-inject.so" to "cec700fb7bb3bac270c2f5752ce735b6007338710aa8cb7e997af328d9e311a3",
        "libsilph-spawn.so"      to "cf1a8058349cf5721b2e5162ddb23a59827c5090c3e4249a1caba220a847e5ee"
    )
    dependsOn(buildLoader)
    inputs.files(exactChecks.keys.map { file("$jniDir/$it") } + listOf(file("$jniDir/libsilph-loader.so")))
    doLast {
        exactChecks.forEach { (name, expected) ->
            val f = file("$jniDir/$name")
            check(f.isFile) { "Missing $name — run: python tools/prepare_native.py" }
            val actual = sha256(f)
            check(actual == expected) { "$name integrity mismatch (got $actual)" }
        }
        val loader = file("$jniDir/libsilph-loader.so")
        check(loader.isFile) { "libsilph-loader.so missing" }
        val hdr = loader.inputStream().use { it.readNBytes(4) }
        check(hdr.contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) { "libsilph-loader.so is not a valid ELF" }
        logger.lifecycle("Native runtime OK — loader ${loader.length()} bytes")
    }
}

// Auto-sync LOADER_SHA256 in ConnectionStrategy.java after every loader recompile
val syncLoaderHash = tasks.register("syncLoaderHash") {
    dependsOn(buildLoader)
    val loaderFile = file("$jniDir/libsilph-loader.so")
    val strategyFile = file("src/main/java/dev/rootcatch/nativeengine/ConnectionStrategy.java")
    inputs.file(loaderFile)
    outputs.file(strategyFile)
    doLast {
        val hash = sha256(loaderFile)
        val content = strategyFile.readText()
        val updated = content.replace(
            Regex("LOADER_SHA256 = \"[0-9a-f]{64}\""),
            "LOADER_SHA256 = \"$hash\""
        )
        if (updated != content) {
            strategyFile.writeText(updated)
            logger.lifecycle("LOADER_SHA256 updated to $hash")
        } else {
            logger.lifecycle("LOADER_SHA256 already current")
        }
    }
}

val releaseStore = providers.environmentVariable("SILPH_KEYSTORE").orNull
val debugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
android {
    namespace = "dev.rootcatch"
    compileSdk = 36
    ndkVersion = ndkVer
    defaultConfig {
        applicationId = "com.android.process.monitor"
        minSdk = 29
        targetSdk = 36
        versionCode = 62
        versionName = "1.9.3"
    }
    buildFeatures { buildConfig = true }
    sourceSets.getByName("main") {
        java.directories.add(generatedEngine.get().dir("java").asFile.absolutePath)
        assets.directories.add(generatedEngine.get().dir("assets").asFile.absolutePath)
    }
    if (releaseStore != null) {
        signingConfigs.create("publication") {
            storeFile = rootProject.file(releaseStore)
            storePassword = providers.environmentVariable("SILPH_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("SILPH_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("SILPH_KEY_PASSWORD").get()
        }
    } else if (debugKeystore.exists()) {
        signingConfigs.create("publication") {
            storeFile = debugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isDebuggable = false
            isCrunchPngs = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStore != null || debugKeystore.exists()) signingConfig = signingConfigs.getByName("publication")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { jniLibs { useLegacyPackaging = true; keepDebugSymbols += setOf("**/librootcatch-inject.so", "**/libsilph-spawn.so", "**/libsilph-loader.so") } }
}
tasks.named("preBuild").configure { dependsOn(buildEngine, checkNative, syncLoaderHash) }
