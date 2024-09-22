/*
 * Copyright © 2018-today Peter M. Stahl pemistahl@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import ru.vyarus.gradle.plugin.python.task.PythonTask
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

val linguaTaskGroup: String by project
val linguaGroupId: String by project
val linguaArtifactId: String by project
val projectVersion: String by project
// Version of the upstream Lingua project on which this project is based
val upstreamProjectVersion: String by project
val linguaName: String by project
val linguaDescription: String by project
val linguaLicenseName: String by project
val linguaLicenseUrl: String by project
val linguaWebsiteUrl: String by project
val linguaScmConnection: String by project
val linguaScmDeveloperConnection: String by project
val linguaScmUrl: String by project
val linguaSupportedDetectors: String by project
val linguaSupportedLanguages: String by project
val linguaMainClass: String by project
val linguaCsvHeader: String by project

val compileTestKotlin: KotlinCompile by tasks

group = linguaGroupId
version = "$projectVersion-L$upstreamProjectVersion"
description = linguaDescription

plugins {
    kotlin("jvm") version libs.versions.kotlinPlugin
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)
    id("ru.vyarus.use-python") version "2.3.0"
    alias(libs.plugins.shadow)
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    `maven-publish`
    signing
    jacoco
}

val targetJdkVersion = libs.versions.targetJdk.get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get()))
    }
}

val javaModuleName = "com.github.pemistahl.lingua"
tasks.compileJava {
    options.release = targetJdkVersion.toInt()

    // Configure the Java compiler to see the compiled Kotlin class files
    // Based on https://github.com/ilya-g/kotlin-jlink-examples/blob/342ccd3762aeea33ec56e2647f3e4ae96af45cf7/gradle/library/build.gradle.kts#L43-L45
    options.compilerArgs = listOf("--patch-module", "$javaModuleName=${sourceSets.main.get().output.asPath}")
}

// Don't use `withType<KotlinCompile>` to not affect `compileTestKotlin` task
tasks.compileKotlin {
    compilerOptions {
        // Prevent using newer JDK API by accident, see https://kotlinlang.org/docs/compiler-reference.html#xjdk-release-version
        freeCompilerArgs.add("-Xjdk-release=$targetJdkVersion")
        jvmTarget.set(JvmTarget.fromTarget(targetJdkVersion))
    }
}

sourceSets {
    create("accuracyReport") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val accuracyReportImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

configurations["accuracyReportRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

tasks.withType<Test> {
    useJUnitPlatform { failFast = false }

    testLogging {
        events = setOf(TestLogEvent.SKIPPED, TestLogEvent.FAILED)

        showExceptions = true
        showStackTraces = true
        showCauses = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.test {
    maxParallelForks = 1
}

// Suppress warnings about incubating test suites feature
@Suppress("UnstableApiUsage")
testing {
    suites {
        // Separate test suite for module testing
        val testJavaModule by registering(JvmTestSuite::class) {
            dependencies {
                implementation(project())
            }
        }
    }
}

tasks.check {
    @Suppress("UnstableApiUsage")
    dependsOn(testing.suites.named("testJavaModule"))
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}
// Note: Currently has to be executed manually, maybe run it by default in the future?
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude("**/app/**")
                }
            },
        )
    )
}

tasks.register<Test>("accuracyReport") {
    group = linguaTaskGroup
    description = "Runs Lingua on provided test data, and writes detection accuracy reports for each language."
    testClassesDirs = sourceSets["accuracyReport"].output.classesDirs
    classpath = sourceSets["accuracyReport"].runtimeClasspath

    val allowedDetectors = linguaSupportedDetectors.split(',')
    val detectors = project.findProperty("detectors")?.toString()?.split(Regex("\\s*,\\s*"))
        ?: allowedDetectors

    detectors.filterNot { it in allowedDetectors }.forEach {
        throw GradleException(
            """
            detector '$it' does not exist
            supported detectors: ${allowedDetectors.joinToString(", ")}
            """.trimIndent()
        )
    }

    val allowedLanguages = linguaSupportedLanguages.split(',')
    val languages = project.findProperty("languages")?.toString()?.split(Regex("\\s*,\\s*"))
        ?: allowedLanguages

    languages.filterNot { it in allowedLanguages }.forEach {
        throw GradleException("language '$it' is not supported")
    }

    val availableCpuCores = Runtime.getRuntime().availableProcessors()
    val cpuCoresRepr = project.findProperty("cpuCores")?.toString() ?: "1"

    val cpuCores = try {
        cpuCoresRepr.toInt()
    } catch (e: NumberFormatException) {
        throw GradleException("'$cpuCoresRepr' is not a valid value for argument -PcpuCores")
    }

    if (cpuCores !in 1..availableCpuCores) {
        throw GradleException(
            """
            $cpuCores cpu cores are not supported
            minimum: 1
            maximum: $availableCpuCores
            """.trimIndent()
        )
    }

    maxHeapSize = "4096m"
    maxParallelForks = cpuCores
    reports.html.required.set(false)
    reports.junitXml.required.set(false)

    filter {
        detectors.forEach { detector ->
            languages.forEach { language ->
                includeTestsMatching(
                    "com.github.pemistahl.lingua.report" +
                        ".${detector.lowercase()}.${language}DetectionAccuracyReport"
                )
            }
        }
    }
}

val writeAggregatedAccuracyReport by tasks.registering {
    group = linguaTaskGroup
    description = "Creates a table from all accuracy detection reports and writes it to a CSV file."

    doLast {
        val accuracyReportsDirectoryName = "accuracy-reports"
        val accuracyReportsDirectory = file(accuracyReportsDirectoryName)
        if (!accuracyReportsDirectory.exists()) {
            throw GradleException("directory '$accuracyReportsDirectoryName' does not exist")
        }

        val detectors = linguaSupportedDetectors.split(',')
        val languages = linguaSupportedLanguages.split(',')

        val csvFile = file("$accuracyReportsDirectoryName/aggregated-accuracy-values.csv")
        val stringToSplitAt = ">> Exact values:"

        if (csvFile.exists()) csvFile.delete()
        csvFile.createNewFile()
        csvFile.appendText(linguaCsvHeader)
        csvFile.appendText("\n")

        for (language in languages) {
            csvFile.appendText(language)

            for (detector in detectors) {
                val languageReportFileName =
                    "$accuracyReportsDirectoryName/${detector.lowercase()}/$language.txt"
                val languageReportFile = file(languageReportFileName)
                val sliceLength = if (detector == "Lingua") (1..8) else (1..4)

                if (languageReportFile.exists()) {
                    for (line in languageReportFile.readLines()) {
                        if (line.startsWith(stringToSplitAt)) {
                            val accuracyValues = line
                                .split(stringToSplitAt)[1]
                                .split(' ')
                                .slice(sliceLength)
                                .joinToString(",")
                            csvFile.appendText(",")
                            csvFile.appendText(accuracyValues)
                        }
                    }
                } else {
                    if (detector == "Lingua") {
                        csvFile.appendText(",NaN,NaN,NaN,NaN,NaN,NaN,NaN,NaN")
                    } else {
                        csvFile.appendText(",NaN,NaN,NaN,NaN")
                    }
                }
            }

            csvFile.appendText("\n")
        }

        println("file 'aggregated-accuracy-values.csv' written successfully")
    }
}

tasks.register<PythonTask>("drawAccuracyPlots") {
    dependsOn(writeAggregatedAccuracyReport)
    group = linguaTaskGroup
    description = "Draws plots showing the results of the accuracy detection reports."
    command = "src/python-scripts/draw_accuracy_plots.py"
}

tasks.register<PythonTask>("writeAccuracyTable") {
    dependsOn(writeAggregatedAccuracyReport)
    group = linguaTaskGroup
    description = "Creates HTML table from all accuracy detection results and writes it to a markdown file."
    command = "src/python-scripts/write_accuracy_table.py"
}

tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets.configureEach {
        jdkVersion.set(17) // link against Java 17 documentation
        reportUndocumented.set(false)
        perPackageOption {
            matchingRegex.set(".*\\.(app|internal).*")
            suppress.set(true)
        }
    }
}

val dokkaJavadocJar by tasks.registering(Jar::class) {
    group = "Build"
    description = "Assembles a jar archive containing Javadoc documentation."
    archiveClassifier = "javadoc"
    from(tasks.dokkaHtml.flatMap { it.outputDirectory })
}
val dokkaHtmlJar by tasks.registering(Jar::class) {
    group = "Build"
    description = "Assembles a jar archive containing Dokka HTML documentation."
    archiveClassifier = "dokka-html"
    from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
}

val sourcesJar by tasks.registering(Jar::class) {
    group = "Build"
    description = "Assembles a jar archive containing the main source code."
    archiveClassifier = "sources"
    // This also seems to include `module-info.java`, as desired
    from(sourceSets.main.get().kotlin)
}

tasks.shadowJar {
    group = "Build"
    description =
        "Assembles a jar archive containing the main classes and all external dependencies. Only intended for usage" +
        " from command line, should not be used as dependency."
    archiveClassifier = "with-dependencies"
    duplicatesStrategy = DuplicatesStrategy.FAIL

    manifest {
        attributes(
            "Main-Class" to linguaMainClass,

            // Note: Depending on the dependencies, might have to set `Multi-Release: true`, see https://github.com/johnrengelman/shadow/issues/449
        )
    }
    // Exclude `module-info` from dependencies, see also https://github.com/johnrengelman/shadow/issues/729
    exclude("**/module-info.class")
}

