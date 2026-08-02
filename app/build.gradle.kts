plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.piyak.english"
    compileSdk = 36

    defaultConfig {
        // 삐약수학. namespace(소스 패키지)는 com.piyak.english 그대로 두고
        // applicationId 만 바꾼다 — 이걸로 삐약영어와 데이터·설치가 완전히 분리된다.
        applicationId = "com.piyak.math"
        minSdk = 26
        targetSdk = 36
        versionCode = 26
        versionName = "1.25"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
