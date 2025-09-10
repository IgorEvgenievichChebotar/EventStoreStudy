package ru.rutmiit.data

import ru.rutmiit.event.OrderPlacedEvent
import java.util.*

data class Product(
    val id: UUID,
    var quantityInStock: Int
) {
    fun apply(event: OrderPlacedEvent) {
        if (quantityInStock >= event.quantity) {
            quantityInStock -= event.quantity
        } else {
            throw InvalidOperationException("Insufficient stock.")
        }
    }
}

class InvalidOperationException(message: String) : RuntimeException(message)