package com.gamut.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {
    @Test
    fun normalizesAccentsAndReleaseNoise() {
        assertEquals("beyonce halo", TextMatch.norm("Beyonce - Halo (Official Video)"))
    }

    @Test
    fun extractsPrimaryArtistBeforeFeatureCredit() {
        assertEquals("taylor swift", TextMatch.primaryArtist("Taylor Swift feat. Ed Sheeran"))
    }

    @Test
    fun identifiesDerivativeTerms() {
        assertTrue("remix" in TextMatch.toks("Love Story (Taylor's Version) Remix"))
    }
}
