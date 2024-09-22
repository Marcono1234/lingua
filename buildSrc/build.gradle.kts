plugins {
    kotlin("jvm") version libs.versions.kotlinPlugin
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get()))
    }
}

sourceSets {
    main {
        java {
            // Use the source files of the main project; they are needed for creating the binary language models
            // Try to include as few files as possible, otherwise every change in main sources causes binary
            // models to be considered outdated
            srcDir("$rootDir/../src/main/kotlin/com/github/pemistahl/lingua/internal/model")
        }
    }
}

dependencies {
    implementation(libs.moshi)
    implementation(libs.moshiKotlin)
    implementation(libs.fastutil)
}

repositories {
    mavenCentral()
}
