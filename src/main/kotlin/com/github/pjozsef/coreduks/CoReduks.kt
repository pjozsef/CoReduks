package com.github.pjozsef.coreduks

import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

interface Reducer<S, A> {
    operator fun invoke(currentState: S, action: A): S
}

interface Subscriber<S> {
    fun onNewState(newState: S)
}

interface Middleware<S, A> {
    operator fun invoke(store: Store<S, A>, action: A, next: (A) -> S): S
}

interface Store<S, A> {
    val state: S
    fun dispatch(action: A)
}


class CoReduksStore<S, A> @JvmOverloads constructor(
        initialState: S,
        reducer: Reducer<S, A>,
        middlewares: List<Middleware<S, A>> = listOf(),
        val scope: CoroutineScope = DEFAULT_SCOPE,
        val defaultSubscriberContext: CoroutineContext = scope.coroutineContext) : Store<S, A> {

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

    private inner class StoreProxy : Store<S, A> {
        override val state: S
            get() = this@CoReduksStore.currentState

        override fun dispatch(action: A) {
            this@CoReduksStore.dispatch(action)
        }
    }

    private var currentState: S = initialState
    private val proxy: StoreProxy = StoreProxy()
    private val subscribers: MutableMap<Subscriber<S>, CoroutineContext> = hashMapOf()
    private val dispatchFunction: (A) -> S = createDispatchFunction(middlewares.asReversed()) {
        reducer(currentState, it)
    }

    override val state: S
        get() = runBlocking { stateAsync.await() }

    val stateAsync: Deferred<S>
        get() = scope.async { currentState }

    override fun dispatch(action: A) {
        scope.launch {
            val newState = dispatchFunction(action)
            if (currentState != newState) {
                currentState = newState
                for ((subscriber, context) in subscribers) {
                    withContext(context) {
                        subscriber.onNewState(currentState)
                    }
                }
            }
        }
    }

    fun subscribe(subscriber: Subscriber<S>, context: CoroutineContext = defaultSubscriberContext) {
        scope.launch {
            subscribers[subscriber] = context
            withContext(context) {
                subscriber.onNewState(currentState)
            }
        }
    }

    fun unsubscribe(subscriber: Subscriber<S>) {
        scope.launch {
            subscribers.remove(subscriber)
        }
    }

    private tailrec fun createDispatchFunction(middlewares: List<Middleware<S, A>>, currentDispatch: (A) -> S): (A) -> S =
            when (middlewares.size) {
                0 -> currentDispatch
                else -> {
                    val head = middlewares.first()
                    val tail = middlewares.subList(1, middlewares.size)
                    val nextDispatch: (A) -> S = {
                        head(proxy, it, currentDispatch)
                    }
                    createDispatchFunction(tail, nextDispatch)
                }
            }
}