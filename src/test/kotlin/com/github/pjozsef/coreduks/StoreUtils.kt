package com.github.pjozsef.coreduks

import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

fun <T> Store<T>.awaitCompletion(context: CoroutineContext = this.scope.coroutineContext){
    runBlocking(context) {  }
}