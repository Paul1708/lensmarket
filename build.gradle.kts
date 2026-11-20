import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import groovy.json.JsonSlurper

plugins {
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.allopen") version "2.3.21" apply false
    id("io.quarkus") version "3.35.3" apply false
    id("org.jetbrains.dokka") version "2.2.0"
    jacoco
}

version = "0.1.0"

jacoco {
    toolVersion = "0.8.15"
}

dokka {
    dokkaPublications.html {
        moduleName.set("LensMarket")
        moduleVersion.set(project.version.toString())
        outputDirectory.set(layout.projectDirectory.dir("docs/build/kdoc"))
        failOnWarning.set(true)
        suppressObviousFunctions.set(true)
    }
}

dependencies {
    add("dokka", project(":image-service"))
    add("dokka", project(":image-service:application"))
    add("dokka", project(":listing-service"))
    add("dokka", project(":listing-service:application"))
    add("dokka", project(":messaging-service"))
    add("dokka", project(":messaging-service:application"))
    add("dokka", project(":user-service"))
    add("dokka", project(":user-service:application"))
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "jacoco")

    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.15"
    }

    tasks.withType<Test>().configureEach {
        finalizedBy("jacocoTestReport")
    }

    tasks.withType<JacocoReport>().configureEach {
        dependsOn("test")
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    extensions.configure<DokkaExtension>("dokka") {
        dokkaPublications.html {
            moduleName.set(project.path.removePrefix(":").replace(':', '-'))
            moduleVersion.set(project.version.toString())
            failOnWarning.set(true)
            suppressObviousFunctions.set(true)
        }

        dokkaSourceSets.configureEach {
            documentedVisibilities.set(
                setOf(
                    VisibilityModifier.Public,
                    VisibilityModifier.Protected,
                    VisibilityModifier.Internal,
                    VisibilityModifier.Private,
                    VisibilityModifier.Package
                )
            )
            reportUndocumented.set(false)
            skipEmptyPackages.set(true)
            suppressGeneratedFiles.set(true)
            jdkVersion.set(17)
        }
    }
}

val serviceProjects = listOf(
    project(":image-service"),
    project(":listing-service"),
    project(":messaging-service"),
    project(":user-service")
)

val applicationProjects = serviceProjects.map { project("${it.path}:application") }
val coverageProjects = serviceProjects + applicationProjects

val openApiContracts = listOf(
    "user-service.openapi.json",
    "listing-service.openapi.json",
    "image-service.openapi.json",
    "messaging-service.openapi.json"
).map { file("docs/openapi/$it") }

tasks.register("validateOpenApi") {
    group = "verification"
    description = "Prueft, dass docs/openapi dem aus den OpenAPI-Annotationen generierten Stand entspricht " +
        "(Diff gegen committeten Stand), sowie JSON-Grundstruktur und lokale Referenzen der Vertraege. " +
        "Setzt voraus, dass 'quarkusBuild' fuer jeden Service zuvor in einer EIGENEN Gradle-Invocation " +
        "gelaufen ist (siehe docs/openapi/README.md) -- mehrere Services' quarkusBuild-Tasks in derselben " +
        "JVM/Invocation koennen OpenAPI-Metadaten zwischen den Services vertauschen (beobachteter, " +
        "nicht-deterministischer Quarkus/SmallRye-Bug)."
    inputs.files(openApiContracts)

    doLast {
        val diffProcess = ProcessBuilder(
            listOf("git", "diff", "--exit-code", "--") + openApiContracts.map { it.relativeTo(rootDir).path }
        )
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val diffOutput = diffProcess.inputStream.bufferedReader().readText()
        val diffExitCode = diffProcess.waitFor()
        check(diffExitCode == 0) {
            "docs/openapi weicht vom generierten Stand ab. Bitte '.\\gradlew.bat quarkusBuild' lokal ausfuehren " +
                "und die Aenderungen unter docs/openapi/ committen.\n$diffOutput"
        }

        fun resolvePointer(document: Any?, pointer: String): Any? {
            var current = document
            pointer.removePrefix("#/").split('/').forEach { rawSegment ->
                val segment = rawSegment.replace("~1", "/").replace("~0", "~")
                current = (current as? Map<*, *>)?.get(segment) ?: return null
            }
            return current
        }

        fun validateReferences(document: Any?, value: Any?, location: String) {
            when (value) {
                is Map<*, *> -> {
                    (value["\$ref"] as? String)?.let { reference ->
                        check(reference.startsWith("#/")) {
                            "$location: Nur dokumentlokale OpenAPI-Referenzen sind erlaubt: $reference"
                        }
                        check(resolvePointer(document, reference) != null) {
                            "$location: Nicht aufloesbare OpenAPI-Referenz: $reference"
                        }
                    }
                    value.forEach { (key, child) ->
                        validateReferences(document, child, "$location/$key")
                    }
                }
                is List<*> -> value.forEachIndexed { index, child ->
                    validateReferences(document, child, "$location/$index")
                }
            }
        }

        openApiContracts.forEach { contract ->
            check(contract.isFile) { "Fehlender OpenAPI-Vertrag: ${contract.relativeTo(rootDir)}" }
            val document = JsonSlurper().parse(contract) as? Map<*, *>
                ?: error("OpenAPI-Wurzel muss ein JSON-Objekt sein: ${contract.relativeTo(rootDir)}")
            check(document["openapi"] == "3.1.0") {
                "${contract.relativeTo(rootDir)}: Erwartet OpenAPI 3.1.0"
            }
            check(document["paths"] is Map<*, *>) {
                "${contract.relativeTo(rootDir)}: paths fehlt"
            }
            check(document["components"] is Map<*, *>) {
                "${contract.relativeTo(rootDir)}: components fehlt"
            }
            validateReferences(document, document, "#")
        }
    }
}

