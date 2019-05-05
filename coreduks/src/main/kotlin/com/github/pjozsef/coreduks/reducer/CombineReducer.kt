package com.github.pjozsef.coreduks.reducer

import com.github.pjozsef.coreduks.Reducer
import java.lang.IllegalArgumentException
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.reflect

inline fun <reified S : Any, A> combineReducers(setup: CombinedReducer.SliceCollector<S, A>.() -> Unit): Reducer<S, A> =
        CombinedReducer.Builder<S, A>()
                .apply(setup)
                .constructor(S::class.primaryConstructor
                        ?: throw IllegalArgumentException("Primary constructor must not be null"))
                .build()

class CombinedReducer<S, A>(
        private val constructor: KFunction<S>,
        private val slices: List<Pair<KProperty1<S, *>, PartialReducer<S, A, *>>>) : Reducer<S, A> {

    class Builder<S, A> : SliceCollector<S, A> {
        private val slices: MutableList<Pair<
                KProperty1<S, *>,
                PartialReducer<S, A, *>>> = mutableListOf()

        private lateinit var constructor: KFunction<S>

        override fun slice(property: KProperty1<S, *>, action: (S, A) -> Any) {
            val lambdaReturnType = action.reflect()?.returnType
            val propertyReturnType = property.returnType

            require(propertyReturnType == lambdaReturnType) {
                "Types did not match: property: $propertyReturnType, lambda: $lambdaReturnType"
            }

            val partialReducer = object : PartialReducer<S, A, Any> {
                override fun invoke(currentState: S, action: A): Any {
                    return action(currentState, action)
                }
            }
            slices.add(property to partialReducer)
        }

        fun constructor(constructor: KFunction<S>): Builder<S, A> {
            this.constructor = constructor
            return this
        }

        fun build(): CombinedReducer<S, A> {
            validate()
            return CombinedReducer(constructor, slices)
        }

        private fun validate() {
            val declaredParameters = slices.map { (property, _) ->
                property.name to property.returnType
            }
            val constructorArguments = constructor.let {
                it.parameters.map {
                    it.name to it.type
                }
            }
            require(declaredParameters.toSet() == constructorArguments.toSet()) {
                "Expected $constructorArguments, but got $declaredParameters"
            }
        }

    }

    interface SliceCollector<S, A> {
        fun slice(property: KProperty1<S, *>, action: (S, A) -> Any)
    }

    interface PartialReducer<S, A, T : Any> {
        fun invoke(currentState: S, action: A): T
    }

    override fun invoke(currentState: S, action: A): S =
            slices.fold(emptyMap<String, Any>()) { map, (property, partialReducer) ->
                map + mapOf(property.name to partialReducer.invoke(currentState, action))
            }.run {
                constructor.parameters.associate { it to get(it.name) }
            }.let {
                constructor.callBy(it)
            }
}