package com.github.pjozsef.coreduks

import com.nhaarman.mockitokotlin2.*
import io.kotlintest.inspectors.forAll
import io.kotlintest.matchers.startWith
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.mockito.Mockito
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

private const val THREAD_NAME_PREFIX = "CoReduksDispatcherThread @CoReduks"

private const val DUMMY_ACTION: String = ""

class StoreTest : FreeSpec({
    data class TestState(val counter: Int)

    val initialState = TestState(0)
    val state1 = TestState(1)
    val state2 = TestState(2)
    val state3 = TestState(3)
    val state4 = TestState(4)
    val state5 = TestState(5)

    val mockReducer = mock<Reducer<TestState>>().apply {
        whenever(invoke(any(), any())).thenReturn(
                state1,
                state2,
                state3,
                state4,
                state5)
    }

    "Store without middlewares" - {
        val store = Store(initialState, mockReducer)

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
                lateinit var threadName: String

                val store = Store(initialState, object : Reducer<TestState> {
                    override fun invoke(currentState: TestState, action: Any): TestState {
                        threadName = Thread.currentThread().name
                        return currentState
                    }

                })

                store.dispatch(DUMMY_ACTION)
                store.awaitCompletion()

                threadName should startWith(THREAD_NAME_PREFIX)
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
                fun createMock(action: ()->Unit) = mock<Subscriber<TestState>> {
                    on { onNewState(any()) } doAnswer {
                        action()
                    }
                }

                lateinit var threadName1: String
                lateinit var threadName2: String
                lateinit var threadName3: String

                val subscriber1 = createMock{threadName1=Thread.currentThread().name}
                val subscriber2 = createMock{threadName2=Thread.currentThread().name}
                val subscriber3 = createMock{threadName3=Thread.currentThread().name}

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
}) {
    override fun isInstancePerTest() = true
}