package com.tubondi.widget.domain

import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.domain.model.Stop
import org.junit.Test

class StopDisplayTest {
    @Test
    fun usesDescriptiveNameAndNeverDisplaysTheInternalId() {
        assertThat(Stop(code = "A911", name = "Duarte Quirós 1717–1728").displayName())
            .isEqualTo("Duarte Quirós 1717–1728")
        assertThat(Stop(code = "A911", name = "A911", hood = "Alberdi").displayName())
            .isEqualTo("Parada en Alberdi")
        assertThat(Stop(code = "A911", name = "Parada A911").displayName())
            .isEqualTo("Ubicación sin nombre")
    }

    @Test
    fun customNameOverridesFallbackButCodeShapedNamesAreRejected() {
        val stop = Stop(code = "A911", name = "", customName = "Esquina del mercado")
        assertThat(stop.displayName()).isEqualTo("Esquina del mercado")
        assertThat(isDescriptiveStopName("Duarte Quirós 1717–1728", "A911")).isTrue()
        assertThat(isDescriptiveStopName("A911", "A911")).isFalse()
        assertThat(isDescriptiveStopName("Parada A911", "A911")).isFalse()
    }

    @Test
    fun removesOnlyTheCurrentStopCodeAndKeepsPlaceNamesThatLookAlphanumeric() {
        assertThat(Stop(code = "A911", name = "Parada A911").displayName())
            .isEqualTo("Ubicación sin nombre")
        assertThat(Stop(code = "UE03", name = "UES21", hood = "Centro").displayName())
            .isEqualTo("UES21")
        assertThat(Stop(code = "A911", name = "E53 y Duarte Quirós").displayName())
            .isEqualTo("E53 y Duarte Quirós")
        assertThat(isDescriptiveStopName("Parada A911", "A911")).isFalse()
    }
}
