package com.github.pjozsef.coreduks

fun Any.setProperty(fieldName: String, value: Any) {
    this::class.java.getDeclaredField(fieldName).also { field ->
        field.isAccessible = true
    }.set(this, value)
}