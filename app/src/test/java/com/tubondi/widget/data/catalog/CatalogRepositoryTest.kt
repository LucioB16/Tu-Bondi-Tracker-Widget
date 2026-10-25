package com.tubondi.widget.data.catalog

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
    fun searchMatchesCodeNameAndHood() {
        val results = repository.search("alberdi")
        assertThat(results).isNotEmpty()
        assertThat(results.size).isAtMost(CatalogRepository.SEARCH_LIMIT)
    }

    @Test
    fun searchByCodeMatchesExactly() {
        val results = repository.search("AA50")
        assertThat(results).isNotEmpty()
        assertThat(results.first().code).isEqualTo("AA50")
    }

    @Test
    fun searchBlankReturnsEmpty() {
        assertThat(repository.search("")).isEmpty()
        assertThat(repository.search("   ")).isEmpty()
    }
}
