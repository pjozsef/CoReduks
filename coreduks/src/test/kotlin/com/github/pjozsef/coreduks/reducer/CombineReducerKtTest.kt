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
                    slice(TestPerson::age) { state, _ ->
                        state.age
                    }
                    slice(TestPerson::height) { state, _ ->
                        state.height
                    }
                }
            }
        }

        "should fail if lambda return type does not match property type" {
            shouldThrow<IllegalArgumentException> {
                combineReducers<TestPerson, TestAction> {
                    slice(TestPerson::age) { state, _ ->
                        state.age
                    }
                    slice(TestPerson::birthDay) { state, _ ->
                        state.birthDay
                    }
                    slice(TestPerson::height) { state, _ ->
                        state.height
                    }
                    slice(TestPerson::name) { state, _ ->
                        state.age
                    }
                }
            }.message should startWith("Types did not match: property: ")
        }

    }

    "combined reducer" - {
        "with identity reducers should return the same state" {
            val reducer = combineReducers<TestPerson, TestAction> {
                slice(TestPerson::age) { state, _ ->
                    state.age
                }
                slice(TestPerson::birthDay) { state, _ ->
                    state.birthDay
                }
                slice(TestPerson::height) { state, _ ->
                    state.height
                }
                slice(TestPerson::name) { state, _ ->
                    state.name
                }
            }
            val input = TestPerson(40, 180, "John", LocalDateTime.MAX)
            val output = reducer.invoke(input, TestAction())
            output shouldBe input
        }

        "with real reducers" {
            class CelebrateBirthDay
            data class ChangeName(val newName: String)

            val reducer = combineReducers<TestPerson, Any> {
                slice(TestPerson::age) { state, action ->
                    when (action) {
                        is CelebrateBirthDay -> state.age + 1
                        else -> state.age
                    }
                }
                slice(TestPerson::birthDay) { state, _ ->
                    state.birthDay
                }
                slice(TestPerson::height) { state, _ ->
                    state.height
                }
                slice(TestPerson::name) { state, action ->
                    when (action) {
                        is ChangeName -> action.newName
                        else -> state.name
                    }
                }
            }

            val actions = listOf(
                    CelebrateBirthDay(),
                    ChangeName("Mr John"),
                    CelebrateBirthDay(),
                    CelebrateBirthDay()
            )
            val person = TestPerson(40, 180, "John", LocalDateTime.MAX)
            val expectedPerson = TestPerson(43, 180, "Mr John", LocalDateTime.MAX)
            val actualPerson = actions.fold(person, reducer::invoke)
            actualPerson shouldBe expectedPerson
        }
    }

})