package ru.rutmiit.data

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.transactions.transaction
import ru.rutmiit.event.OrderPlacedEvent
import java.util.UUID

object Products : UUIDTable() {
    val name = varchar("name", 50)
    val quantityInStock = integer("quantity_in_stock")
}

class Product(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Product>(Products)

    var name by Products.name
    var quantityInStock by Products.quantityInStock

    fun apply(event: OrderPlacedEvent) {
        if (quantityInStock >= event.quantity) {
            quantityInStock -= event.quantity
        } else {
            throw InvalidOperationException("Insufficient stock.")
        }
    }
}

class InvalidOperationException(message: String) : RuntimeException(message)