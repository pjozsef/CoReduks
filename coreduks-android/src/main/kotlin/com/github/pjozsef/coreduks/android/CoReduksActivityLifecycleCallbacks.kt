package com.github.pjozsef.coreduks.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.github.pjozsef.coreduks.Observable
import com.github.pjozsef.coreduks.Subscriber

class CoReduksActivityLifecycleCallbacks<T>(val observable: Observable<T>) : Application.ActivityLifecycleCallbacks {

    override fun onActivityResumed(activity: Activity) {
        activity.takeIf { it is Subscriber<*> }?.let {
            observable.subscribe(it as Subscriber<T>)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        activity.takeIf { it is Subscriber<*> }?.let {
            observable.unsubscribe(it as Subscriber<T>)
        }
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, p1: Bundle?) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivityCreated(activity: Activity, p1: Bundle?) {
    }
}