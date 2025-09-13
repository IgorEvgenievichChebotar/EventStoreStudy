package ru.rutmiit.data

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.r2dbc.core.DatabaseClient

class DbUtils {
    companion object {
        private val logger = KotlinLogging.logger { }

        fun migrate(client: DatabaseClient) {
            client.sql(
                """
                create table if not exists products(
                    id uuid primary key,
                    quantity_in_stock integer
                )
            
        """.trimIndent()
            )
                .fetch()
                .rowsUpdated()
                .block()
                .also { logger.info { "Создана таблица продуктов" } }
        }

        fun seed(client: DatabaseClient) {
            client.sql(
                """
            insert into products (id, quantity_in_stock) 
            values (:id_1::uuid, :quantity_in_stock_1), (:id_2::uuid, :quantity_in_stock_2) 
            on conflict do nothing
        """.trimIndent()
            )
                .bind("id_1", "c3d4b7e4-9e6a-4b1e-8b0a-2e9d8c4f6a2d")
                .bind("quantity_in_stock_1", 100)
                .bind("id_2", "a1b2c3d4-e5f6-a7b8-c9d0-e1f2a3b4c5d6")
                .bind("quantity_in_stock_2", 50)
                .fetch()
                .rowsUpdated()
                .block()
                .also { logger.info { "Таблица продуктов заполнена тестовыми данными" } }
        }
    }
}