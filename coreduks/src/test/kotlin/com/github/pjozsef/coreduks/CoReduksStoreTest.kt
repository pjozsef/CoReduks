package com.github.pjozsef.coreduks

import com.nhaarman.mockitokotlin2.*
import io.kotlintest.inspectors.forAll
import io.kotlintest.matchers.startWith
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import org.mockito.Mockito
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

private const val THREAD_NAME_PREFIX = "CoReduksDispatcherThread @CoReduks"

private const val DUMMY_ACTION: String = ""

class CoReduksStoreTest : FreeSpec({
    data class TestState(val counter: Int)

    val initialState = TestState(0)
    val state1 = TestState(1)
    val state2 = TestState(2)
    val state3 = TestState(3)
    val state4 = TestState(4)
    val state5 = TestState(5)

    val mockReducer = mock<Reducer<TestState, String>>().apply {
        whenever(invoke(any(), any())).thenReturn(
                state1,
                state2,
                state3,
                state4,
                state5)
    }

    "Default params" - {
        "scope should default to DEFAULT_SCOPE" {
            val store = CoReduksStore(initialState, mockReducer)

            store.scope shouldBe CoReduksStore.DEFAULT_SCOPE
        }

        "subscriber context should default to scope's context" - {
            "when scope not defined" {
                val store = CoReduksStore(initialState, mockReducer)

                store.defaultSubscriberContext shouldBe CoReduksStore.DEFAULT_SCOPE.coroutineContext
            }

            "when scope defined" {
                val store = CoReduksStore(initialState, mockReducer, scope = GlobalScope)

                store.defaultSubscriberContext shouldBe GlobalScope.coroutineContext
            }
        }
    }

    "CoReduksStore without middlewares" - {
        val store = CoReduksStore(initialState, mockReducer)

        "State" - {

            "should return initialState as state after instantiation" {
                store.state shouldBe initialState
            }

            "should return state after all queued actions are handled" {
                store.dispatch(DUMMY_ACTION)
                store.dispatch(DUMMY_ACTION)
                store.dispatch(DUMMY_ACTION)
                store.dispatch(DUMMY_ACTION)
                store.dispatch(DUMMY_ACTION)

                store.state shouldBe TestState(5)
            }
        }

        "Dispatch" - {
            val action = "action"

            "should execute in the given coroutine context" {
                val reducer = object : Reducer<TestState, String> {
                    lateinit var threadName:String
                    override fun invoke(currentState: TestState, action: String): TestState {
                        threadName = Thread.currentThread().name
                        return currentState
                    }

                }
                val store = CoReduksStore(initialState, reducer)

                store.dispatch(DUMMY_ACTION)
                store.awaitCompletion()

                reducer.threadName should startWith(THREAD_NAME_PREFIX)
            }

            "should call reducer" {
                store.dispatch(action)
                store.awaitCompletion()
                verify(mockReducer).invoke(initialState, action)
            }

            "should update the state" {
                store.dispatch(action)
                store.awaitCompletion()
                store.state shouldBe state1
            }

        }

        "Subscribe" - {

            "should execute subscribe in the given coroutine context"{
                lateinit var threadName: String
                val subscriberMap = mock<HashMap<Subscriber<TestState>, CoroutineContext>> {
                    doAnswer {
                        threadName = Thread.currentThread().name
                    }.`when`(it)[com.nhaarman.mockitokotlin2.any()] = com.nhaarman.mockitokotlin2.any()
                }
                store.setProperty("subscribers", subscriberMap)

                store.subscribe(mock())
                store.awaitCompletion()

                threadName should startWith(THREAD_NAME_PREFIX)
            }

            "should immediately call subscriber after subscription" - {
                val mockSubscriber = mock<Subscriber<TestState>>()

                "before any actions were dispatched" {
                    store.subscribe(mockSubscriber)
                    store.awaitCompletion()

                    verify(mockSubscriber).onNewState(initialState)
                }

                "after dispatch"{
                    store.dispatch(DUMMY_ACTION)
                    store.subscribe(mockSubscriber)
                    store.awaitCompletion()

                    verify(mockSubscriber).onNewState(state1)
                }
            }

            "should notify subscribers upon new state" {
                val subscribers = (1..5).map { mock<Subscriber<TestState>>() }
                subscribers.forEach { store.subscribe(it) }

                store.dispatch(DUMMY_ACTION)
                store.awaitCompletion()

                subscribers.forAll {
                    verify(it).onNewState(state1)
                }
            }

            "should notify subscribers on their given coroutineContext" - {
                fun createMock(action: () -> Unit) = mock<Subscriber<TestState>> {
                    on { onNewState(any()) } doAnswer {
                        action()
                    }
                }

                lateinit var threadName1: String
                lateinit var threadName2: String
                lateinit var threadName3: String

                val subscriber1 = createMock { threadName1 = Thread.currentThread().name }
                val subscriber2 = createMock { threadName2 = Thread.currentThread().name }
                val subscriber3 = createMock { threadName3 = Thread.currentThread().name }

                val coroutineContext2 = Dispatchers.Default
                val coroutineContext3 = Executors.newFixedThreadPool(5) {
                    Thread(it, "CustomDispatcher")
                }.asCoroutineDispatcher()

                store.subscribe(subscriber1)
                store.subscribe(subscriber2, coroutineContext2)
                store.subscribe(subscriber3, coroutineContext3)

                store.awaitCompletion()
                store.awaitCompletion(coroutineContext2)
                store.awaitCompletion(coroutineContext3)

                "after subscription" {
                    threadName1 should startWith(THREAD_NAME_PREFIX)
                    threadName2 should startWith("DefaultDispatcher")
                    threadName3 should startWith("CustomDispatcher")
                }

                "after dispatch" {
                    store.dispatch(DUMMY_ACTION)
                    store.awaitCompletion()
                    store.awaitCompletion(coroutineContext2)
                    store.awaitCompletion(coroutineContext3)

                    threadName1 should startWith(THREAD_NAME_PREFIX)
                    threadName2 should startWith("DefaultDispatcher")
                    threadName3 should startWith("CustomDispatcher")
                }
            }

            "should not notify if state didn't change" {
                val mockReducer = mock<Reducer<TestState, String>> {
                    on { invoke(any(), any()) } doAnswer {
                        it.arguments.first() as TestState
                    }
                }
                val store = CoReduksStore(initialState, mockReducer)
                val subscriber1 = mock<Subscriber<TestState>>()
                store.subscribe(subscriber1)
                store.dispatch(DUMMY_ACTION)
                store.awaitCompletion()

                verify(subscriber1).onNewState(initialState)
                verifyNoMoreInteractions(subscriber1)
            }
        }

        "Unsubscribe" - {

            "should execute unsubscribe in the given coroutine context"{
                lateinit var threadName: String
                val subscriberMap = mock<HashMap<Subscriber<TestState>, CoroutineContext>> {
                    doAnswer {
                        threadName = Thread.currentThread().name
                    }.`when`(it).remove(com.nhaarman.mockitokotlin2.any())
                }
                store.setProperty("subscribers", subscriberMap)

                store.unsubscribe(mock())
                store.awaitCompletion()

                threadName should startWith(THREAD_NAME_PREFIX)
            }

            "should not notify unsubscribed objects" {
                val subscriber1 = mock<Subscriber<TestState>>()
                val subscriber2 = mock<Subscriber<TestState>>()

                store.subscribe(subscriber1)
                store.subscribe(subscriber2)
                store.dispatch(DUMMY_ACTION)
                store.unsubscribe(subscriber1)
                store.dispatch(DUMMY_ACTION)
                store.awaitCompletion()

                Mockito.inOrder(subscriber1, subscriber2).run {
                    verify(subscriber1).onNewState(initialState)
                    verify(subscriber2).onNewState(initialState)
                    verify(subscriber1).onNewState(state1)
                    verify(subscriber2).onNewState(state1)
                    verify(subscriber2).onNewState(state2)
                }
                verifyNoMoreInteractions(subscriber1)
                verifyNoMoreInteractions(subscriber2)
            }
        }
    }

    "CoReduksStore with middlewares" - {
        val beforeNext1 = mock<Runnable>()
        val beforeNext2 = mock<Runnable>()
        val beforeNext3 = mock<Runnable>()
        val afterNext1 = mock<Runnable>()
        val afterNext2 = mock<Runnable>()
        val afterNext3 = mock<Runnable>()

        fun createMiddleware(before: Runnable, after: Runnable) = object : Middleware<TestState, String> {
            override fun invoke(store: Store<TestState, String>, action: String, next: (String) -> TestState): TestState {
                before.run()
                val nextState = next(action)
                after.run()
                return nextState
            }
        }

        "when each middleware calls next" {
            val middleware1 = createMiddleware(beforeNext1, afterNext1)
            val middleware2 = createMiddleware(beforeNext2, afterNext2)
            val middleware3 = createMiddleware(beforeNext3, afterNext3)
            val middlewares = listOf(middleware1, middleware2, middleware3)

            val store = CoReduksStore(initialState, mockReducer, middlewares)

            store.dispatch(DUMMY_ACTION)
            store.awaitCompletion()

            Mockito.inOrder(
                    beforeNext1,
                    beforeNext2,
                    beforeNext3,
                    afterNext1,
                    afterNext2,
                    afterNext3,
                    mockReducer).apply {
                verify(beforeNext1).run()
                verify(beforeNext2).run()
                verify(beforeNext3).run()
                verify(mockReducer)(initialState, DUMMY_ACTION)
                verify(afterNext3).run()
                verify(afterNext2).run()
                verify(afterNext1).run()
            }

            store.state shouldBe state1
        }

        "when a middleware breaks the call chain" {
            val breaksTheChain = mock<Runnable>()
            val middleware1 = createMiddleware(beforeNext1, afterNext1)
            val middleware2 = object : Middleware<TestState, String> {
                override fun invoke(store: Store<TestState, String>, action: String, next: (String) -> TestState): TestState {
                    breaksTheChain.run()
                    return store.state
                }
            }
            val middleware3 = createMiddleware(beforeNext3, afterNext3)
            val middlewares = listOf(middleware1, middleware2, middleware3)

            val store = CoReduksStore(initialState, mockReducer, middlewares)

            store.dispatch(DUMMY_ACTION)
            store.awaitCompletion()

            Mockito.inOrder(
                    beforeNext1,
                    afterNext1,
                    breaksTheChain).apply {
                verify(beforeNext1).run()
                verify(breaksTheChain).run()
                verify(afterNext1).run()
            }
            verifyZeroInteractions(
                    beforeNext2,
                    beforeNext3,
                    mockReducer,
                    afterNext3,
                    afterNext1
            )

            store.state shouldBe initialState
        }

        "middlewares should see the up to date state" {
            val states = arrayListOf<TestState>()
            val middleware = object : Middleware<TestState, String> {
                override fun invoke(store: Store<TestState, String>, action: String, next: (String) -> TestState): TestState {
                    states.add(store.state)
                    return next(action)
                }
            }
            val store = CoReduksStore(initialState, mockReducer, listOf(middleware))

            store.dispatch(DUMMY_ACTION)
            store.dispatch(DUMMY_ACTION)
            store.awaitCompletion()

            states shouldBe listOf(initialState, state1)
        }

        "when a middleware dispatches an action throughout the call chain" {
            val secondAction = "Counter is 0!"
            val middleware1 = createMiddleware(beforeNext1, afterNext1)
            val middleware2 = object : Middleware<TestState, String> {
                override fun invoke(store: Store<TestState, String>, action: String, next: (String) -> TestState): TestState {
                    if (store.state.counter == 0) {
                        store.dispatch(secondAction)
                    }
                    return next(action)
                }
            }
            val middlewares = listOf(middleware1, middleware2)
            val subscriber1 = mock<Subscriber<TestState>>()
            val subscriber2 = mock<Subscriber<TestState>>()

            val store = CoReduksStore<TestState, String>(initialState, mockReducer, middlewares)
            store.subscribe(subscriber1)
            store.subscribe(subscriber2)

            store.dispatch(DUMMY_ACTION)
            store.awaitCompletion()

            Mockito.inOrder(
                    beforeNext1,
                    afterNext1,
                    mockReducer,
                    subscriber1,
                    subscriber2).apply {
                verify(subscriber1).onNewState(initialState)
                verify(subscriber2).onNewState(initialState)
                verify(beforeNext1).run()
                verify(mockReducer)(initialState, DUMMY_ACTION)
                verify(afterNext1).run()
                verify(subscriber1).onNewState(state1)
                verify(subscriber2).onNewState(state1)
                verify(beforeNext1).run()
                verify(mockReducer)(state1, secondAction)
                verify(afterNext1).run()
            }
        }

        "when a middleware dispatches an action throughout the call chain and breaks the call chain" {
            val secondAction = "Counter is 0!"
            val middleware1 = createMiddleware(beforeNext1, afterNext1)
            val middleware2 = object : Middleware<TestState, String> {
                var alreadyFired = false
                override fun invoke(store: Store<TestState, String>, action: String, next: (String) -> TestState): TestState {
                    return if (!alreadyFired) {
                        store.dispatch(secondAction)
                        alreadyFired = true
                        store.state
                    } else {
                        next(action)
                    }
                }
            }
            val middlewares = listOf(middleware1, middleware2)
            val subscriber1 = mock<Subscriber<TestState>>()
            val subscriber2 = mock<Subscriber<TestState>>()

            val store = CoReduksStore<TestState, String>(initialState, mockReducer, middlewares)
            store.subscribe(subscriber1)
            store.subscribe(subscriber2)

            store.dispatch(DUMMY_ACTION)
            store.awaitCompletion()

            Mockito.inOrder(
                    beforeNext1,
                    afterNext1,
                    mockReducer,
                    subscriber1,
                    subscriber2).apply {
                verify(subscriber1).onNewState(initialState)
                verify(subscriber2).onNewState(initialState)
                verify(beforeNext1).run()
                verify(afterNext1).run()
                verify(beforeNext1).run()
                verify(mockReducer)(initialState, secondAction)
                verify(afterNext1).run()
                verify(subscriber1).onNewState(state1)
                verify(subscriber2).onNewState(state1)
            }
        }
    }
}) {
    override fun isInstancePerTest() = true
}