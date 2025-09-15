package ru.rutmiit

import com.eventstore.dbclient.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.springframework.r2dbc.core.DatabaseClient
import ru.rutmiit.data.DbUtils
import ru.rutmiit.data.WarehouseRepository
import ru.rutmiit.event.OrderCancelledEvent
import ru.rutmiit.event.OrderPlacedEvent
import ru.rutmiit.event.ProductRestockedEvent
import ru.rutmiit.service.Projections
import ru.rutmiit.util.EventStoreCoroutineClient
import ru.rutmiit.util.EventStoreCoroutineClient.Companion.asCoroutines
import ru.rutmiit.util.EventStoreCoroutineClient.Companion.mapEvents
import ru.rutmiit.web.dto.CancelOrderCommand
import ru.rutmiit.web.dto.PlaceOrderCommand
import ru.rutmiit.web.dto.ProductDto
import ru.rutmiit.web.dto.RestockProductCommand
import java.util.*

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

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
}

fun Application.configureRouting() {
    val client by inject<EventStoreCoroutineClient>()
    val projections by inject<Projections>()
    val objectMapper by inject<ObjectMapper>()
    val repository by inject<WarehouseRepository>()
    routing {
        route("/orders") {
            post {
                val command = call.receive<PlaceOrderCommand>()
                val product = projections.getProductProjection(command.productId)

                if (product != null && product.quantityInStock >= command.quantity) {
                    val orderPlacedEvent = OrderPlacedEvent(command.productId, command.quantity)
                    val eventData = EventData.builderAsJson(
                        orderPlacedEvent.eventId,
                        orderPlacedEvent::class.simpleName,
                        objectMapper.writeValueAsBytes(orderPlacedEvent),
                    ).build()

                    client.appendToStream(
                        stream = "product-${command.productId}",
                        options = AppendToStreamOptions.get().expectedRevision(ExpectedRevision.any()),
                        eventData
                    )
                    call.respond(HttpStatusCode.OK, "Order placed successfully.")
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Insufficient stock.")
                }
            }
            post("/cancel") {
                val command = call.receive<CancelOrderCommand>()
                val orderCancelledEvent = OrderCancelledEvent(command.productId, command.quantity)
                val eventData = EventData.builderAsJson(
                    orderCancelledEvent.eventId,
                    orderCancelledEvent::class.simpleName,
                    objectMapper.writeValueAsBytes(orderCancelledEvent),
                ).build()
                client.appendToStream(
                    stream = "product-${command.productId}",
                    options = AppendToStreamOptions.get().expectedRevision(ExpectedRevision.any()),
                    eventData
                )
                call.respond(HttpStatusCode.OK, "Order cancelled successfully.")
            }
            post("/restock") {
                val command = call.receive<RestockProductCommand>()
                val productRestockedEvent = ProductRestockedEvent(command.productId, command.quantity)
                val eventData = EventData.builderAsJson(
                    productRestockedEvent.eventId,
                    productRestockedEvent::class.simpleName,
                    objectMapper.writeValueAsBytes(productRestockedEvent),
                ).build()
                client.appendToStream(
                    stream = "product-${command.productId}",
                    options = AppendToStreamOptions.get().expectedRevision(ExpectedRevision.any()),
                    eventData
                )
                call.respond(HttpStatusCode.OK, "Product restocked successfully.")
            }
        }
        route("/products") {
            get {
                val products = repository.findAll().map {
                    ProductDto(it.id, it.quantityInStock)
                }
                call.respond(products)
            }

            get("/{productId}/events") {
                val productId = UUID.fromString(call.parameters["productId"])
                val events = client.readStreamFlow(
                    stream = "product-$productId",
                    options = ReadStreamOptions.get().fromStart()
                )
                    .mapEvents()
                    .toList()

                call.respond(events)
            }
        }
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
            DatabaseClient.create(connectionFactory).also { client ->
                if (cfg.propertyOrNull("app.db.migrate")?.getString()?.toBooleanStrictOrNull() ?: false) {
                    DbUtils.migrate(client)
                }
                if (cfg.propertyOrNull("app.db.seed")?.getString()?.toBooleanStrictOrNull() ?: false) {
                    DbUtils.seed(client)
                }
            }
        }

        // WarehouseRepository
        single { WarehouseRepository(get()) }
        single { Projections(get(), get(), get()) }
    })
}