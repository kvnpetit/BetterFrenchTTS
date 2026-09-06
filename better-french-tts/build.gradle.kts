plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
    `maven-publish`
}

group = "com.github.kvnpetit"
version = providers.gradleProperty("version")
    .orElse(providers.environmentVariable("VERSION"))
    .orElse("2.1.1").get()

dokka {
    dokkaPublications.html {
        moduleName.set("better-french-tts")
        moduleVersion.set(providers.gradleProperty("dokkaVersion").orElse("dev"))
        providers.gradleProperty("dokkaOutputDirectory").orNull?.let {
            outputDirectory.set(file(it))
        }
    }
    dokkaSourceSets.configureEach {
        includes.from(rootProject.file(providers.gradleProperty("dokkaIncludes").orElse("docs/api.md").get()))
        val documentedSources = providers.gradleProperty("dokkaSourceDirectory")
            .map { file(it) }.orElse(file("src/main/java"))
        if (providers.gradleProperty("dokkaSourceDirectory").isPresent) {
            sourceRoots.setFrom(documentedSources)
        }
        jdkVersion.set(21)
        skipEmptyPackages.set(true)
        sourceLink {
            localDirectory.set(documentedSources.get())
            remoteUrl.set(providers.gradleProperty("dokkaSourceRef").orElse("main").map {
                uri("https://github.com/kvnpetit/better-french-tts/tree/$it/better-french-tts/src/main/java")
            })
            remoteLineSuffix.set("#L")
        }
    }
    pluginsConfiguration {
        versioning {
            version.set(providers.gradleProperty("dokkaVersion").orElse("dev"))
            renderVersionsNavigationOnAllPages.set(true)
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
    compileSdk = 37

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
    dokkaPlugin("org.jetbrains.dokka:versioning-plugin:${libs.versions.dokka.get()}")
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
