package ru.rutmiit.web.dto

import java.util.*

data class PlaceOrderCommand(val productId: UUID, val quantity: Int)
