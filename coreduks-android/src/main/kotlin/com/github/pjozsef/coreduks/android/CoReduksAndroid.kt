package com.github.pjozsef.coreduks.android

import android.app.Application
import com.github.pjozsef.coreduks.Observable

object CoReduksAndroid {
    fun <T> start(application: Application, observable: Observable<T>) {
        application.registerActivityLifecycleCallbacks(CoReduksActivityLifecycleCallbacks(observable))
    }
}