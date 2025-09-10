package ru.rutmiit.web.dto

import java.util.*

data class RestockProductCommand(val productId: UUID, val quantity: Int)