package ru.rutmiit.data

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object DatabaseSeeder {
    fun seed(database: Database) {
        transaction(database) {
            if (Product.all().empty()) {
                Products.insert {
                    it[id] = UUID.fromString("c3d4b7e4-9e6a-4b1e-8b0a-2e9d8c4f6a2d")
                    it[name] = "Sample Product 1"
                    it[quantityInStock] = 100
                }
                Products.insert {
                    it[id] = UUID.fromString("a1b2c3d4-e5f6-a7b8-c9d0-e1f2a3b4c5d6")
                    it[name] = "Sample Product 2"
                    it[quantityInStock] = 50
                }
            }
        }
    }
}
