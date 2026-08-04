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
        versionCode = 54
        versionName = "1.55"
    }

    buildTypes {
        release {
            // 코드·리소스를 줄인다. **이름으로 찾아 쓰는** 리소스(tpl_*, word_*)는
            // res/raw/keep.xml 이 지켜 준다 — 없으면 릴리스에서만 조용히 사라져서
            // 디버그로는 절대 재현 안 되는 사고가 난다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // TODO: 정식 키스토어가 생기면 signingConfigs.release 로 바꾼다
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
