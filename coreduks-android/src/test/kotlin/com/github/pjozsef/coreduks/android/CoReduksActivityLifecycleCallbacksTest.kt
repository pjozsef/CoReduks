package com.github.pjozsef.coreduks.android

import android.app.Activity
import com.github.pjozsef.coreduks.Observable
import com.github.pjozsef.coreduks.Subscriber
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotlintest.specs.FreeSpec

class CoReduksActivityLifecycleCallbacksTest : FreeSpec({

    "CoReduksActivityLifecycleCallbacks instantiated with Observable" - {
        val observable = mock<Observable<Any>>()
        val callbacks = CoReduksActivityLifecycleCallbacks(observable)

        "with Activity that implements Subscriber" - {
            val activity = mock<Activity>(extraInterfaces = arrayOf(Subscriber::class))

            "onActivityResumed should register activity" {
                callbacks.onActivityResumed(activity)

                verify(observable).subscribe(activity as Subscriber<Any>)
            }

            "onActivityPaused should unregister activity" {
                callbacks.onActivityPaused(activity)

                verify(observable).unsubscribe(activity as Subscriber<Any>)
            }
        }

        "with Activity without implementing Subscriber" - {
            val activity = mock<Activity>()

            "onActivityResumed should ignore activity" {
                callbacks.onActivityResumed(activity)

                verifyZeroInteractions(observable)
            }

            "onActivityPaused should ignore activity" {
                callbacks.onActivityPaused(activity)

                verifyZeroInteractions(observable)
            }
        }
    }
}) {
    override fun isInstancePerTest() = true
}