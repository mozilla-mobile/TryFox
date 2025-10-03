package org.mozilla.tryfox.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mozilla.tryfox.model.ParsedNightlyApk
import org.mozilla.tryfox.network.ApiService
import retrofit2.HttpException
import retrofit2.Response

@ExperimentalCoroutinesApi
@ExtendWith(MockitoExtension::class)
class MozillaArchiveRepositoryImplTest {

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private companion object {
        private const val YEAR = "2023"
        private const val MONTH = "10"
        private const val FENIX = "fenix"
        private const val FOCUS = "focus"
        private const val DATE = "$YEAR-$MONTH-01-10-00-00"
    }

    @Mock
    private lateinit var mockApiService: ApiService

    private lateinit var repository: MozillaArchiveRepositoryImpl

    // Test constants for Fenix
    private val fenixVersion = "125.0a1"
    private val fenixFileName1 = "$FENIX-$fenixVersion.multi.android-arm64-v8a.apk"
    private val fenixDirString1 = "$DATE-$FENIX-$fenixVersion-android-arm64-v8a/"
    private val fenixFullUrl1 = "https://archive.mozilla.org/pub/fenix/nightly/$YEAR/$MONTH/$fenixDirString1$fenixFileName1"

    private val fenixFileName2 = "$FENIX-$fenixVersion.multi.android-x86_64.apk"
    private val fenixDirString2 = "$DATE-$FENIX-$fenixVersion-android-x86_64/"
    private val fenixFullUrl2 = "https://archive.mozilla.org/pub/fenix/nightly/$YEAR/$MONTH/$fenixDirString2$fenixFileName2"

    // Test constants for Focus
    private val focusVersion = "110.0a1"
    private val focusAbi = "armeabi-v7a"
    private val focusFileName = "$FOCUS-$focusVersion.multi.android-$focusAbi.apk"
    private val focusDirString = "$DATE-$FOCUS-$focusVersion-android-$focusAbi/"
    private val focusFullUrl = "https://archive.mozilla.org/pub/focus/nightly/$YEAR/$MONTH/$focusDirString$focusFileName"

    @BeforeEach
    fun setUp() {
        val testDate = LocalDate(2023, 10, 1)
        val clock = FixedClock(testDate.atStartOfDayIn(TimeZone.UTC))
        repository = MozillaArchiveRepositoryImpl(mockApiService, clock)
    }

    private fun createMockHtmlResponse(vararg dirStrings: String): String {
        return dirStrings.joinToString(separator = "\n") {
            "<td>Dir</td><td><a href=\"$it\">$it</a></td>"
        }
    }

    @Test
    fun `getFenixNightlyBuilds success - single latest build`() = runTest {
        val mockHtml = createMockHtmlResponse(fenixDirString1)
        val expectedUrl = "https://archive.mozilla.org/pub/fenix/nightly/2023/10/"
        whenever(mockApiService.getHtmlPage(eq(expectedUrl))).thenReturn(mockHtml)

        val result = repository.getFenixNightlyBuilds()

        assertTrue(result is NetworkResult.Success)
        val apks = (result as NetworkResult.Success).data
        assertEquals(1, apks.size)
        val apk = apks[0]
        assertEquals(fenixDirString1, apk.originalString)
        assertEquals(DATE, apk.rawDateString)
        assertEquals(FENIX, apk.appName)
        assertEquals(fenixVersion, apk.version)
        assertEquals("arm64-v8a", apk.abiName)
        assertEquals(fenixFullUrl1, apk.fullUrl)
        assertEquals(fenixFileName1, apk.fileName)
    }

    @Test
    fun `getFenixNightlyBuilds success - multiple builds on latest date`() = runTest {
        val olderDateDir = "2023-9-31-00-00-00-$FENIX-$fenixVersion-android-arm64-v8a/"
        val mockHtml = createMockHtmlResponse(fenixDirString1, fenixDirString2, olderDateDir)
        val expectedUrl = "https://archive.mozilla.org/pub/fenix/nightly/2023/10/"
        whenever(mockApiService.getHtmlPage(eq(expectedUrl))).thenReturn(mockHtml)

        val result = repository.getFenixNightlyBuilds()

        assertTrue(result is NetworkResult.Success)
        val apks = (result as NetworkResult.Success).data
        assertEquals(2, apks.size)

        val expectedApks = listOf(
            ParsedNightlyApk(fenixDirString1, DATE, FENIX, fenixVersion, "arm64-v8a", fenixFullUrl1, fenixFileName1),
            ParsedNightlyApk(fenixDirString2, DATE, FENIX, fenixVersion, "x86_64", fenixFullUrl2, fenixFileName2)
        )
        assertEquals(expectedApks.first(), apks.first())
        assertTrue(apks.containsAll(expectedApks) && expectedApks.containsAll(apks))
    }

