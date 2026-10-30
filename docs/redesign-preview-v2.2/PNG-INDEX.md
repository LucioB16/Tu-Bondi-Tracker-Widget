# Índice de capturas v2.2

Previews HTML/CSS exportadas con Playwright a escala 3×. Son maquetas de diseño, no capturas del runtime Compose/Glance. Los datos están marcados DEMO y son ficticios; los ejemplos de color corresponden a valores observados en las respuestas guardadas de Tu Bondi.

| Captura | Tamaño lógico | PNG |
|---|---:|---|
| Arribos · claro | 393 × 852 dp | [arribos](png/arrivals-light-3x.png) · 1179 × 2556 px |
| Arribos · oscuro | 393 × 852 dp | [arribos](png/arrivals-dark-3x.png) · 1179 × 2556 px |
| Arribos desactualizados · claro | 393 × 852 dp | [estado antiguo](png/arrivals-stale-light-3x.png) · 1179 × 2556 px |
| Arribos desactualizados · oscuro | 393 × 852 dp | [estado antiguo](png/arrivals-stale-dark-3x.png) · 1179 × 2556 px |
| Arribos con error y datos recientes · claro | 393 × 852 dp | [error con datos conservados](png/arrivals-error-light-3x.png) · 1179 × 2556 px |
| Arribos con error y datos recientes · oscuro | 393 × 852 dp | [error con datos conservados](png/arrivals-error-dark-3x.png) · 1179 × 2556 px |
| Configuración · Paradas · claro | 393 × 852 dp | [paradas](png/config-stops-light-3x.png) · 1179 × 2556 px |
| Configuración · Paradas · oscuro | 393 × 852 dp | [paradas](png/config-stops-dark-3x.png) · 1179 × 2556 px |
| Configuración · búsqueda sin resultados · claro | 393 × 852 dp | [sin resultados](png/config-no-results-light-3x.png) · 1179 × 2556 px |
| Configuración · búsqueda sin resultados · oscuro | 393 × 852 dp | [sin resultados](png/config-no-results-dark-3x.png) · 1179 × 2556 px |
| Configuración · sin paradas · claro | 393 × 852 dp | [sin paradas](png/config-empty-light-3x.png) · 1179 × 2556 px |
| Configuración · sin paradas · oscuro | 393 × 852 dp | [sin paradas](png/config-empty-dark-3x.png) · 1179 × 2556 px |
| Configuración · Ajustes · claro | 393 × 852 dp | [ajustes](png/config-settings-light-3x.png) · 1179 × 2556 px |
| Configuración · Ajustes · oscuro | 393 × 852 dp | [ajustes](png/config-settings-dark-3x.png) · 1179 × 2556 px |
| Widget compacto · claro | 180 × 110 dp | [compacto](png/widget-compact-light-3x.png) · 540 × 330 px |
| Widget compacto · oscuro | 180 × 110 dp | [compacto](png/widget-compact-dark-3x.png) · 540 × 330 px |
| Widget mediano · claro | 270 × 180 dp | [mediano](png/widget-medium-light-3x.png) · 810 × 540 px |
| Widget mediano · oscuro | 270 × 180 dp | [mediano](png/widget-medium-dark-3x.png) · 810 × 540 px |
| Widget amplio · claro | 360 × 240 dp | [amplio](png/widget-wide-light-3x.png) · 1080 × 720 px |
| Widget amplio · oscuro | 360 × 240 dp | [amplio](png/widget-wide-dark-3x.png) · 1080 × 720 px |
| Widget amplio desactualizado · claro | 360 × 240 dp | [desactualizado](png/widget-wide-stale-light-3x.png) · 1080 × 720 px |
| Widget amplio desactualizado · oscuro | 360 × 240 dp | [desactualizado](png/widget-wide-stale-dark-3x.png) · 1080 × 720 px |

El compacto usa la prioridad actual del código: una parada y el arribo de menor tiempo; el mediano muestra la primera opción de dos paradas; el amplio muestra hasta cuatro arribos y un aviso independiente. Los tres tamaños mantienen número y destino sin depender del color. En el compacto, destino y tiempo ocupan líneas distintas para evitar el recorte de nombres largos.

Los estados adicionales de Arribos y Configuración se capturaron porque su microcopy cambia en v2.2. El error conserva y muestra datos de una respuesta exitosa reciente; la búsqueda sin resultados mantiene la parada ya configurada; el estado inicial muestra el buscador y la acción Guardar deshabilitada. No se regeneraron las capturas principales que no cambiaron.
