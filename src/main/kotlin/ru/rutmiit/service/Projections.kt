package ru.rutmiit.service

import com.eventstore.dbclient.ReadStreamOptions
import com.eventstore.dbclient.StreamNotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.map
import ru.rutmiit.data.Product
import ru.rutmiit.data.WarehouseRepository
import ru.rutmiit.event.OrderPlacedEvent
import ru.rutmiit.util.EventStoreCoroutineClient
import ru.rutmiit.util.EventStoreCoroutineClient.Companion.onlyEvents
import java.util.*

class Projections(
    private val client: EventStoreCoroutineClient,
    private val repository: WarehouseRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    suspend fun getProductProjection(productId: UUID): Product? {
        val product = repository.findById(productId) ?: return null

        val events = try {
            client.readStreamFlow(
                stream = "product-$productId",
                options = ReadStreamOptions.get().fromStart().forwards()
            ).onlyEvents()
        } catch (e: StreamNotFoundException) {
            logger.error(e) { "Ошибка получения проекции" }
            return product
        }

        events.map {
            val eventData = it.event.eventData
            objectMapper.readValue<OrderPlacedEvent>(eventData)
        }.collect { event ->
            product.apply(event)
        }

        return product
    }
}
