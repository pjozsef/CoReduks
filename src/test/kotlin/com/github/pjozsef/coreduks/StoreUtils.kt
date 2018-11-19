package com.github.pjozsef.coreduks

import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

fun <T> CoReduksStore<T>.awaitCompletion(context: CoroutineContext = this.scope.coroutineContext){
    runBlocking(context) {  }
}