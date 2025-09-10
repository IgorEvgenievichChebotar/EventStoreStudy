package ru.rutmiit

import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBClientSettings
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.springframework.r2dbc.core.DatabaseClient
import ru.rutmiit.data.DbUtils
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
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

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
    val mapper by inject<ObjectMapper>()

    routing {
        orderRoutes(client, projections, mapper)
        productRoutes(client, repository)
    }
}

fun Application.configureDI() = install(Koin) {
    val cfg = environment.config

    modules(module {
        single<ObjectMapper> {
            jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
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

        // R2DBC ConnectionFactory из конфига
        single {
            val options = ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, cfg.property("app.db.driver").getString())
                .option(ConnectionFactoryOptions.HOST, cfg.property("app.db.host").getString())
                .option(ConnectionFactoryOptions.PORT, cfg.property("app.db.port").getString().toInt())
                .option(ConnectionFactoryOptions.DATABASE, cfg.property("app.db.database").getString())
                .option(ConnectionFactoryOptions.USER, cfg.property("app.db.user").getString())
                .option(ConnectionFactoryOptions.PASSWORD, cfg.property("app.db.password").getString())
                .build()
            val connectionFactory = ConnectionFactories.get(options)
            DatabaseClient.create(connectionFactory).also {
                val migrate = { runBlocking { DbUtils(it).migrate() } }
                val seed = { runBlocking { DbUtils(it).seed() } }
                if (cfg.propertyOrNull("app.db.migrate")?.getString()?.toBooleanStrictOrNull() ?: false) {
                    migrate()
                }
                if (cfg.propertyOrNull("app.db.seed")?.getString()?.toBooleanStrictOrNull() ?: false) {
                    seed()
                }
            }
        }

        // WarehouseRepository
        single { WarehouseRepository(get()) }
        single { Projections(get(), get(), get()) }
    })
}