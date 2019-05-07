package com.github.pjozsef.coreduks.reducer

import io.kotlintest.matchers.startWith
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.FreeSpec
import java.lang.IllegalArgumentException
import java.time.LocalDateTime

class CombineReducerKtTest : FreeSpec({
    class TestAction
    data class TestPerson(val age: Int, val height: Int, val name: String, val birthDay: LocalDateTime)

    "property argument validation" - {

        "should fail if not all properties are enumerated" {
            shouldThrow<IllegalArgumentException> {
                combineReducers<TestPerson, TestAction> {
                    TestPerson::age { state, _ ->
                        state.age
                    }
                    TestPerson::height { state, _ ->
                        state.height
                    }
                }
            }
        }

        "should fail if lambda return type does not match property type" {
            shouldThrow<IllegalArgumentException> {
                combineReducers<TestPerson, TestAction> {
                    TestPerson::age { state, _ ->
                        state.age
                    }
                    TestPerson::birthDay { state, _ ->
                        state.birthDay
                    }
                    TestPerson::height { state, _ ->
                        state.height
                    }
                    TestPerson::name { state, _ ->
                        state.age
                    }
                }
            }.message should startWith("Types did not match: property: ")
        }

    }

    "combined reducer with identity reducers should return the same state" {
        val reducer = combineReducers<TestPerson, TestAction> {
            TestPerson::age { state, _ ->
                state.age
            }
            TestPerson::birthDay { state, _ ->
                state.birthDay
            }
            TestPerson::height { state, _ ->
                state.height
            }
            TestPerson::name { state, _ ->
                state.name
            }
        }
        val input = TestPerson(40, 180, "John", LocalDateTime.MAX)
        val output = reducer.invoke(input, TestAction())
        output shouldBe input
    }

    "combined reducer" - {
        class CelebrateBirthDay
        data class ChangeName(val newName: String)

        val actions = listOf(
                CelebrateBirthDay(),
                ChangeName("Mr John"),
                CelebrateBirthDay(),
                CelebrateBirthDay()
        )

        val reducer = combineReducers<TestPerson, Any> {
            TestPerson::age { state, action ->
                when (action) {
                    is CelebrateBirthDay -> state.age + 1
                    else -> state.age
                }
            }
            TestPerson::birthDay { state, _ ->
                state.birthDay
            }
            TestPerson::height { state, _ ->
                state.height
            }
            TestPerson::name { state, action ->
                when (action) {
                    is ChangeName -> action.newName
                    else -> state.name
                }
            }
        }

        val initialState = TestPerson(40, 180, "John", LocalDateTime.MAX)
        val expectedState = TestPerson(43, 180, "Mr John", LocalDateTime.MAX)

        "with real reducers" {
            actions.fold(initialState, reducer::invoke).let {
                it shouldBe expectedState
            }
        }
    }

}) {
    override fun isInstancePerTest() = true
}