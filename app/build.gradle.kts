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
        versionCode = 5
        versionName = "1.0.0-alpha3"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
}
