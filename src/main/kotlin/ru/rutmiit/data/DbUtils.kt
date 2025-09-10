package ru.rutmiit.data

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitSingleOrNull

class DbUtils(private val client: DatabaseClient) {
    suspend fun migrate() {
        client.sql(
            """
                create table if not exists products(
                    id uuid primary key,
                    quantity_in_stock integer
                )
            
        """.trimIndent()
        )
            .fetch()
            .awaitSingleOrNull()
    }

    suspend fun seed() {
        client.sql(
            """
            insert into products (id, quantity_in_stock) values (:id::uuid, :quantity_in_stock) on conflict do nothing
        """.trimIndent()
        )
            .bind("id", "c3d4b7e4-9e6a-4b1e-8b0a-2e9d8c4f6a2d")
            .bind("quantity_in_stock", 100)
            .fetch()
            .awaitSingleOrNull()

        client.sql(
            """
            insert into products (id, quantity_in_stock) values (:id::uuid, :quantity_in_stock) on conflict do nothing
        """.trimIndent()
        )
            .bind("id", "a1b2c3d4-e5f6-a7b8-c9d0-e1f2a3b4c5d6")
            .bind("quantity_in_stock", 50)
            .fetch()
            .awaitSingleOrNull()
    }
}