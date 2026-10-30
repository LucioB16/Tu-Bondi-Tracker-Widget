package com.tubondi.widget.data.catalog

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
class CatalogRepositorySearchTest {
    @Test
    fun fullOfflineCatalogSearchBuildsItsIndexOnceAndHandlesRepeatedQueries() {
        val repository = CatalogRepository(ApplicationProvider.getApplicationContext())
        val catalog = repository.load()
        val queries = listOf("duarte", "quirós", "centro", "alberdi", "hipólito")
        var lastResults = emptyList<CatalogStop>()

        val elapsedNanos = measureNanoTime {
            repeat(20) { index ->
                lastResults = repository.search(queries[index % queries.size])
            }
        }
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos)

        assertThat(catalog.stops.size).isAtLeast(5_000)
        assertThat(lastResults).isNotEmpty()
        assertThat(lastResults.size).isAtMost(CatalogRepository.SEARCH_LIMIT)
        assertThat(elapsedMillis).isLessThan(2_000L)
        println("Catalog search benchmark: ${catalog.stops.size} stops, 20 queries in ${elapsedMillis}ms")
    }
}