val lingua by configurations.creating {
    // Prevent projects depending on lingua from seeing and using this configuration
    isCanBeConsumed = false
    isVisible = false
    isTransitive = false
}

@Suppress("PropertyName")
val modelOutputDir_ = layout.buildDirectory.dir("generated/language-models")
val createLanguageModels by tasks.registering(GenerateLanguageModelsTask::class) {
    linguaArtifact.set(lingua.singleFile)
    modelOutputDir.set(modelOutputDir_)

    finalizedBy(checkLanguageModelsChecksum)
}
sourceSets.main {
    output.dir(mutableMapOf<String, Any>("builtBy" to createLanguageModels), modelOutputDir_)
}

// Check whether generated models match expected checksum; this is done mainly to verify that model
// generation is deterministic
// Note: This is a separate task to not cause model generation task to fail, which would require regenerating
// models a second time when checksum becomes outdated
val checkLanguageModelsChecksum by tasks.registering {
    doLast {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val startDir = modelOutputDir_.get().asFile.toPath()
        Files.walk(startDir).use { files ->
            // Sort files because the iteration order is important for checksum creation
            files.filter(Files::isRegularFile).sorted(
                Comparator { a, b ->
                    // Create path strings in OS independent way
                    fun Path.createPathString(): String {
                        return startDir.relativize(this).iterator().asSequence().map(Path::toString).joinToString("/")
                    }

                    val pathA = a.createPathString()
                    val pathB = b.createPathString()
                    return@Comparator pathA.compareTo(pathB)
                }
            ).forEach {
                messageDigest.update(Files.readAllBytes(it))
            }
        }

        val actualChecksum = messageDigest.digest().joinToString("") {
            it.toInt().and(0xFF).toString(16).padStart(2, '0')
        }

        val expectedModelsChecksum: String by project
        if (actualChecksum != expectedModelsChecksum) {
            throw Exception(
                """
                Language model checksums differ:
                  Expected: $expectedModelsChecksum
                  Actual:   $actualChecksum
                """.trimIndent()
            )
        }
    }
}

