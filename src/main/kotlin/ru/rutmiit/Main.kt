package ru.rutmiit

import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBClientSettings
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import ru.rutmiit.data.Products
import ru.rutmiit.data.WarehouseRepository
import ru.rutmiit.service.Projections
import ru.rutmiit.util.EventStoreCoroutineClient
import ru.rutmiit.util.asCoroutines
import ru.rutmiit.web.orderRoutes
import ru.rutmiit.web.productRoutes

private val logger = KotlinLogging.logger { }

fun main(args: Array<String>): Unit = EngineMain.main(args)

// Точка входа для Ktor, указанная в application.yaml
@Suppress("unused")
fun Application.module() {
    configureSerialization()
    configureDI()
    configureRouting()

    monitor.subscribe(ApplicationStarted) {
        val host = environment.config.propertyOrNull("ktor.deployment.host")?.getString()
        val port = environment.config.propertyOrNull("ktor.deployment.port")?.getString()
        logger.info { "Приложение запущено на $host:$port" }
    }
    monitor.subscribe(ApplicationStopped) {
        logger.info { "Приложение остановлено" }
    }
}

fun Application.configureRouting() {
    val client by inject<EventStoreCoroutineClient>()
    val projections by inject<Projections>()
    val repository by inject<WarehouseRepository>()
    val objectMapper by inject<ObjectMapper>()

    routing {
        orderRoutes(client, projections)
        productRoutes(client, repository, objectMapper)
    }
}

fun Application.configureDI() = install(Koin) {
    val cfg = environment.config

    modules(module {
        single<ObjectMapper> {
            jacksonObjectMapper().apply {
                registerModule(JavaTimeModule())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        }

        // EventStoreDB из конфига
        single {
            EventStoreDBClient.create(
                EventStoreDBClientSettings.builder()
                    .addHost(
                        cfg.property("app.eventstore.host").getString(),
                        cfg.property("app.eventstore.port").getString().toInt()
                    )
                    .tls(cfg.propertyOrNull("app.eventstore.tls")?.getString()?.toBooleanStrictOrNull() ?: false)
                    .buildConnectionSettings()
            ).asCoroutines()
        }

        // Exposed Database
        single {
            val hikariConfig = HikariConfig().apply {
                val host = cfg.property("app.db.host").getString()
                val port = cfg.property("app.db.port").getString().toInt()
                val database = cfg.property("app.db.database").getString()
                jdbcUrl = "jdbc:postgresql://$host:$port/$database"
                driverClassName = "org.postgresql.Driver"
                username = cfg.property("app.db.user").getString()
                password = cfg.property("app.db.password").getString()
                maximumPoolSize = 10
            }
            val dataSource = HikariDataSource(hikariConfig)
            Database.connect(dataSource).also {
                transaction(it) {
                    SchemaUtils.create(Products)
                }
            }
        }

        // WarehouseRepository
        single { WarehouseRepository(get()) }
        single { Projections(get(), get(), get()) }
    })
}

fun Application.configureSerialization() = install(ContentNegotiation) {
    jackson {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}
