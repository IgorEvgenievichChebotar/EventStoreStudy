package ru.rutmiit.event

import java.time.Instant
import java.util.UUID

interface Event {
    val eventId: UUID
        get() = UUID.randomUUID()
    val occurredOn: Instant
        get() = Instant.now()
}
