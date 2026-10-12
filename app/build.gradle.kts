import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseSigningFile = providers.environmentVariable("CLARID_SIGNING_PROPERTIES")
    .map { file(it) }
    .getOrElse(file("${System.getProperty("user.home")}/.config/clarid-pocket/signing.properties"))
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}
val requireReleaseSigning = tasks.register("requireReleaseSigning") {
    doLast {
        check(listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all {
            !releaseSigning.getProperty(it).isNullOrBlank()
        }) { "Release signing is missing. See DEVELOPMENT.md for CLARID_SIGNING_PROPERTIES." }
        check(file(releaseSigning.getProperty("storeFile")).isFile) { "Release keystore is missing." }
    }
}

android {
    namespace = "org.cnag.clarid.pocket"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.cnag.clarid.pocket"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.1.9"
    }
    signingConfigs {
        create("release") {
            releaseSigning.getProperty("storeFile")?.let { storeFile = file(it) }
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") { signingConfig = signingConfigs.getByName("release") }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core"))
    implementation("com.google.code.gson:gson:2.13.2")
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")
    implementation("androidx.camera:camera-mlkit-vision:1.5.3")
    // Bundled model: scanning does not need a first-use download.
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    testImplementation("junit:junit:4.13.2")
}

// Never produce an unsigned release when the local signing setup is absent.
tasks.matching { it.name == "validateSigningRelease" || it.name == "packageRelease" }.configureEach {
    dependsOn(requireReleaseSigning)
}
