package org.movieos.bluereader.utilities

import timber.log.Timber

fun <T> List<T>.sliceSafely(start: Int, end: Int): List<T> {
    return subList(Math.min(start, size), Math.min(end, size))
}

public inline fun <T> measureTimeMillis(message: String, block: () -> T): T {
    val start = System.currentTimeMillis()
    val result = block()
    val duration = System.currentTimeMillis() - start
    Timber.i("Timed $message at $duration ms")
    return result
}
