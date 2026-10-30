package com.tubondi.widget.data.catalog

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tubondi.widget.domain.displayName
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Valida el catálogo offline (asset tubondi-catalogo.json real: 5050 paradas,
 * 24 hubs) y el buscador RF-8/SA-10.
 */
@RunWith(RobolectricTestRunner::class)
class CatalogRepositoryTest {
    private val repository = CatalogRepository(ApplicationProvider.getApplicationContext())

    @Test
    fun loadsRealAssetCatalog() {
        val catalog = repository.load()
        assertThat(catalog.stops).hasSize(5050)
        assertThat(catalog.hubs).isNotEmpty()
        assertThat(catalog.updated).isEqualTo("2026-10-12")
    }

    @Test
    fun searchMatchesNameAndHoodWithoutSearchingByInternalCode() {
        val results = repository.search("alberdi")
        assertThat(results).isNotEmpty()
        assertThat(results.size).isAtMost(CatalogRepository.SEARCH_LIMIT)
        assertThat(repository.search("AA50")).isEmpty()
    }

    @Test
    fun searchIgnoresAccentDifferencesAndHubNamesAreDescriptive() {
        assertThat(repository.search("duarte quiros 1717")).isNotEmpty()
        val duarte = repository.search("duarte quiros 1717").first { it.code == "A911" }
        assertThat(repository.toStop(duarte).displayName()).isEqualTo("Av. Duarte Quirós 1717–1728")
    }

    @Test
    fun incompleteCatalogLabelsFallBackToKnownNeighborhood() {
        val hubs = repository.hubStops().associateBy { it.code }
        assertThat(hubs.getValue("CU15").displayName()).isEqualTo("Parada en Ciudad Universitaria")
        assertThat(hubs.getValue("NC14").displayName()).isEqualTo("Parada en Nueva Córdoba")
    }

    @Test
    fun searchBlankReturnsEmpty() {
        assertThat(repository.search("")).isEmpty()
        assertThat(repository.search("   ")).isEmpty()
    }
}
