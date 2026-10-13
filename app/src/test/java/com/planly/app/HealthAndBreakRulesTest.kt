package com.planly.app

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class HealthAndBreakRulesTest {
    private val zone = ZoneId.of("Asia/Colombo")
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDate.of(2026, 10, day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    @Test fun hourlyBreaksAreOptInAndStartOneHourAfterEnabling() {
        val now = at(13, 10)
        assertNull(BreakRules.nextAt(0, now, Preferences(), zone))
        assertEquals(at(13, 11), BreakRules.nextAt(0, now, Preferences(screenBreaks = true), zone))
    }
    @Test fun openingTheAppDoesNotPushAnExistingBreakFurtherAway() {
        val stored = at(13, 11)
        assertEquals(stored, BreakRules.nextAt(stored, at(13, 10, 30), Preferences(screenBreaks = true), zone))
    }
    @Test fun missedBreaksSkipBacklogsRatherThanSendingRepeatedNudges() {
        assertEquals(at(13, 13), BreakRules.nextAt(at(13, 9), at(13, 12), Preferences(screenBreaks = true), zone))
    }
    @Test fun hourlyBreaksDeferToTheEndOfOvernightQuietHours() {
        assertEquals(at(14, 7), BreakRules.nextAt(0, at(13, 23), Preferences(screenBreaks = true, quietHours = true), zone))
    }
    @Test fun quietHourBoundaryAndDisablingAreHandled() {
        val p = Preferences(screenBreaks = true, quietHours = true)
        assertEquals(at(14, 7), BreakRules.nextAt(at(14, 7), at(14, 6, 30), p, zone))
        assertNull(BreakRules.nextAt(at(14, 7), at(14, 6, 30), p.copy(screenBreaks = false), zone))
    }
    @Test fun aiSettingsRequireAnHttpsOriginWithoutEndpointPaths() {
        assertTrue(AiRules.validEndpoint("https://planly-ai.onrender.com"))
        assertTrue(AiRules.validEndpoint(" https://example.com/ "))
        assertFalse(AiRules.validEndpoint("http://example.com"))
        assertFalse(AiRules.validEndpoint("https://example.com/health"))
        assertFalse(AiRules.validEndpoint("https://example.com/api/plan"))
    }
    @Test fun aiSettingsRejectCredentialsQueryFragmentsAndMalformedUris() {
        listOf("https://user:secret@example.com", "https://example.com?key=x", "https://example.com#secret", "https://", "not a URL").forEach {
            assertFalse(AiRules.validEndpoint(it))
        }
    }
}
