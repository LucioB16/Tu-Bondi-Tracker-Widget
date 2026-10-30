package com.tubondi.widget.domain

import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.domain.model.Line
import org.junit.Test

class LineColorResolverTest {
    @Test
    fun usesCompositeApiIdentityAndValidatedCatalogColor() {
        val colors = LineColorResolver.canonicalColors(
            listOf(
                Line("42", "42", "#ff9500", clientId = 411),
                Line("42", "22", "#0099CB", clientId = 408)
            )
        )

        assertThat(colors[LineKey(411, "42")]).isEqualTo("#FF9500")
        assertThat(colors[LineKey(408, "42")]).isEqualTo("#0099CB")
        assertThat(colors[LineKey(408, "22")]).isNull()
    }

    @Test
    fun invalidOrConflictingColorsAreNeutral() {
        val colors = LineColorResolver.canonicalColors(
            listOf(
                Line("42", "42", "#ff9500", clientId = 411),
                Line("42", "42", "#fe3434", clientId = 411),
                Line("10", "10", "#fff", clientId = 411),
                Line("11", "11", "orange", clientId = 411)
            )
        )

        assertThat(colors[LineKey(411, "42")]).isNull()
        assertThat(colors[LineKey(411, "10")]).isNull()
        assertThat(colors[LineKey(411, "11")]).isNull()
    }

    @Test
    fun arrivalColorMustAgreeWithTheCanonicalColorWhenPresent() {
        assertThat(LineColorResolver.forArrival("#ff9500", "#FF9500")).isEqualTo("#FF9500")
        assertThat(LineColorResolver.forArrival("#ff9500", null)).isEqualTo("#FF9500")
        assertThat(LineColorResolver.forArrival("#ff9500", "#fe3434")).isNull()
        assertThat(LineColorResolver.forArrival("bad", "#ff9500")).isNull()
    }
}
