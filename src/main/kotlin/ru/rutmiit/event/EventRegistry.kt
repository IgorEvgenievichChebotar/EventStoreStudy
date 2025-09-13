package ru.rutmiit.event

object EventRegistry {
    private val eventRegistry = mutableMapOf<String, Class<out Event>>()

    init {
        eventRegistry[OrderCancelledEvent::class.java.simpleName] = OrderCancelledEvent::class.java
        eventRegistry[OrderPlacedEvent::class.java.simpleName] = OrderPlacedEvent::class.java
        eventRegistry[ProductRestockedEvent::class.java.simpleName] = ProductRestockedEvent::class.java
    }

    fun getEventClass(eventType: String): Class<out Event> {
        return eventRegistry[eventType] ?: error("Unexpected event type: $eventType")
    }
}