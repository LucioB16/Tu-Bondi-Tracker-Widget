package com.tubondi.widget

import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.domain.friendlyErrorMessage
import java.io.IOException
import org.junit.Test

class ErrorMessagesTest {

    @Test
    fun mapsKnownFailuresToGenericCopy() {
        assertThat(friendlyErrorMessage(IOException("Session expired (408)"))).isEqualTo("Sin servicio ahora")
        assertThat(friendlyErrorMessage(IOException("unexpected end of stream on https://backend/")))
            .isEqualTo("Sin conexión con TuBondi")
        assertThat(friendlyErrorMessage(IllegalStateException("boom"))).isEqualTo("No se pudo actualizar")
    }
}
