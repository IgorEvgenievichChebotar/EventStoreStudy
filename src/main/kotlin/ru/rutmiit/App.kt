package ru.rutmiit

import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBClientSettings
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import ru.rutmiit.util.asCoroutines

private val logger = KotlinLogging.logger { }

fun main() = runCatching {
    embeddedServer(Netty, port = 8080) {
        configureSerialization()
        configureDI()
    }.start(wait = true)
}.fold(
    onFailure = { cause -> logger.error(cause) { "Ошибка старта приложения" } },
    onSuccess = { logger.info { "Приложение завершено" } }
)

fun Application.configureSerialization() = install(ContentNegotiation) {
    jackson {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}

fun Application.configureDI() = install(Koin) {
    modules(module {
        single {
            jacksonObjectMapper().apply {
                registerModule(JavaTimeModule())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        }

        single {
            EventStoreDBClient.create(
                EventStoreDBClientSettings.builder()
                    .addHost("localhost", 2112)
                    .tls(false)
                    .buildConnectionSettings()
            ).asCoroutines()
        }
    })
}