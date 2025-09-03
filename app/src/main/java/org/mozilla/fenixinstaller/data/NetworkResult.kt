package org.mozilla.fenixinstaller.data

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val cause: Exception? = null) : NetworkResult<Nothing>()
}
