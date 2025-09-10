package ru.rutmiit.web.dto

import java.util.*

data class CancelOrderCommand(val productId: UUID, val quantity: Int)