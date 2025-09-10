package ru.rutmiit.event

import java.time.LocalDateTime
import java.util.UUID

interface Event {
    val eventId: UUID
        get() = UUID.randomUUID()
    val occurredOn: LocalDateTime
        get() = LocalDateTime.now()
}
