package com.github.pjozsef.coreduks

import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

interface Reducer<T> {

    operator fun invoke(currentState: T, action: Any): T

}

interface Subscriber<T> {

    fun onNewState(newState: T)

}

class Store<T>(
        initialState: T,
        val reducer: Reducer<T>,
        val scope: CoroutineScope = DEFAULT_SCOPE,
        val defaultSubscriberContext: CoroutineContext = DEFAULT_SCOPE.coroutineContext) {

    companion object {
        val DEFAULT_SCOPE: CoroutineScope by lazy {
            val dispatcher = Executors.newFixedThreadPool(1) {
                Thread(it, "CoReduksDispatcherThread")
            }.asCoroutineDispatcher()

            val name = CoroutineName("CoReduks")

            val parentJob = Job()

            val context = dispatcher + name + parentJob

            object : CoroutineScope {
                override val coroutineContext: CoroutineContext
                    get() = context
            }
        }
    }

    private var currentState: T = initialState
    private val subscribers: MutableMap<Subscriber<T>, CoroutineContext> = HashMap()

    val state: T
        get() = runBlocking { stateAsync.await() }

    val stateAsync: Deferred<T>
        get() = scope.async { currentState }

    fun dispatch(action: Any) {
        scope.launch {
            val newState = reducer(currentState, action)
            currentState = newState
            subscribers.forEach(notifyOf(currentState))
        }
    }

    fun subscribe(subscriber: Subscriber<T>, coroutineContext: CoroutineContext = defaultSubscriberContext) {
        scope.launch {
            subscribers[subscriber] = coroutineContext
            notifyOf(currentState)(subscriber, coroutineContext)
        }
    }

    fun unsubscribe(subscriber: Subscriber<T>) {
        scope.launch {
            subscribers.remove(subscriber)
        }
    }

    private fun notifyOf(newState: T): (Subscriber<T>, CoroutineContext) -> Unit = { subscriber, context ->
        scope.launch(context) {
            subscriber.onNewState(newState)
        }
    }
}