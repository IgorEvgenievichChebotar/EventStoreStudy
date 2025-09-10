package ru.rutmiit.event

import java.util.UUID

class OrderPlacedEvent(
    val productId: UUID,
    val quantity: Int
) : Event