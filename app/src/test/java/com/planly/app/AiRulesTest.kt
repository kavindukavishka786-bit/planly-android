package com.planly.app

import org.junit.Assert.*
import org.junit.Test

class AiRulesTest {
    @Test fun rejectsMissingAndShortTokensBeforeNetworkAccess() {
        assertNotNull(AiRules.validateToken(""))
        assertNotNull(AiRules.validateToken("short-token"))
        assertNotNull(AiRules.validateToken("a".repeat(31)))
    }
    @Test fun acceptsServerTokenLengthBoundaries() {
        assertNull(AiRules.validateToken("a".repeat(32)))
        assertNull(AiRules.validateToken("b".repeat(512)))
        assertNotNull(AiRules.validateToken("c".repeat(513)))
    }
    @Test fun rejectsWhitespaceAndUnsupportedHeaderCharacters() {
        for (suffix in listOf(" ", "\n", "\r", "\t", "é")) {
            assertNotNull(AiRules.validateToken("a".repeat(32) + suffix))
        }
    }
}
