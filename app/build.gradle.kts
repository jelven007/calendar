plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseSigningVariables = listOf(
    "TIANDILI_KEYSTORE_PATH",
    "TIANDILI_KEY_ALIAS",
    "TIANDILI_STORE_PASSWORD",
    "TIANDILI_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it).orNull }
require(releaseSigningVariables.values.all { it.isNullOrBlank() } ||
    releaseSigningVariables.values.all { !it.isNullOrBlank() }) {
    "Release signing requires all four TIANDILI signing environment variables."
}
val hasReleaseSigning = releaseSigningVariables.values.all { !it.isNullOrBlank() }

android {
    namespace = "com.tiandili.calendar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tiandili.calendar"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("localRelease") {
                storeFile = file(releaseSigningVariables.getValue("TIANDILI_KEYSTORE_PATH")!!)
                keyAlias = releaseSigningVariables.getValue("TIANDILI_KEY_ALIAS")
                storePassword = releaseSigningVariables.getValue("TIANDILI_STORE_PASSWORD")
                keyPassword = releaseSigningVariables.getValue("TIANDILI_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("localRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Versions are pinned to the reviewed API 36 compatibility matrix.
        disable += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
            "ObsoleteSdkInt",
        )
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("debug").assets.srcDir("$projectDir/schemas")
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kizitonwose.calendar.compose)
    implementation(libs.lunar.java)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.moshi)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime)
    ksp(libs.androidx.room.compiler)
    ksp(libs.moshi.kotlin.codegen)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.glance.testing)
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Robolectric's native renderer loads process-wide libraries. Run its screenshots
// in a dedicated JVM so other SDK sandboxes cannot load the same font archive twice.
tasks.withType<Test>().configureEach {
    if (name == "testDebugUnitTest") {
        exclude("**/WidgetRenderingTest*")
        exclude("**/AlarmSoundServiceTest*")
        exclude("**/UiRenderingTest*")
        exclude("**/AppStartupCompatibilityTest*")
    }
}

tasks.register<Test>("testUiRendering") {
    group = "verification"
    description = "Render and verify native Compose pages in an isolated JVM."
    val unitTests = tasks.named<Test>("testDebugUnitTest").get()
    testClassesDirs = unitTests.testClassesDirs
    classpath = unitTests.classpath
    systemProperties(unitTests.systemProperties)
    jvmArgs(unitTests.jvmArgs.orEmpty())
    include("**/UiRenderingTest*")
    dependsOn("compileDebugUnitTestKotlin", "generateDebugUnitTestConfig")
}

listOf(26, 35).forEach { sdk ->
    tasks.register<Test>("testStartupApi$sdk") {
        group = "verification"
        description = "Verify app startup on Android API $sdk in an isolated JVM."
        val unitTests = tasks.named<Test>("testDebugUnitTest").get()
        testClassesDirs = unitTests.testClassesDirs
        classpath = unitTests.classpath
        systemProperties(unitTests.systemProperties)
        systemProperty("robolectric.enabledSdks", sdk.toString())
        jvmArgs(unitTests.jvmArgs.orEmpty())
        include("**/AppStartupCompatibilityTest*")
        dependsOn("compileDebugUnitTestKotlin", "generateDebugUnitTestConfig")
    }
}

tasks.register<Test>("testWidgetRendering") {
    group = "verification"
    description = "Render and verify native widget layouts in an isolated JVM."
    val unitTests = tasks.named<Test>("testDebugUnitTest").get()
    testClassesDirs = unitTests.testClassesDirs
    classpath = unitTests.classpath
    systemProperties(unitTests.systemProperties)
    jvmArgs(unitTests.jvmArgs.orEmpty())
    include("**/WidgetRenderingTest*")
    dependsOn("compileDebugUnitTestKotlin", "generateDebugUnitTestConfig")
}

listOf(26, 35).forEach { sdk ->
    tasks.register<Test>("testAlarmApi$sdk") {
        group = "verification"
        description = "Verify alarm service lifecycle on Android API $sdk in an isolated JVM."
        val unitTests = tasks.named<Test>("testDebugUnitTest").get()
        testClassesDirs = unitTests.testClassesDirs
        classpath = unitTests.classpath
        systemProperties(unitTests.systemProperties)
        systemProperty("robolectric.enabledSdks", sdk.toString())
        jvmArgs(unitTests.jvmArgs.orEmpty())
        include("**/AlarmSoundServiceTest*")
        dependsOn("compileDebugUnitTestKotlin", "generateDebugUnitTestConfig")
    }
}
