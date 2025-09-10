package ru.rutmiit.web

import com.eventstore.dbclient.AppendToStreamOptions
import com.eventstore.dbclient.EventData
import com.eventstore.dbclient.ExpectedRevision
import com.eventstore.dbclient.ReadStreamOptions
import com.eventstore.dbclient.StreamNotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.koin.ktor.ext.get

import ru.rutmiit.web.dto.ProductDto
import ru.rutmiit.data.WarehouseRepository
import ru.rutmiit.event.OrderPlacedEvent
import ru.rutmiit.service.Projections
import ru.rutmiit.util.EventStoreCoroutineClient
import ru.rutmiit.util.EventStoreCoroutineClient.Companion.onlyEvents
import ru.rutmiit.web.dto.PlaceOrderCommand
import java.util.UUID

fun Route.orderRoutes(
    client: EventStoreCoroutineClient,
    projections: Projections,
    objectMapper: ObjectMapper
) {
    route("/orders") {
        post {
            val command = call.receive<PlaceOrderCommand>()
            val product = projections.getProductProjection(command.productId)

            if (product != null && product.quantityInStock >= command.quantity) {
                val orderPlacedEvent = OrderPlacedEvent(command.productId, command.quantity)
                val eventData = EventData.builderAsJson(
                    orderPlacedEvent.eventId,
                    orderPlacedEvent::class.java.simpleName,
                    objectMapper.writeValueAsBytes(orderPlacedEvent)
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
    }
}

fun Route.productRoutes(
    client: EventStoreCoroutineClient,
    repository: WarehouseRepository,
    objectMapper: ObjectMapper
) {
    route("/products") {
        get {
            val products = repository.findAll().map {
                ProductDto(it.id.value, it.name, it.quantityInStock)
            }
            call.respond(products)
        }

        get("/{productId}/events") {
            val productId = UUID.fromString(call.parameters["productId"])
            val events = client.readStreamFlow(
                stream = "product-$productId",
                options = ReadStreamOptions.get().fromStart()
            )
                .onlyEvents()
                .map {
                    val eventData = it.event.eventData
                    objectMapper.runCatching {
                        readValue<OrderPlacedEvent>(eventData)
                    }.getOrNull()
                }.toList()

            call.respond(events)
        }
    }
}
