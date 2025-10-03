package org.mozilla.tryfox.network

/**
 * Annotation to indicate that for this Retrofit request, OkHttp logging should be disabled.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class DisableLogs
