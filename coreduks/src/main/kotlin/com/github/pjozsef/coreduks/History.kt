package com.github.pjozsef.coreduks

interface History<T> {
    val past: List<T>
    val current: T
    val future: List<T>

    val ignoreFirstValue: Boolean
        get() = false

    operator fun plus(other: T): History<T>
    fun newValue(other: T): History<T>

    operator fun unaryMinus(): History<T>
    fun undo(): History<T>

    operator fun unaryPlus(): History<T>
    fun redo(): History<T>
}

data class CoReduksHistory<T>(
        override val current: T,
        override val past: List<T> = emptyList(),
        override val future: List<T> = emptyList(),
        override val ignoreFirstValue: Boolean = false) : History<T> {

    override fun newValue(other: T) = this.copy(
            current = other,
            past = if(ignoreFirstValue) past else past + current,
            future = emptyList(),
            ignoreFirstValue = false
    )

    override fun plus(other: T) = this.newValue(other)

    override fun undo(): History<T> =
            if (past.isEmpty()) {
                this
            } else {
                this.copy(
                        current = past.last(),
                        past = past.dropLast(1),
                        future = listOf(current) + future
                )
            }

    override fun unaryMinus(): History<T> = this.undo()

    override fun redo(): History<T> =
            if (future.isEmpty()) {
                this
            } else {
                this.copy(
                        current = future.first(),
                        past = past + current,
                        future = future.drop(1)
                )
            }

    override fun unaryPlus(): History<T> = this.redo()
}