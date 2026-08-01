import com.android.build.api.dsl.LibraryExtension

plugins {
    id("ankidroid.android.library")
}

configure<LibraryExtension> {
    namespace = "dev.androidbroadcast.vbpd"

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
}