dependencies {
    lingua("com.github.pemistahl:lingua:$upstreamProjectVersion")

    implementation(libs.fastutil)

    testImplementation(libs.junit)
    testImplementation(libs.assertj)

    accuracyReportImplementation("com.optimaize.languagedetector:language-detector:0.6")
    accuracyReportImplementation("org.apache.opennlp:opennlp-tools:1.9.4")
    accuracyReportImplementation("org.apache.tika:tika-core:2.3.0")
    accuracyReportImplementation("org.apache.tika:tika-langdetect-optimaize:2.3.0")
    accuracyReportImplementation("org.slf4j:slf4j-nop:1.7.36")
}

python {
    pip("matplotlib:3.5.2")
    pip("seaborn:0.11.2")
    pip("pandas:1.4.2")
    pip("numpy:1.22.0")
}

publishing {
    publications {
        create<MavenPublication>("lingua") {
            groupId = linguaGroupId
            artifactId = linguaArtifactId
            version = version

            from(components["kotlin"])

            artifact(sourcesJar)
            artifact(tasks.shadowJar)
            artifact(dokkaJavadocJar)
            artifact(dokkaHtmlJar)

            pom {
                name.set(linguaName)
                description.set(linguaDescription)
                url.set(linguaWebsiteUrl)

                licenses {
                    license {
                        name.set(linguaLicenseName)
                        url.set(linguaLicenseUrl)
                    }
                }
                scm {
                    connection.set(linguaScmConnection)
                    developerConnection.set(linguaScmDeveloperConnection)
                    url.set(linguaScmUrl)
                }
            }
        }
    }
}

nexusPublishing {
    repositories {
        sonatype()
    }
}

signing {
    sign(publishing.publications["lingua"])
}

repositories {
    mavenCentral()
}

// TODO: Signing is temporarily disabled
tasks.withType<Sign> {
    enabled = false
}
