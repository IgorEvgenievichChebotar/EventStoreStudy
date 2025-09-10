package ru.rutmiit.event

import java.util.*

class ProductRestockedEvent(
    val productId: UUID,
    val quantity: Int
) : Event()