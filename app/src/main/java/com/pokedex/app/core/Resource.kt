package com.pokedex.app.core

/**
 * Wraps a value that is loaded from cache and/or network. [data] may be present
 * even while [Loading] (stale-while-revalidate) or on [Error] (show cache + a
 * retry affordance).
 */
sealed interface Resource<out T> {
    val data: T?

    data class Loading<out T>(override val data: T? = null) : Resource<T>
    data class Success<out T>(override val data: T) : Resource<T>
    data class Error<out T>(
        val message: String,
        val cause: Throwable? = null,
        override val data: T? = null,
    ) : Resource<T>
}