     @Test
    fun `getFenixNightlyBuilds success - sorts by date correctly`() = runTest {
        val olderDateDir = "2023-09-31-23-59-59-$FENIX-$fenixVersion-android-arm64-v8a/"
        val middleDateDir = fenixDirString1
        val latestDateDir = "2023-11-01-09-00-00-$FENIX-$fenixVersion-android-x86/"

        val mockHtml = createMockHtmlResponse(olderDateDir, latestDateDir, middleDateDir)
        val expectedUrl = "https://archive.mozilla.org/pub/fenix/nightly/2023/10/"
        whenever(mockApiService.getHtmlPage(eq(expectedUrl))).thenReturn(mockHtml)

        val result = repository.getFenixNightlyBuilds()
        assertTrue(result is NetworkResult.Success)
        val apks = (result as NetworkResult.Success).data
        assertEquals(1, apks.size)
        assertEquals(latestDateDir, apks[0].originalString)
    }

    @Test
    fun `getFenixNightlyBuilds success - no builds found`() = runTest {
        val mockHtml = "<td>Some other HTML</td>"
        val expectedUrl = "https://archive.mozilla.org/pub/fenix/nightly/2023/10/"
        whenever(mockApiService.getHtmlPage(eq(expectedUrl))).thenReturn(mockHtml)

        val result = repository.getFenixNightlyBuilds()

        assertTrue(result is NetworkResult.Success)
        assertTrue((result as NetworkResult.Success).data.isEmpty())
    }

    @Test
    fun `getFenixNightlyBuilds network error`() = runTest {
        val errorMessage = "Network error"
        val expectedUrl = "https://archive.mozilla.org/pub/fenix/nightly/2023/10/"
        whenever(mockApiService.getHtmlPage(eq(expectedUrl))).thenThrow(RuntimeException(errorMessage))

        val result = repository.getFenixNightlyBuilds()

        assertTrue(result is NetworkResult.Error)
        assertEquals("Failed to fetch or parse fenix builds: $errorMessage", (result as NetworkResult.Error).message)
    }

    @Test
    fun `getFenixNightlyBuilds when current month 404 queries previous month`() = runTest {
        // Given
        val testDate = LocalDate(2024, 3, 15)
        val clock = FixedClock(testDate.atStartOfDayIn(TimeZone.UTC))
        val repository = MozillaArchiveRepositoryImpl(mockApiService, clock)

        val currentMonthUrl = MozillaArchiveRepositoryImpl.archiveUrlForDate(FENIX, testDate)

        val previousMonthDate = testDate.minus(1, DateTimeUnit.MONTH)
        val previousMonthUrl = MozillaArchiveRepositoryImpl.archiveUrlForDate(FENIX, previousMonthDate)

        val previousMonthDateString = "2024-02-28-10-00-00"
        val previousMonthDirString = "$previousMonthDateString-$FENIX-$fenixVersion-android-arm64-v8a/"
        val previousMonthMockHtml = createMockHtmlResponse(previousMonthDirString)

        whenever(mockApiService.getHtmlPage(currentMonthUrl)).thenThrow(
            HttpException(Response.error<Any>(404, "".toResponseBody(null)))
        )
        whenever(mockApiService.getHtmlPage(previousMonthUrl)).thenReturn(previousMonthMockHtml)

        // When
        val result = repository.getFenixNightlyBuilds()

        // Then
        assertTrue(result is NetworkResult.Success)
        val apks = (result as NetworkResult.Success).data
        assertEquals(1, apks.size)
        assertEquals(previousMonthDirString, apks[0].originalString)
    }

    @Test
    fun `getFocusNightlyBuilds success - single latest build`() = runTest {
        val mockHtml = createMockHtmlResponse(focusDirString)
        val expectedUrl = "https://archive.mozilla.org/pub/focus/nightly/2023/10/"
        whenever(mockApiService.getHtmlPage(eq(expectedUrl))).thenReturn(mockHtml)

        val result = repository.getFocusNightlyBuilds()

        assertTrue(result is NetworkResult.Success)
        val apks = (result as NetworkResult.Success).data
        assertEquals(1, apks.size)
        val apk = apks[0]
        assertEquals(focusDirString, apk.originalString)
        assertEquals(DATE, apk.rawDateString)
        assertEquals(FOCUS, apk.appName)
        assertEquals(focusVersion, apk.version)
        assertEquals(focusAbi, apk.abiName)
        assertEquals(focusFullUrl, apk.fullUrl)
        assertEquals(focusFileName, apk.fileName)
    }

    @Test
    fun `getFocusNightlyBuilds network error`() = runTest {
        val errorMessage = "Network error for Focus"
        val expectedUrl = "https://archive.mozilla.org/pub/focus/nightly/2023/10/"
        whenever(mockApiService.getHtmlPage(eq(expectedUrl))).thenThrow(RuntimeException(errorMessage))

        val result = repository.getFocusNightlyBuilds()

        assertTrue(result is NetworkResult.Error)
        assertEquals("Failed to fetch or parse focus builds: $errorMessage", (result as NetworkResult.Error).message)
    }
}
