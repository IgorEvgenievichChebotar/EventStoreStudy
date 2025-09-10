package ru.rutmiit.web.dto

import java.util.UUID

data class ProductDto(
    val id: UUID,
    val name: String,
    val quantityInStock: Int
)
