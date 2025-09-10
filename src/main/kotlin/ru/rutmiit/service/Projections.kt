package ru.rutmiit.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.eventstore.dbclient.ReadStreamOptions
import com.eventstore.dbclient.StreamNotFoundException
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import ru.rutmiit.data.Product
import ru.rutmiit.data.WarehouseRepository
import ru.rutmiit.event.OrderPlacedEvent
import ru.rutmiit.util.EventStoreCoroutineClient
import ru.rutmiit.util.EventStoreCoroutineClient.Companion.onlyEvents
import java.util.UUID

class Projections(
    private val client: EventStoreCoroutineClient,
    private val repository: WarehouseRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    suspend fun getProductProjection(productId: UUID): Product? {
        val product = repository.findById(productId) ?: return null

        try {
            val events = client.readStreamFlow(
                stream = "product-$productId",
                options = ReadStreamOptions.get().fromStart()
            ).onlyEvents()

            events.map {
                val eventData = it.event.eventData
                objectMapper.readValue<OrderPlacedEvent>(eventData)
            }.collect { event ->
                product.apply(event)
            }
        } catch (e: StreamNotFoundException) {
            logger.error(e) { "Ошибка получения проекции" }
            return product
        }

        return product
    }
}
