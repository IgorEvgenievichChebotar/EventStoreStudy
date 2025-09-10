package ru.rutmiit.data

import java.util.UUID

data class Product(
    val id: UUID,
    var quantityInStock: Int
) {
    fun apply(event: ru.rutmiit.event.OrderPlacedEvent) {
        if (quantityInStock >= event.quantity) {
            quantityInStock -= event.quantity
        } else {
            throw InvalidOperationException("Insufficient stock.")
        }
    }
}

class InvalidOperationException(message: String) : RuntimeException(message)
