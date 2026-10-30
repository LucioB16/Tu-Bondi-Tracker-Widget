package com.tubondi.widget.ui.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapStopPayloadTest {
    @Test
    fun acceptsOnlyFiniteCoordinatesWithinLatitudeAndLongitudeRanges() {
        assertThat(MapStopPayload("AA01", "Centro", -31.4, -64.2).hasValidCoordinates()).isTrue()
        assertThat(MapStopPayload("AA02", "Sin coordenadas").hasValidCoordinates()).isFalse()
        assertThat(MapStopPayload("AA03", "Fuera de latitud", 91.0, -64.2).hasValidCoordinates()).isFalse()
        assertThat(MapStopPayload("AA04", "Fuera de longitud", -31.4, 181.0).hasValidCoordinates()).isFalse()
        assertThat(MapStopPayload("AA05", "No finitas", Double.NaN, -64.2).hasValidCoordinates()).isFalse()
    }
}
