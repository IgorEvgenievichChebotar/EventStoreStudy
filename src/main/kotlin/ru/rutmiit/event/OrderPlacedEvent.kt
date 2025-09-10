package ru.rutmiit.event

import java.util.UUID

data class OrderPlacedEvent(
    val productId: UUID,
    val quantity: Int
) : DomainEvent
