# Proveedor de mapa para “Paradas en mapa”

**Revisión:** 2026-10-30 · **Estado:** implementación y validación en emulador completadas; no se usa ubicación del teléfono.

## Datos de ubicación

La respuesta actual de `seleccionatraza` incluye `lat`/`lon` como cadenas numéricas por parada. La consulta de muestra de este día devolvió 27 puntos para una ruta. El catálogo offline de búsqueda no trae coordenadas. Por eso el mapa solo coloca puntos que tengan coordenadas finitas y dentro de los rangos geográficos válidos; el resto sigue disponible en la lista con el nombre de parada y una indicación de ubicación faltante. No se geocodifican calles ni se inventan coordenadas.

## Opciones consideradas

- **MapLibre Native Android + OpenFreeMap:** MapLibre Native Android es BSD-2-Clause. OpenFreeMap declara uso comercial permitido y servicio público gratuito, sin cuenta, clave ni cuotas publicadas por vistas o solicitudes; su ToS lo ofrece “as-is”, sin garantía de disponibilidad, y permite discontinuarlo. Su política de privacidad (octubre de 2026) dice que no hay cuentas/cookies y que los logs de acceso no guardan IP por defecto; errores pueden retener IP y URL hasta 7 días y eventos de seguridad hasta 30 días, y Cloudflare puede procesar solicitudes. La página pública permite descargas offline de hasta 3.000 tiles o 50 MB, con máximo cuatro solicitudes paralelas y User-Agent propio; Tu Bondi no implementa descargas offline. Es la opción elegida para la vista bajo demanda: la app pide solo los tiles del encuadre y no envía la lista de paradas.
- **MapTiler Cloud:** su página consultada ofrece plan Free para pruebas/uso personal o no comercial, con 5.000 sesiones y 100.000 solicitudes API al mes; al agotar la cuota se pausa. Flex figura a USD 30/mes, con cargos por exceso. Requiere cuenta y clave; no se justifica agregar facturación para esta función.
- **Stadia Maps:** la página consultada ofrece 200.000 créditos mensuales gratuitos para desarrollo, evaluación y uso no comercial; Starter figura a USD 20/mes para uso comercial, con créditos adicionales cobrables. Requiere cuenta/clave para clientes móviles. El free tier no habilita uso comercial.
- **Tiles estándar de OpenStreetMap:** el servicio es mejor esfuerzo, sin SLA; exige identificación de app, atribución, caché y prohíbe uso sin conexión/prefetch. No se elige como endpoint de producción porque OpenFreeMap proporciona un servicio vectorial diseñado para clientes MapLibre y su política permite el uso previsto.

## Decisión y privacidad

Implementar la vista con el SDK oficial `org.maplibre.gl:android-sdk`, estilo público Liberty de OpenFreeMap y atribución de OpenMapTiles/OpenStreetMap visible mediante el control del mapa. Licencia de la biblioteca BSD-2-Clause; los datos se derivan de OpenStreetMap/OpenMapTiles. El proveedor recibe solicitudes de tiles del encuadre visible y necesariamente el origen de red de esas solicitudes; su política dice que no registra IP en los logs de acceso por defecto, pero errores o incidentes pueden retenerla temporalmente, y Cloudflare puede procesar tráfico. No se solicita permiso de ubicación ni se manda la lista de paradas al proveedor. La pantalla mantiene una lista accesible con nombres junto al mapa y un estado de error si la cartografía no carga.

La app no descarga mapa sin conexión ni usa el servidor de tiles de OSMF. OpenFreeMap hoy no publica un precio por vista ni límites públicos de solicitudes, pero tampoco SLA; puede interrumpir o discontinuar el servicio. La lista permanece disponible si el mapa falla. Revaluar proveedor si cambian tráfico, términos, costo o requisito de disponibilidad.

## Implementación y validación Android

La vista usa MapLibre Android `11.0.0`, el estilo Liberty de OpenFreeMap y anotaciones nativas numeradas. Al ajustar la cámara, los puntos se agrupan por celdas de pantalla y los grupos se abren al tocarlos; así la ruta de 79 paradas no deja pines solapados en el encuadre inicial. La pantalla mantiene la lista de paradas al pie. Se probaron fuentes GeoJSON y capas `CircleLayer`, pero el SDK registraba la fuente/capa sin devolver features ni renderizar círculos. MapLibre `13.7.1` fue incompatible con el Kotlin `1.9.23` del proyecto, por lo que se conservó `11.0.0` y se dejó la solución funcional basada en anotaciones.

El 2026-10-30, `:app:assembleDebug` pasó con el emulador apagado y el APK se verificó en `TuBondi_Test_API36` usando solo el stub loopback. Para el fixture de 79 coordenadas se comprobó el agrupamiento, el zoom, la selección de «Carlos Gimenez 90», el cambio a «Quitar» y el retorno al borrador sin guardarlo. El recorrido local 10 carece de coordenadas: el mapa no inventó puntos, mantuvo «Av. Duarte Quirós 1717–1728» y «Colon 545/33» en la lista e indicó «Sin ubicación en el mapa». Una superposición inicial del estado sin coordenadas se corrigió apilando la etiqueta bajo el nombre; el APK recompilado pasó la misma comprobación visual. La prueba unitaria `MapStopPayloadTest` valida coordenadas finitas dentro de rangos geográficos.

Las capturas reales 1080×1920 están en `docs/redesign-implementation-validation/`: `map-manual-clusters-79.png`, `map-manual-cluster-zoom-79.png`, `map-manual-cluster-zoom2-79.png`, `map-manual-single-stop-selected.png`, `config-map-draft-selection.png` y `map-manual-no-coordinates.png`. No hubo compilaciones Gradle mientras QEMU estaba activo o renderizaba el mapa. No se llamaron endpoints de producción ni se guardó la configuración de QA.

## Fuentes consultadas

- [MapLibre Android quickstart](https://maplibre.org/maplibre-native/android/examples/getting-started/) y [licencia Android BSD-2-Clause](https://github.com/maplibre/maplibre-native/blob/main/platform/android/LICENSE.md).
- [OpenFreeMap servicio](https://openfreemap.org/), [privacidad](https://openfreemap.org/privacy/), [términos](https://openfreemap.org/tos/) y [quick start/atribución](https://openfreemap.org/quick_start/).
- [MapTiler precios](https://www.maptiler.com/cloud/pricing/) y [términos de Cloud](https://www.maptiler.com/terms/cloud/).
- [Stadia Maps precios](https://stadiamaps.com/pricing/) y [términos](https://stadiamaps.com/terms-of-service/).
- [Política de tiles de OSMF](https://operations.osmfoundation.org/policies/tiles/).
