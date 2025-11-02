package org.mozilla.tryfox.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VersionUtilsTest {

    @Test
    fun `from with v prefix and no pre-release`() {
        assertEquals(Version("v1.2.3", 1, 2, 3, null), Version.from("v1.2.3"))
    }

    @Test
    fun `from without prefix and no pre-release`() {
        assertEquals(Version("10.0.0", 10, 0, 0, null), Version.from("10.0.0"))
    }

    @Test
    fun `from with pre-release`() {
        assertEquals(Version("1.0.0-alpha1", 1, 0, 0, "alpha1"), Version.from("1.0.0-alpha1"))
    }

    @Test
    fun `from with invalid string`() {
        assertEquals(null, Version.from("invalid"))
    }

    @Test
    fun `version comparison with patch update`() {
        assertTrue(Version.from("1.2.4")!! > Version.from("1.2.3")!!)
    }

    @Test
    fun `version comparison with minor update`() {
        assertTrue(Version.from("1.3.0")!! > Version.from("1.2.3")!!)
    }

    @Test
    fun `version comparison with major update`() {
        assertTrue(Version.from("2.0.0")!! > Version.from("1.2.3")!!)
    }

    @Test
    fun `version comparison with same version`() {
        assertFalse(Version.from("1.2.3")!! > Version.from("1.2.3")!!)
        assertTrue(Version.from("1.2.3")!! >= Version.from("1.2.3")!!)
        assertTrue(Version.from("1.2.3")!! <= Version.from("1.2.3")!!)
    }

    @Test
    fun `version comparison with older patch`() {
        assertTrue(Version.from("1.2.2")!! < Version.from("1.2.3")!!)
    }

    @Test
    fun `version comparison with older minor`() {
        assertTrue(Version.from("1.1.0")!! < Version.from("1.2.3")!!)
    }

    @Test
    fun `version comparison with older major`() {
        assertTrue(Version.from("0.9.9")!! < Version.from("1.2.3")!!)
    }

    @Test
    fun `pre-release comparison alpha to alpha numeric`() {
        assertTrue(Version.from("1.0.0-alpha")!! < Version.from("1.0.0-alpha.1")!!)
    }

    @Test
    fun `pre-release comparison alpha numeric to beta`() {
        assertTrue(Version.from("1.0.0-alpha.1")!! < Version.from("1.0.0-beta")!!)
    }

    @Test
    fun `pre-release comparison beta to beta numeric`() {
        assertTrue(Version.from("1.0.0-beta")!! < Version.from("1.0.0-beta.2")!!)
    }

    @Test
    fun `pre-release comparison beta numeric to beta numeric higher`() {
        assertTrue(Version.from("1.0.0-beta.2")!! < Version.from("1.0.0-beta.11")!!)
    }

    @Test
    fun `pre-release comparison beta numeric to rc`() {
        assertTrue(Version.from("1.0.0-beta.11")!! < Version.from("1.0.0-rc.1")!!)
    }

    @Test
    fun `pre-release comparison rc to stable`() {
        assertTrue(Version.from("1.0.0-rc.1")!! < Version.from("1.0.0")!!)
    }

    @Test
    fun `pre-release comparison same pre-release`() {
        assertFalse(Version.from("1.0.0-alpha")!! > Version.from("1.0.0-alpha")!!)
    }

    @Test
    fun `pre-release comparison different pre-release parts`() {
        assertTrue(Version.from("1.0.0-alpha.beta")!! < Version.from("1.0.0-alpha.gamma")!!)
    }

    @Test
    fun `pre-release comparison numeric vs non-numeric`() {
        assertTrue(Version.from("1.0.0-1")!! < Version.from("1.0.0-alpha")!!)
    }
}
