package ru.rutmiit.data

import ru.rutmiit.event.Event
import ru.rutmiit.event.OrderCancelledEvent
import ru.rutmiit.event.OrderPlacedEvent
import java.util.*

data class Product(
    val id: UUID,
    var quantityInStock: Int
) {
    fun apply(event: Event) {
        when (event) {
            is OrderPlacedEvent -> {
                if (quantityInStock >= event.quantity) {
                    quantityInStock -= event.quantity
                } else {
                    throw InvalidOperationException("Insufficient stock.")
                }
            }

            is OrderCancelledEvent -> {
                quantityInStock += event.quantity
            }
        }
    }
}

class InvalidOperationException(message: String) : RuntimeException(message)