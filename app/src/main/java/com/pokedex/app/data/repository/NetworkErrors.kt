package com.pokedex.app.data.repository

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Turns a networking exception into a short, user-facing message. */
fun Throwable.toUserMessage(): String = when (this) {
    is UnknownHostException -> "No internet connection."
    is SocketTimeoutException -> "The request timed out. Try again."
    is HttpException -> when (code()) {
        404 -> "Not found."
        in 500..599 -> "PokéAPI is having trouble right now. Try again shortly."
        else -> "Network error (${code()})."
    }
    is IOException -> "Network error. Check your connection and try again."
    else -> message ?: "Something went wrong."
}
