plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    // Add the Google services Gradle plugin
    id("com.google.gms.google-services")

}

android {
    namespace = "com.shunlight_library.novel_reader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shunlight_library.novel_reader"
        minSdk = 21
        targetSdk = 34
        versionCode = 170
        versionName = "1.6.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // 開発環境: バージョン名に@δ を追加
            versionNameSuffix = "@δ"
            // ログ出力を許可
            buildConfigField("boolean", "ENABLE_LOGGING", "true")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // リリース環境: ログ出力を無効化
            buildConfigField("boolean", "ENABLE_LOGGING", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = rootProject.extra["kotlinJvmTarget"] as String
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
//    buildToolsVersion = "35.0.0"
}

dependencies {
    implementation(libs.androidx.work.runtime.ktx)
    val room_version = "2.7.0"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

// Jsoup for HTML parsing
    implementation("org.jsoup:jsoup:1.18.3")

// Markdown rendering for Compose
    implementation("com.github.jeziellago:compose-markdown:0.5.8")

// DataStore for preferences
    implementation ("androidx.compose.material:material-icons-extended:1.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.yaml:snakeyaml:2.3")
// DocumentFile for content provider access
    implementation("androidx.documentfile:documentfile:1.1.0")

// Kotlin Coroutines (Android 5.0+ compatible)
    val coroutines_version = "1.10.2"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:$coroutines_version")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines_version")
    testImplementation("com.google.truth:truth:1.1.5")

    // Android Instrumented Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation("androidx.room:room-testing:$room_version")
    androidTestImplementation("androidx.work:work-testing:2.10.1")
    androidTestImplementation("com.google.truth:truth:1.1.5")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines_version")

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("androidx.compose.ui:ui-text-google-fonts:1.7.0")
    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))


    // TODO: Add the dependencies for Firebase products you want to use
    // When using the BoM, don't specify versions in Firebase dependencies
    implementation("com.google.firebase:firebase-analytics")


    // Add the dependencies for any other desired Firebase products
    // https://firebase.google.com/docs/android/setup#available-libraries

}
