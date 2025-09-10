package ru.rutmiit.util

import com.eventstore.dbclient.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import java.util.concurrent.CompletableFuture

/**
 * Тонкая корутинная обёртка под присланные сигнатуры EventStoreDBClient.
 * Ничего не меняет в семантике: только suspend/Flow поверх существующих API.
 */
class EventStoreCoroutineClient(private val client: EventStoreDBClient) {

    /* ========== suspend-обёртки над CompletableFuture ========== */

    suspend fun appendToStream(stream: String, vararg events: EventData): WriteResult =
        client.appendToStream(stream, *events).await()

    suspend fun appendToStream(
        stream: String,
        options: AppendToStreamOptions,
        vararg events: EventData
    ): WriteResult = client.appendToStream(stream, options, *events).await()

    suspend fun setStreamMetadata(stream: String, metadata: StreamMetadata): WriteResult =
        client.setStreamMetadata(stream, metadata).await()

    /**
     * В Java-версии `options` может быть null — Kotlin спокойно передаст null.
     */
    suspend fun setStreamMetadata(
        stream: String,
        options: AppendToStreamOptions?,
        metadata: StreamMetadata
    ): WriteResult = client.setStreamMetadata(stream, options, metadata).await()

    suspend fun readStream(stream: String, options: ReadStreamOptions): ReadResult =
        client.readStream(stream, options).await()

    suspend fun readAll(options: ReadAllOptions = ReadAllOptions.get()): ReadResult =
        client.readAll(options).await()

    suspend fun getStreamMetadata(stream: String): StreamMetadata =
        client.getStreamMetadata(stream).await()

    suspend fun getStreamMetadata(stream: String, options: ReadStreamOptions?): StreamMetadata =
        client.getStreamMetadata(stream, options).await()

    suspend fun deleteStream(
        stream: String,
        options: DeleteStreamOptions = DeleteStreamOptions.get()
    ): DeleteResult = client.deleteStream(stream, options).await()

    suspend fun tombstoneStream(
        stream: String,
        options: DeleteStreamOptions = DeleteStreamOptions.get()
    ): DeleteResult = client.tombstoneStream(stream, options).await()

    /* ========== Flow над Publisher<ReadMessage> ========== */

    /** ReadStream как Flow<ReadMessage> (ровно что возвращает readStreamReactive). */
    fun readStreamFlow(
        stream: String,
        options: ReadStreamOptions? = null
    ): Flow<ReadMessage> = client.readStreamReactive(
        stream,
        options ?: ReadStreamOptions.get()
    ).asFlow()

    /** ReadAll как Flow<ReadMessage>. */
    fun readAllFlow(
        options: ReadAllOptions? = null
    ): Flow<ReadMessage> = client.readAllReactive(
        options ?: ReadAllOptions.get()
    ).asFlow()

    /* ========== Подписки -> Flow<ResolvedEvent> ========== */

    /**
     * Подписка на stream как Flow<ResolvedEvent>.
     *
     * @param onStop вызывается при cancel flow — сюда передай правильный способ остановить Subscription для твоей версии.
     *               По умолчанию — no-op, чтобы не гадать о методах Subscription.
     */
    fun subscribeToStreamFlow(
        stream: String,
        options: SubscribeToStreamOptions? = null,
        buffer: Int = Channel.BUFFERED,
        onStop: (Subscription) -> Unit = {}
    ): Flow<ResolvedEvent> = callbackFlow {
        val listener = object : SubscriptionListener() {
            override fun onEvent(subscription: Subscription, event: ResolvedEvent) {
                trySend(event).isSuccess
            }

            override fun onCancelled(subscription: Subscription, exception: Throwable?) {
                // если отмена по ошибке — закрываем flow с причиной
                if (exception != null) close(exception) else close()
            }

            override fun onConfirmation(subscription: Subscription) { /* опционально: emit сигнал состояния */
            }

            override fun onCaughtUp(subscription: Subscription) { /* опционально */
            }

            override fun onFellBehind(subscription: Subscription) { /* опционально */
            }
        }

        val subFuture: CompletableFuture<Subscription> =
            client.subscribeToStream(stream, listener, options ?: SubscribeToStreamOptions.get())

        // если не удалось начать подписку — закрываем Flow
        subFuture.whenComplete { _, err -> if (err != null) close(err) }

        awaitClose {
            runCatching { subFuture.getNow(null)?.let(onStop) }
        }
    }.buffer(buffer)

    /**
     * Подписка на $all как Flow<ResolvedEvent>.
     */
    fun subscribeToAllFlow(
        options: SubscribeToAllOptions? = null,
        buffer: Int = Channel.BUFFERED,
        onStop: (Subscription) -> Unit = {}
    ): Flow<ResolvedEvent> = callbackFlow {
        val listener = object : SubscriptionListener() {
            override fun onEvent(subscription: Subscription, event: ResolvedEvent) {
                trySend(event).isSuccess
            }

            override fun onCancelled(subscription: Subscription, exception: Throwable?) {
                if (exception != null) close(exception) else close()
            }

            override fun onConfirmation(subscription: Subscription) {}
            override fun onCaughtUp(subscription: Subscription) {}
            override fun onFellBehind(subscription: Subscription) {}
        }

        val subFuture: CompletableFuture<Subscription> =
            client.subscribeToAll(listener, options ?: SubscribeToAllOptions.get())

        subFuture.whenComplete { _, err -> if (err != null) close(err) }

        awaitClose {
            runCatching { subFuture.getNow(null)?.let(onStop) }
        }
    }.buffer(buffer)

    companion object {
        /** Утилита: оставить только события. */
        fun Flow<ReadMessage>.onlyEvents(): Flow<ResolvedEvent> =
            mapNotNull { if (it.hasEvent()) it.event else null }
    }
}

/** Удобный extension, чтобы быстро получить корутинный фасад. */
fun EventStoreDBClient.asCoroutines(): EventStoreCoroutineClient = EventStoreCoroutineClient(this)
