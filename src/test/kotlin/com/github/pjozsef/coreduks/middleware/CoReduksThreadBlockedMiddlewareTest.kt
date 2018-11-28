package com.github.pjozsef.coreduks.middleware

import com.github.pjozsef.coreduks.Store
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotlintest.matchers.numerics.shouldBeInRange
import io.kotlintest.specs.FreeSpec

private interface TestAction
private interface TestState

class CoReduksThreadBlockedMiddlewareTest : FreeSpec({

    "Middleware" - {
        val mockAction = mock<TestAction>()
        val mockStore = mock<Store<TestState, TestAction>>()
        val mockBeyondThresholdAction = mock<(Long) -> Unit>()
        val middleware = CoReduksThreadBlockedMiddleware<TestState, TestAction>(
                50,
                beyondThresholdAction = mockBeyondThresholdAction)

        "should do nothing if threshold is not passed" {
            middleware(mockStore, mockAction){
                mock()
            }
            verifyZeroInteractions(mockBeyondThresholdAction)
        }

        "should invoke lambda if threshold is passed" {
            middleware(mockStore, mockAction){
                Thread.sleep(60)
                mock()
            }
            argumentCaptor<Long>{
                verify(mockBeyondThresholdAction).invoke(capture())

                firstValue shouldBeInRange 60L..70L
            }
        }
    }

}) {
    override fun isInstancePerTest() = true
}