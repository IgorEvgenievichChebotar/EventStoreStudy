package ru.rutmiit.web

import com.eventstore.dbclient.AppendToStreamOptions
import com.eventstore.dbclient.EventData
import com.eventstore.dbclient.ExpectedRevision
import com.eventstore.dbclient.ReadStreamOptions
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import ru.rutmiit.data.WarehouseRepository
import ru.rutmiit.event.OrderPlacedEvent
import ru.rutmiit.service.Projections
import ru.rutmiit.util.EventStoreCoroutineClient
import ru.rutmiit.util.EventStoreCoroutineClient.Companion.onlyEvents
import ru.rutmiit.web.dto.PlaceOrderCommand
import ru.rutmiit.web.dto.ProductDto
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
                    orderPlacedEvent::class.java.simpleName,
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
    }
}

fun Route.productRoutes(
    client: EventStoreCoroutineClient,
    repository: WarehouseRepository,
    objectMapper: ObjectMapper,
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
                    objectMapper.readValue<OrderPlacedEvent>(eventData)
                }.toList()

            call.respond(events)
        }
    }
}
