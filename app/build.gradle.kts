import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// ── Version ──────────────────────────────────────────────────────────────────
// Single source of truth is `appVersionName` in gradle.properties (scripts/release.sh bumps it).
// versionCode is derived from it (1.2.3 -> 10203), so every release is automatically higher
// than the previous one — Android refuses updates whose versionCode does not increase.
val appVersionName: String = providers.gradleProperty("appVersionName").get()
val appVersionCode: Int = run {
    val parts = appVersionName.split(".").map { it.toIntOrNull() }
    require(parts.size == 3 && parts.all { it != null && it in 0..99 }) {
        "appVersionName must look like MAJOR.MINOR.PATCH with each part 0..99, was '$appVersionName'"
    }
    val (major, minor, patch) = parts.map { it!! }
    major * 10000 + minor * 100 + patch
}

// ── Release signing ──────────────────────────────────────────────────────────
// Every release MUST be signed with the same key, otherwise Android rejects the update
// and the app would have to be uninstalled (= data loss).
// Local builds read keystore.properties (gitignored), CI reads environment variables.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(property: String, env: String): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(property)

android {
    namespace = "io.github.veitkramerschoeggl.trainingtracker"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Never change after the first release: a different applicationId is a different app
        // and would not receive the existing data.
        applicationId = "io.github.veitkramerschoeggl.trainingtracker"
        minSdk = 34
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            signingValue("storeFile", "RELEASE_STORE_FILE")?.let { path ->
                storeFile = rootProject.file(path)
                storePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Separate app next to the real one, so a debug install can never overwrite
            // (or force uninstalling) the release app and its data.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Pull-ups Debug"
            // Debug builds only look for updates when a test source is passed explicitly, e.g.
            // ./gradlew installDebug -PdebugUpdateManifestUrl=http://10.0.2.2:8765/update.json
            val debugUpdateUrl = providers.gradleProperty("debugUpdateManifestUrl").orElse("").get()
            buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$debugUpdateUrl\"")
        }
        release {
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            manifestPlaceholders["appLabel"] = "Pull-ups"
            val updateUrl = providers.gradleProperty("updateManifestUrl").get()
            buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateUrl\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

room {
    // Schema JSON per DB version, committed to git — needed to write and test migrations.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    // kotlinx-serialization comes in via androidx.savedstate (1.7.3); room-testing needs 1.8.1 and
    // instrumented tests must use the app's versions — so align it here.
    implementation(platform(libs.kotlinx.serialization.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
