package org.mozilla.tryfox.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchQueryClassifierTest {
    @Test fun `classifies trimmed email`() {
        assertEquals(SearchQuery.Email("person+try@mozilla.org"), SearchQueryClassifier.classify(" person+try@mozilla.org ").getOrThrow())
    }

    @Test fun `classifies revision`() {
        assertEquals(SearchQuery.Revision("abc123"), SearchQueryClassifier.classify(" abc123 ").getOrThrow())
    }

    @Test fun `rejects blank and malformed at input`() {
        assertTrue(SearchQueryClassifier.classify(" ").isFailure)
        assertTrue(SearchQueryClassifier.classify("person@mozilla").isFailure)
    }
}
