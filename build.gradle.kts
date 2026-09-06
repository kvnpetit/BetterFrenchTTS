// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.dokka)
}

val kotlinFormatter by configurations.creating {
    attributes {
        attribute(
            org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE,
            objects.named(org.gradle.api.attributes.Bundling.SHADOWED),
        )
    }
}
dependencies {
    kotlinFormatter("com.facebook:ktfmt:0.61")
}

val kotlinSources = files(
    fileTree("better-french-tts/src") { include("**/*.kt") },
    fileTree("app/src") { include("**/*.kt") },
)

fun registerKotlinFormatTask(taskName: String, checkOnly: Boolean) =
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        description = if (checkOnly) "Check Kotlin source formatting" else "Format Kotlin sources"
        classpath = kotlinFormatter
        mainClass.set("com.facebook.ktfmt.cli.Main")
        args("--kotlinlang-style")
        if (checkOnly) args("--dry-run", "--set-exit-if-changed")
        args(kotlinSources.files.sortedBy { it.path }.map { it.absolutePath })
    }

registerKotlinFormatTask("formatKotlin", false)
registerKotlinFormatTask("checkKotlinFormat", true)
