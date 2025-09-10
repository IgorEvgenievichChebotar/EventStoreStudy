package ru.rutmiit.data

import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Database
import java.util.UUID

class WarehouseRepository(private val db: Database) {
    fun findById(id: UUID): Product? = transaction(db) {
        Product.findById(id)
    }

    fun findAll(): List<Product> = transaction(db) {
        Product.all().toList()
    }
}