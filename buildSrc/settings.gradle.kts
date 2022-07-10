dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    versionCatalogs {
        create("libs") {
            from(files("$rootDir/../libs.versions.toml"))
        }
    }
}