tasks.register("architectureCheck") {
    group = "verification"
    description = "Prueft die Clean-Architecture-Grenzen aller LensMarket-Services."
    dependsOn(applicationProjects.map { "${it.path}:compileKotlin" })

    doLast {
        val violations = mutableListOf<String>()
        val forbiddenInnerPrefixes = listOf(
            "com.fasterxml.",
            "io.minio.",
            "io.quarkus.",
            "io.smallrye.",
            "io.vertx.",
            "jakarta.",
            "net.coobird.",
            "org.eclipse.microprofile."
        )
        val forbiddenOutwardSegments = setOf(
            "auth", "composition", "data", "events", "metrics", "resources", "runtime"
        )

        serviceProjects.forEach { service ->
            val applicationDir = file("${service.projectDir}/application")
            fileTree("$applicationDir/src/main/kotlin") { include("**/*.kt") }.files.forEach { source ->
                source.readLines().forEachIndexed { index, line ->
                    val imported = line.trim().takeIf { it.startsWith("import ") }
                        ?.removePrefix("import ")
                        ?.substringBefore(" as ")
                        ?: return@forEachIndexed
                    if (forbiddenInnerPrefixes.any(imported::startsWith)) {
                        violations += "${source.relativeTo(rootDir)}:${index + 1}: framework import $imported"
                    }
                    val segments = imported.split('.')
                    if (segments.take(2) == listOf("lensmarket", service.name.substringBefore("-service")) &&
                        segments.getOrNull(2) in forbiddenOutwardSegments
                    ) {
                        violations += "${source.relativeTo(rootDir)}:${index + 1}: outward import $imported"
                    }
                }
            }

            val applicationBuild = file("$applicationDir/build.gradle.kts")
            applicationBuild.readLines().forEachIndexed { index, line ->
                val declaration = line.trim()
                if (listOf("api(", "implementation(", "compileOnly(", "runtimeOnly(")
                        .any(declaration::startsWith)
                ) {
                    violations += "${applicationBuild.relativeTo(rootDir)}:${index + 1}: production dependency $declaration"
                }
            }

            fileTree("${service.projectDir}/src/main/kotlin") { include("**/*Resource.kt") }.files.forEach { source ->
                source.readLines().forEachIndexed { index, line ->
                    val imported = line.trim().removePrefix("import ")
                    if (line.trim().startsWith("import ") && listOf(
                            ".auth.", ".composition.", ".data.", ".events.", ".metrics.", ".domain.port."
                        ).any(imported::contains)
                    ) {
                        violations += "${source.relativeTo(rootDir)}:${index + 1}: resource bypasses use case via $imported"
                    }
                }
            }

            fileTree("${service.projectDir}/src/main/kotlin") { include("**/events/**/*Consumer*.kt") }.files.forEach { source ->
                source.readLines().forEachIndexed { index, line ->
                    val imported = line.trim().removePrefix("import ")
                    if (line.trim().startsWith("import ") &&
                        (".domain.port." in imported && "Repository" in imported || ".data.repository." in imported)
                    ) {
                        violations += "${source.relativeTo(rootDir)}:${index + 1}: consumer accesses repository via $imported"
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException("Clean-Architecture-Verletzungen:\n${violations.joinToString("\n")}")
        }
    }
}

tasks.register<JacocoReport>("jacocoAggregatedReport") {
    group = "verification"
    description = "Erzeugt einen gemeinsamen JaCoCo-Report aller LensMarket-Services."
    dependsOn(coverageProjects.map { "${it.path}:test" })

    executionData.setFrom(coverageProjects.map { it.layout.buildDirectory.file("jacoco/test.exec") })
    classDirectories.setFrom(coverageProjects.map {
        fileTree(it.layout.buildDirectory.dir("classes/kotlin/main")) {
            exclude("**/*\$Companion.class", "**/*\$DefaultImpls.class")
        }
    })
    sourceDirectories.setFrom(coverageProjects.map { it.layout.projectDirectory.dir("src/main/kotlin") })

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
