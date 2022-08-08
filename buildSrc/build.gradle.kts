import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version libs.versions.kotlinPlugin.get()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get()))
    }
}

val targetJdkVersion = libs.versions.targetJdk.get()
tasks.withType<KotlinCompile> {
    kotlinOptions {
        // TODO: Does not work yet due to https://youtrack.jetbrains.com/issue/KT-52823
        // @Suppress("SuspiciousCollectionReassignment")
        // freeCompilerArgs += listOf("-Xjdk-release=$targetJdkVersion")
        jvmTarget = targetJdkVersion
    }
}

java.sourceSets["main"].java {
    // Use the source files of the main project; they are needed for creating the binary language models
    // Try to include as few files as possible, otherwise every change in main sources causes binary
    // models to be considered outdated
    srcDir("$rootDir/../src/main/kotlin/com/github/pemistahl/lingua/internal/model")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.squareup.moshi:moshi:1.13.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.13.0")
    implementation(libs.fastutil)
}

repositories {
    mavenCentral()
}
