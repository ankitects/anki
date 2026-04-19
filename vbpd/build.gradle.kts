import com.android.build.api.dsl.LibraryExtension

plugins {
    id("ankidroid.android.library")
}

configure<LibraryExtension> {
    namespace = "dev.androidbroadcast.vbpd"

    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
}
