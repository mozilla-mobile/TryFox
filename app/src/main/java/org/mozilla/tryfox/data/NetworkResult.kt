package org.mozilla.tryfox.data

/**
 * A sealed class representing the result of a network operation, which can either be a success or an error.
 * @param T The type of data expected in a successful result.
 */
sealed class NetworkResult<out T> {
    /**
     * Represents a successful network operation.
     * @param data The data returned by the successful operation.
     * @param T The type of the data.
     */
    data class Success<out T>(
        val data: T,
    ) : NetworkResult<T>()

    /**
     * Represents a failed network operation.
     * @property message A message describing the error.
     * @property cause An optional [Exception] that caused the error.
     */
    data class Error(
        val message: String,
        val cause: Exception? = null,
    ) : NetworkResult<Nothing>()
}
