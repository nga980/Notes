plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.notes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.notes"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // ✅ Room (Kotlin DSL style)
    implementation("androidx.room:room-runtime:2.2.5")
    annotationProcessor("androidx.room:room-compiler:2.2.5") // Dùng kapt thay vì annotationProcessor trong Kotlin

    // ✅ RecyclerView (sửa chính tả androidx)
    implementation("androidx.recyclerview:recyclerview:1.1.0")

    // ✅ Scalable Size Unit (support for different screen sizes)
    // ✅ Sửa đúng như sau:
    implementation("com.intuit.sdp:sdp-android:1.0.6")
    implementation("com.intuit.ssp:ssp-android:1.0.6")

    // Material Design (bạn đã có bản mới hơn phía trên, nên xoá dòng cũ này nếu không cần)
    // implementation("com.google.android.material:material:1.1.0")

    // ✅ Rounded ImageView
    implementation("com.makeramen:roundedimageview:2.3.0")
}
