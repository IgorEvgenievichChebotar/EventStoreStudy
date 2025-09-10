package ru.rutmiit.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.flow
import java.util.*

class WarehouseRepository(private val client: DatabaseClient) {
    suspend fun findById(id: UUID): Product? {
        return client.sql("SELECT * FROM products WHERE id = :id")
            .bind("id", id)
            .map { row ->
                Product(
                    id = row["id"] as UUID,
                    quantityInStock = row["quantity_in_stock"] as Int
                )
            }
            .one()
            .awaitSingleOrNull()
    }

    fun findAll(): Flow<Product> {
        return client.sql("SELECT * FROM products")
            .map { row ->
                Product(
                    id = row["id"] as UUID,
                    quantityInStock = row["quantity_in_stock"] as Int
                )
            }
            .flow()
    }
}
