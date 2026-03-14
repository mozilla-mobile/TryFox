package org.mozilla.tryfox

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDeepLinkParserInstrumentedTest {

    @Test
    fun parse_preservesEncodedPlusInFragmentAuthorOnAndroidRuntime() {
        val uri = Uri.parse("https://treeherder.mozilla.org/#/jobs?author=try%2Buser%40mozilla.com")

        val destination = AppDeepLinkParser.parse(uri)

        assertEquals(
            AppDeepLinkDestination.Profile(email = "try+user@mozilla.com"),
            destination,
        )
    }
}
