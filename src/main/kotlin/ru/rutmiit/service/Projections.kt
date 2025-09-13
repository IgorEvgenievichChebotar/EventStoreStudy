package ru.rutmiit.service

import com.eventstore.dbclient.ReadStreamOptions
import com.eventstore.dbclient.StreamNotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.map
import ru.rutmiit.data.Product
import ru.rutmiit.data.WarehouseRepository
import ru.rutmiit.event.EventRegistry
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
        // 1. снимок бы хранил в эвент сторе в том же стриме, что и события. Иначе будет eventual consistency.
        // 2. вместо fromStart() был бы offset снапшота, и события бы проигрывались поверх него.
        // 3. после успешной записи порции событий.
        val product = repository.findById(productId) ?: return null

        val events = try {
            client.readStreamFlow(
                stream = "product-$productId",
                options = ReadStreamOptions.get().fromStart().forwards()
            ).onlyEvents().map {
                val eventData = it.event.eventData
                val eventClass = EventRegistry.getEventClass(it.event.eventType)
                objectMapper.readValue(eventData, eventClass)
            }
        } catch (e: StreamNotFoundException) {
            logger.error(e) { "Ошибка получения событий для продукта" }
            return product
        }

        events.collect { event ->
            product.apply(event)
        }

        return product
    }
}
