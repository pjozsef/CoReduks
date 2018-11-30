package com.github.pjozsef.coreduks.android

import android.app.Application
import com.github.pjozsef.coreduks.Observable
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec

class CoReduksAndroidTest : FreeSpec({

    "CoReduksAndroid::start" - {
        val mockObservable = mock<Observable<Any>>()
        val mockApplication = mock<Application>()

        CoReduksAndroid.start(mockApplication, mockObservable)

        "should register CoReduksActivityLifecycleCallbacks in Application with given Observable" {
            argumentCaptor<Application.ActivityLifecycleCallbacks> {
                verify(mockApplication).registerActivityLifecycleCallbacks(capture())

                val callbacks = firstValue
                callbacks::class.java shouldBe CoReduksActivityLifecycleCallbacks::class.java

                callbacks as CoReduksActivityLifecycleCallbacks<Any>

                callbacks.observable shouldBe mockObservable
            }
        }
    }
}) {
    override fun isInstancePerTest() = true
}