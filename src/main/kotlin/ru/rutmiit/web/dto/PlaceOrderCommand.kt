package ru.rutmiit.web.dto

import java.util.UUID

data class PlaceOrderCommand(val productId: UUID, val quantity: Int)
