plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
    `maven-publish`
}

group = "com.github.kvnpetit"
version = providers.gradleProperty("version")
    .orElse(providers.environmentVariable("VERSION"))
    .orElse("2.0.0-SNAPSHOT").get()

dokka {
    pluginsConfiguration {
        versioning {
            version.set(providers.gradleProperty("dokkaVersion").orElse("dev"))
            providers.gradleProperty("dokkaOlderVersionsDir").orNull?.let {
                olderVersionsDir.set(file(it))
            }
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.kvnpetit"
                artifactId = "better-french-tts"
                version = project.version.toString()
                pom {
                    name.set("Better French TTS")
                    description.set("Android text-to-speech library optimized for French")
                    url.set("https://github.com/kvnpetit/better-french-tts")
                }
            }
        }
    }
}

android {
    namespace = "io.github.kvnpetit.betterfrenchtts"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
