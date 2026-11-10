package de.rpicloud.ipv64net.main.views.security

import org.junit.Assert.assertEquals
import org.junit.Test

class CountryFlagTest {
    @Test
    fun codesBecomeFlags() {
        assertEquals("🇩🇪", "DE".countryFlag())
        assertEquals("🇫🇷", "fr".countryFlag())
    }

    @Test
    fun otherValuesStay() {
        assertEquals("??", "??".countryFlag())
        assertEquals("DEU", "DEU".countryFlag())
        assertEquals("", "".countryFlag())
    }
}
