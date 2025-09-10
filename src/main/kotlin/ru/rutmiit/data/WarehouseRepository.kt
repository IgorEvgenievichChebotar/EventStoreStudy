package ru.rutmiit.data

import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.flow
import java.util.*

class WarehouseRepository(connectionFactory: ConnectionFactory) {
    private val client: DatabaseClient = DatabaseClient.create(connectionFactory)

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
            .awaitSingle()
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
