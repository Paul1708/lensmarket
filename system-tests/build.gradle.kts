plugins {
    kotlin("jvm") version "2.3.21"
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    testImplementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testRuntimeOnly("commons-logging:commons-logging:1.3.5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<Test> {
    useJUnitPlatform()
    onlyIf {
        providers.gradleProperty("lensmarket.systemTests").orNull?.toBoolean() == true
    }
    systemProperty(
        "lensmarket.base-url",
        providers.gradleProperty("lensmarket.baseUrl").orElse("http://localhost:8081").get()
    )
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}
