package ru.rutmiit.web

import com.eventstore.dbclient.AppendToStreamOptions
import com.eventstore.dbclient.EventData
import com.eventstore.dbclient.ExpectedRevision
import com.eventstore.dbclient.ReadStreamOptions
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import ru.rutmiit.data.WarehouseRepository
import ru.rutmiit.event.OrderCancelledEvent
import ru.rutmiit.event.OrderPlacedEvent
import ru.rutmiit.event.ProductRestockedEvent
import ru.rutmiit.service.Projections
import ru.rutmiit.util.EventStoreCoroutineClient
import ru.rutmiit.util.EventStoreCoroutineClient.Companion.onlyEvents
import ru.rutmiit.web.dto.CancelOrderCommand
import ru.rutmiit.web.dto.PlaceOrderCommand
import ru.rutmiit.web.dto.ProductDto
import ru.rutmiit.web.dto.RestockProductCommand
import java.util.*

fun Route.orderRoutes(
    client: EventStoreCoroutineClient,
    projections: Projections,
    objectMapper: ObjectMapper,
) {
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
}

fun Route.productRoutes(
    client: EventStoreCoroutineClient,
    repository: WarehouseRepository,
) {
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
                .onlyEvents()
                .map { it.event }
                .toList()
                .sortedByDescending { it.created }

            call.respond(events)
        }
    }
}
