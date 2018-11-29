package com.github.pjozsef.coreduks

import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec

class CoReduksHistoryTest : FreeSpec({
    fun <T> History<T>.fillUpWith(values: List<T>) = values.fold(this, History<T>::newValue)

    "History that does not ignore first value" - {
        var history: History<Int> = CoReduksHistory(0)
        var otherHistory: History<Int> = CoReduksHistory(0)

        "new value" - {

            "should set the current value" {
                history.newValue(1).let {
                    it.current shouldBe 1
                }
            }

            "should intert the previous current value in the past list" {
                history.newValue(1).let {
                    it.past shouldBe listOf(0)
                }
            }

            "should clear future list" {
                history.newValue(1).undo().newValue(2).let {
                    it.past shouldBe listOf(0)
                    it.current shouldBe 2
                    it.future shouldBe emptyList()
                }
            }
        }

        "undo" - {
            val history = CoReduksHistory(0)
                    .newValue(1)
                    .newValue(2)
                    .undo()
            "should set the current value" {
                history.current shouldBe 1
            }


            "should pop the last element from past" {
                history.past shouldBe listOf(0)
            }

            "should intert the previous current value in the future list" {
                history.future shouldBe listOf(2)
            }

            "undo with empty past should do nothing" {
                CoReduksHistory(0).undo().undo().undo().undo() shouldBe CoReduksHistory(0)
            }
        }

        "redo" - {
            val history = CoReduksHistory(0)
                    .newValue(1)
                    .newValue(2)
                    .undo()
                    .undo()
                    .redo()
            "should set the current value" {
                history.current shouldBe 1
            }

            "should pop the first element from future" {
                history.future shouldBe listOf(2)
            }

            "should intert the previous current value in the past list" {
                history.past shouldBe listOf(0)
            }

            "redo with empty future should do nothing" {
                CoReduksHistory(0).redo().redo().redo().redo() shouldBe CoReduksHistory(0)
            }
        }

        "operators" - {
            "newValue and += should work the same way" {
                (0..10).forEach {
                    history = history.newValue(it)
                    otherHistory += it
                }.let {
                    history shouldBe otherHistory
                }
            }

            "undo and - should work the same way" {
                history = history.fillUpWith((0..20).toList())
                otherHistory = otherHistory.fillUpWith((0..20).toList())

                repeat(10) {
                    history = history.undo()
                    otherHistory = -otherHistory
                }

                history shouldBe otherHistory
            }

            "redo and + should work the same way" {
                history = history.fillUpWith((0..20).toList())
                otherHistory = otherHistory.fillUpWith((0..20).toList())

                repeat(10) {
                    history = history.undo()
                    otherHistory = -otherHistory
                }

                repeat(10) {
                    history = history.redo()
                    otherHistory = +otherHistory
                }

                history shouldBe otherHistory
            }
        }
    }


    "History that ignores first value" - {
        val history: History<Int> = CoReduksHistory(0, ignoreFirstValue = true)

        "should not store initial value in its history" {
            history.newValue(1).newValue(2).newValue(3).undo().let {
                it.past shouldBe listOf(1)
                it.current shouldBe 2
                it.future shouldBe listOf(3)
            }
        }

        "ignoreFirstValue flag turns false after first onNewValue" {
            history.newValue(1).let {
                it.ignoreFirstValue shouldBe false
            }
        }

        "ignoreFirstValue flag unaffected by undo action" {
            history.undo().undo().let {
                it.ignoreFirstValue shouldBe true
            }
        }

        "ignoreFirstValue flag unaffected by redo action" {
            history.redo().redo().let {
                it.ignoreFirstValue shouldBe true
            }
        }
    }
}) {
    override fun isInstancePerTest() = true
}