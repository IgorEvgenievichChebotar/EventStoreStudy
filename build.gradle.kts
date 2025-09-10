plugins {
    kotlin("jvm") version "2.2.0"
}

group = "ru.rutmiit"
version = "1.0-SNAPSHOT"

// Версии зависимостей
val kafkaVersion = "4.0.0"
val ktorVersion = "3.1.2"
val coroutinesVersion = "1.10.2"
val kotlinLoggingVersion = "7.0.7"
val logbackVersion = "1.5.18"
val jacksonVersion = "2.19.0"
val koinVersion = "4.0.4"
val r2dbcPostgresqlVersion = "1.0.7.RELEASE"
val r2dbcPoolVersion = "1.0.2.RELEASE"
val springR2dbcVersion = "3.4.5"
val r2dbcProxyVersion = "1.1.5.RELEASE"
val springWebfluxVersion = "6.2.6"
val dotenvVersion = "6.5.1"
val eventStoreVersion = "5.4.5"
val jacksonModuleKotlinVersion = "2.20.0"


repositories {
    mavenCentral()
}

dependencies {
    // EventStoreDB
    implementation("com.eventstore:db-client-java:$eventStoreVersion")

    // Ktor
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutinesVersion")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonModuleKotlinVersion")

    // Koin DI
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-ktor:$koinVersion")

    // Database - R2DBC
    implementation("io.r2dbc:r2dbc-pool:$r2dbcPoolVersion")
    implementation("io.r2dbc:r2dbc-proxy:$r2dbcProxyVersion")
    implementation("org.postgresql:r2dbc-postgresql:$r2dbcPostgresqlVersion")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc:$springR2dbcVersion")
    implementation("org.springframework:spring-webflux:${springWebfluxVersion}")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:$kotlinLoggingVersion")
    runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")

    // Testing
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}