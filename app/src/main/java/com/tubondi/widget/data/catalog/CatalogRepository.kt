package com.tubondi.widget.data.catalog

import android.content.Context
import com.tubondi.widget.domain.model.Stop
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
)

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
     * Buscador (paridad Oma widget.owl:44): code/name/barrio case-insensitive,
     * hasta 25 resultados. Query vacía = sin resultados.
     */
    fun search(query: String, limit: Int = SEARCH_LIMIT): List<CatalogStop> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        return load().stops.filter {
            it.code.lowercase().contains(q) ||
                it.name.lowercase().contains(q) ||
                (it.hood?.lowercase()?.contains(q) ?: false)
        }.take(limit)
    }

    fun hubs(): List<String> = load().hubs

    fun toStop(catalogStop: CatalogStop): Stop = Stop(
        code = catalogStop.code,
        name = catalogStop.name,
        latitude = null,
        longitude = null,
        lines = emptyList()
    )

    companion object {
        const val CATALOG_ASSET = "tubondi-catalogo.json"
        const val SEARCH_LIMIT = 25
    }
}
