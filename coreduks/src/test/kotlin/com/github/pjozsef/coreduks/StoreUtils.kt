package com.github.pjozsef.coreduks

import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

fun <S,A> CoReduksStore<S,A>.awaitCompletion(context: CoroutineContext = this.scope.coroutineContext){
    runBlocking(context) {  }
}