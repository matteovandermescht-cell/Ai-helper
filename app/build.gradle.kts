plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.matteo.aihelper"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.matteo.aihelper"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
