package org.mozilla.fenixinstaller.network

/**
 * Annotation to indicate that for this Retrofit request, OkHttp logging should be disabled.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class DisableLogs