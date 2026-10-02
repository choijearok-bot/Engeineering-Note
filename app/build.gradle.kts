plugins {
    id("com.android.application")
}

android {
    namespace = "com.kcc.engineeringnote"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kcc.engineeringnote"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
}
