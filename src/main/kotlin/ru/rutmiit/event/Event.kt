package ru.rutmiit.event

import java.time.Instant
import java.time.LocalDateTime
import java.util.*

abstract class Event {
    val eventId: UUID? = UUID.randomUUID()
    val occurredOn: Instant = Instant.now()

    override fun toString(): String {
        return "Event(eventId=$eventId, occurredAt=$occurredOn)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Event) return false

        if (eventId != other.eventId) return false

        return true
    }

    override fun hashCode(): Int {
        return eventId?.hashCode() ?: 0
    }

}