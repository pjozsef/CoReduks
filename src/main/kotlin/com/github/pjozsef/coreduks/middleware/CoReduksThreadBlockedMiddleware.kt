package com.github.pjozsef.coreduks.middleware

import com.github.pjozsef.coreduks.Middleware
import com.github.pjozsef.coreduks.Store

class CoReduksThreadBlockedMiddleware<S, A>(
        private val threshold: Long,
        private val verbose: Boolean = false,
        private val beyondThresholdAction: ((Long) -> Unit)? = null) : Middleware<S, A> {
    override fun invoke(store: Store<S, A>, action: A, next: (A) -> S): S {
        val start = System.currentTimeMillis()
        val newState = next(action)
        val duration = System.currentTimeMillis() - start
        if (duration > threshold) {
            beyondThresholdAction?.invoke(duration) ?: kotlin.run {
                System.err.println("CoReduks thread blocked for $duration millis!")
            }
        } else if (verbose) {
            println("Action handling took $duration millis.")
        }
        return newState
    }
}