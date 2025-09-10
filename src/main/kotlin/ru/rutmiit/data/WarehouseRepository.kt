package ru.rutmiit.data

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class WarehouseRepository(private val db: Database) {
    suspend fun findById(id: UUID): Product? = newSuspendedTransaction(db = db) {
        Product.findById(id)
    }

    suspend fun findAll(): List<Product> = newSuspendedTransaction(db = db) {
        Product.all().toList()
    }
}