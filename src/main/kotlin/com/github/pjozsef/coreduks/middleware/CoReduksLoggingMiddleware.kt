package com.github.pjozsef.coreduks.middleware

import com.github.pjozsef.coreduks.Middleware
import com.github.pjozsef.coreduks.Store

class CoReduksLoggingMiddleware<S, A>: Middleware<S, A> {
    override fun invoke(store: Store<S, A>, action: A, next: (A) -> S): S {
        println("CoReduksLogginMiddleware: old state: ${store.state}")
        println("CoReduksLogginMiddleware: action: $action")
        val newState = next(action)
        println("CoReduksLogginMiddleware: new state: $newState")
        return newState
    }
}