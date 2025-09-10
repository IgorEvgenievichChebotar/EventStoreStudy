package ru.rutmiit.event

import java.util.*

class OrderCancelledEvent(
    val productId: UUID,
    val quantity: Int
) : Event()