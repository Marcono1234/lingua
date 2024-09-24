dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("$rootDir/../libs.versions.toml"))
        }
    }
}

// Automatically download JDK toolchain if necessary, see https://docs.gradle.org/8.10.1/userguide/toolchains.html#sec:provisioning
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
