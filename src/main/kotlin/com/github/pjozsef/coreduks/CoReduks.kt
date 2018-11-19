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

interface Middleware<T> {
    operator fun invoke(store: Store<T>, action: Any, next: (Any) -> T): T
}

interface Store<T> {
    val state: T
    fun dispatch(action: Any)
}

class CoReduksStore<T>(
        initialState: T,
        reducer: Reducer<T>,
        middlewares: List<Middleware<T>> = listOf(),
        val scope: CoroutineScope = DEFAULT_SCOPE,
        val defaultSubscriberContext: CoroutineContext = scope.coroutineContext) : Store<T> {

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

    private inner class StoreProxy : Store<T> {
        override val state: T
            get() = this@CoReduksStore.currentState

        override fun dispatch(action: Any) {
            this@CoReduksStore.dispatch(action)
        }
    }

    private var currentState: T = initialState
    private val proxy: StoreProxy = StoreProxy()
    private val subscribers: MutableMap<Subscriber<T>, CoroutineContext> = hashMapOf()
    private val dispatchFunction: (Any) -> T = createDispatchFunction(middlewares.asReversed()) {
        reducer(currentState, it)
    }

    override val state: T
        get() = runBlocking { stateAsync.await() }

    val stateAsync: Deferred<T>
        get() = scope.async { currentState }

    override fun dispatch(action: Any) {
        scope.launch {
            val newState = dispatchFunction(action)
            if(currentState != newState){
                currentState = newState
                for ((subscriber, context) in subscribers) {
                    withContext(context) {
                        subscriber.onNewState(currentState)
                    }
                }
            }
        }
    }

    fun subscribe(subscriber: Subscriber<T>, context: CoroutineContext = defaultSubscriberContext) {
        scope.launch {
            subscribers[subscriber] = context
            withContext(context) {
                subscriber.onNewState(currentState)
            }
        }
    }

    fun unsubscribe(subscriber: Subscriber<T>) {
        scope.launch {
            subscribers.remove(subscriber)
        }
    }

    private tailrec fun createDispatchFunction(middlewares: List<Middleware<T>>, currentDispatch: (Any) -> T): (Any) -> T =
            when (middlewares.size) {
                0 -> currentDispatch
                else -> {
                    val head = middlewares.first()
                    val tail = middlewares.subList(1, middlewares.size)
                    val nextDispatch: (Any) -> T = {
                        head(proxy, it, currentDispatch)
                    }
                    createDispatchFunction(tail, nextDispatch)
                }
            }
}