package com.tubondi.widget.data.catalog

import android.content.Context
import com.tubondi.widget.domain.model.Stop
import com.tubondi.widget.domain.displayName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catálogo offline de paradas (D4, fallback): asset `tubondi-catalogo.json`
 * con 5050 paradas + 24 hubs (regenerado por el cosechador de Oma; el asset
 * se refresca con cada release). Sin red: alimenta el buscador (RF-8/SA-10).
 */
@Serializable
data class CatalogStop(
    val code: String,
    val name: String,
    val hood: String? = null,
    val n: Int? = null
) {
    fun displayName(): String = CatalogRepository.displayName(this)

    fun displayHood(): String? = CatalogRepository.displayHood(this)
}

@Serializable
data class StopCatalog(
    val updated: String? = null,
    val stops: List<CatalogStop> = emptyList(),
    val hubs: List<String> = emptyList()
)

@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: StopCatalog? = null

    @Volatile
    private var cachedSearchIndex: List<SearchEntry>? = null

    /** Catálogo completo (parseado una vez por proceso; asset siempre presente). */
    fun load(): StopCatalog = cached ?: run {
        val parsed = json.decodeFromString(
            StopCatalog.serializer(),
            context.assets.open(CATALOG_ASSET).bufferedReader().readText()
        )
        cached = parsed
        parsed
    }

    /**
     * Buscador por nombre/barrio, sin consultar ni mostrar códigos internos;
     * hasta 25 resultados. Query vacía = sin resultados.
     */
    fun search(query: String, limit: Int = SEARCH_LIMIT): List<CatalogStop> {
        if (query.isBlank()) return emptyList()
        val q = normalizeForSearch(query)
        if (q.isBlank()) return emptyList()
        return searchIndex().asSequence()
            .filter { it.normalizedName.contains(q) || it.normalizedHood.contains(q) }
            .take(limit)
            .map(SearchEntry::stop)
            .toList()
    }

    fun hubs(): List<String> = load().hubs

    /** Hub IDs stay internal; consumers render the descriptive catalog record. */
    fun hubStops(): List<CatalogStop> {
        val byCode = load().stops.associateBy { it.code }
        return load().hubs.map { code ->
            byCode[code] ?: CatalogStop(code = code, name = "")
        }
    }

    fun toStop(catalogStop: CatalogStop): Stop = Stop(
        code = catalogStop.code,
        name = catalogDisplayName(catalogStop),
        latitude = null,
        longitude = null,
        lines = emptyList(),
        hood = catalogStop.hood?.let(::formatPlaceName)
    )

    fun resolveStop(stop: Stop): Stop {
        val catalogStop = load().stops.firstOrNull { it.code == stop.code } ?: return stop
        val catalog = toStop(catalogStop)
        return stop.copy(
            name = catalog.name.ifBlank { stop.name },
            hood = catalog.hood ?: stop.hood,
            customName = stop.customName,
            lines = stop.lines.ifEmpty { catalog.lines }
        )
    }

    private fun searchIndex(): List<SearchEntry> = cachedSearchIndex ?: synchronized(this) {
        cachedSearchIndex ?: load().stops.map { stop ->
            SearchEntry(
                stop = stop,
                normalizedName = normalizeForSearch(stop.name),
                normalizedHood = normalizeForSearch(stop.hood.orEmpty())
            )
        }.also { cachedSearchIndex = it }
    }

    private data class SearchEntry(
        val stop: CatalogStop,
        val normalizedName: String,
        val normalizedHood: String
    )

    companion object {
        const val CATALOG_ASSET = "tubondi-catalogo.json"
        const val SEARCH_LIMIT = 25

        fun displayName(catalogStop: CatalogStop): String = Stop(
            code = catalogStop.code,
            name = catalogDisplayName(catalogStop),
            hood = displayHood(catalogStop)
        ).displayName()

        fun displayHood(catalogStop: CatalogStop): String? = catalogStop.hood?.let(::formatPlaceName)

        private fun catalogDisplayName(catalogStop: CatalogStop): String {
            val name = formatPlaceName(catalogStop.name)
            val incompleteQualifier = name.contains('(') && !name.contains(')')
            // The offline source truncates some labels at its field limit. For
            // example, "... de la Torr" has a recognized neighborhood but no
            // complete street name, so fall back to that location instead.
            val incompleteStreet = name.endsWith("Torr", ignoreCase = true)
            return if (incompleteQualifier || incompleteStreet) "" else name
        }

        private val locale = Locale("es", "AR")
        private val word = Regex("[\\p{L}]+")

        private fun normalizeForSearch(value: String): String = Normalizer
            .normalize(value.lowercase(locale), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

        private fun formatPlaceName(value: String): String {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return ""
            val lower = trimmed.lowercase(locale)
                .replace("quiros", "quirós")
                .replace("hipolito", "hipólito")
                .replace("cordoba", "córdoba")
                .replace("lopez", "lópez")
                .replace("republica", "república")
                .replace("pediatrico", "pediátrico")
                .replace("maipu", "maipú")
            val titleCase = word.replace(lower) { match ->
                match.value.replaceFirstChar { it.titlecase(locale) }
            }
            return titleCase.replace(Regex("(?<=\\d)-(?=\\d)"), "–")
        }
    }
}
