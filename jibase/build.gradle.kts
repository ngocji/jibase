import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.hilt.gradle)
    id("maven-publish")
    kotlin("kapt")
}

val buildDate: String by lazy {
    SimpleDateFormat("ddMMyy").format(Date())
}

android {
    namespace = "com.jibase"
    compileSdk = 35
    
    defaultConfig {
        targetSdk = 35
        minSdk = 21
        buildConfigField("String", "VERSION", "\"${buildDate}\"")
        consumerProguardFiles("proguard-rules.pro")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    libraryVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.LibraryVariantOutputImpl
            output.outputFileName = "jibase-$buildDate.aar"
        }
    }
}

dependencies {
    implementation(libs.bundles.androidX)
    implementation(libs.bundles.coroutine)
    implementation(libs.bundles.hilt)
    kapt(libs.bundles.hilt.compiler)
    implementation(libs.bundles.common)
    kapt(libs.bundles.common.compiler)
    implementation(libs.androidx.paging.runtime)
}

publishing {
    publications {
        create<MavenPublication>("jibase-di") {
            groupId = "com.ngocji"
            artifactId = "jibase"
            version = "4.3.3"
        }
    }
}