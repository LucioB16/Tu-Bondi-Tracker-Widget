package com.tubondi.widget.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RouteDestinationTest {
    @Test
    fun takesExplicitFinalTerminalFromObservedRouteText() {
        assertThat(RouteDestination.fromRouteDescription("PARQUE REPUBLICA A PATRICIOS"))
            .isEqualTo("PATRICIOS")
        assertThat(RouteDestination.fromRouteDescription("AV. COLON -> CIUDAD UNIVERSITARIA"))
            .isEqualTo("CIUDAD UNIVERSITARIA")
    }

    @Test
    fun doesNotGuessWhenRouteHasNoRecognizedDirectionSeparator() {
        assertThat(RouteDestination.fromRouteDescription("CENTRO" )).isNull()
        assertThat(RouteDestination.fromRouteDescription(null)).isNull()
        assertThat(RouteDestination.fromRouteDescription(" ")).isNull()
    }
}
