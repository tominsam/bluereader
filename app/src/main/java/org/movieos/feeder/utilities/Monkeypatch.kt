package org.movieos.feeder.utilities

fun <T> List<T>.sliceSafely(start: Int, end: Int): List<T> {
    return subList(Math.min(start, size), Math.min(end, size))
}
