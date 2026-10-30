# Capturas de revisión v2.1

22 PNG a escala 3×: cinco pantallas y seis variantes de widget, cada una en claro y oscuro. Abrí `index.html` para ver la galería; consultá `REVIEW.md` para decisiones de UX y datos pendientes.

| Vista | Tema | Marco → PNG | Archivo |
|---|---|---:|---|
| Arribos · ETA ocultas por antigüedad | Claro | 393 × 852 dp → 1179 × 2556 px | [arribos-desactualizados-light-3x.png](png/arribos-desactualizados-light-3x.png) |
| Arribos · ETA ocultas por antigüedad | Oscuro | 393 × 852 dp → 1179 × 2556 px | [arribos-desactualizados-dark-3x.png](png/arribos-desactualizados-dark-3x.png) |
| Configuración · buscar y agregar al borrador | Claro | 393 × 852 dp → 1179 × 2556 px | [config-buscar-agregar-light-3x.png](png/config-buscar-agregar-light-3x.png) |
| Configuración · buscar y agregar al borrador | Oscuro | 393 × 852 dp → 1179 × 2556 px | [config-buscar-agregar-dark-3x.png](png/config-buscar-agregar-dark-3x.png) |
| Configuración · quitar, guardar y contenido variable | Claro | 393 × 852 dp → 1179 × 2556 px | [config-borrador-scroll-light-3x.png](png/config-borrador-scroll-light-3x.png) |
| Configuración · quitar, guardar y contenido variable | Oscuro | 393 × 852 dp → 1179 × 2556 px | [config-borrador-scroll-dark-3x.png](png/config-borrador-scroll-dark-3x.png) |
| Configuración · confirmar descarte | Claro | 393 × 852 dp → 1179 × 2556 px | [config-confirmar-descarte-light-3x.png](png/config-confirmar-descarte-light-3x.png) |
| Configuración · confirmar descarte | Oscuro | 393 × 852 dp → 1179 × 2556 px | [config-confirmar-descarte-dark-3x.png](png/config-confirmar-descarte-dark-3x.png) |
| Configuración · valores actuales | Claro | 393 × 852 dp → 1179 × 2556 px | [config-valores-actuales-light-3x.png](png/config-valores-actuales-light-3x.png) |
| Configuración · valores actuales | Oscuro | 393 × 852 dp → 1179 × 2556 px | [config-valores-actuales-dark-3x.png](png/config-valores-actuales-dark-3x.png) |
| Widget compacto · una sola superficie de acceso | Claro | 180 × 110 dp → 540 × 330 px | [widget-compacto-180x110-light-3x.png](png/widget-compacto-180x110-light-3x.png) |
| Widget compacto · una sola superficie de acceso | Oscuro | 180 × 110 dp → 540 × 330 px | [widget-compacto-180x110-dark-3x.png](png/widget-compacto-180x110-dark-3x.png) |
| Widget mediano · dos arribos | Claro | 270 × 180 dp → 810 × 540 px | [widget-mediano-270x180-light-3x.png](png/widget-mediano-270x180-light-3x.png) |
| Widget mediano · dos arribos | Oscuro | 270 × 180 dp → 810 × 540 px | [widget-mediano-270x180-dark-3x.png](png/widget-mediano-270x180-dark-3x.png) |
| Widget amplio · tres arribos | Claro | 360 × 240 dp → 1080 × 720 px | [widget-amplio-360x240-light-3x.png](png/widget-amplio-360x240-light-3x.png) |
| Widget amplio · tres arribos | Oscuro | 360 × 240 dp → 1080 × 720 px | [widget-amplio-360x240-dark-3x.png](png/widget-amplio-360x240-dark-3x.png) |
| Widget compacto · datos antiguos | Claro | 180 × 110 dp → 540 × 330 px | [widget-desactualizado-180x110-light-3x.png](png/widget-desactualizado-180x110-light-3x.png) |
| Widget compacto · datos antiguos | Oscuro | 180 × 110 dp → 540 × 330 px | [widget-desactualizado-180x110-dark-3x.png](png/widget-desactualizado-180x110-dark-3x.png) |
| Widget compacto · error | Claro | 180 × 110 dp → 540 × 330 px | [widget-error-180x110-light-3x.png](png/widget-error-180x110-light-3x.png) |
| Widget compacto · error | Oscuro | 180 × 110 dp → 540 × 330 px | [widget-error-180x110-dark-3x.png](png/widget-error-180x110-dark-3x.png) |
| Widget compacto · sin paradas | Claro | 180 × 110 dp → 540 × 330 px | [widget-sin-paradas-180x110-light-3x.png](png/widget-sin-paradas-180x110-light-3x.png) |
| Widget compacto · sin paradas | Oscuro | 180 × 110 dp → 540 × 330 px | [widget-sin-paradas-180x110-dark-3x.png](png/widget-sin-paradas-180x110-dark-3x.png) |

## Cobertura

- Arribos desactualizados: fecha y antigüedad del último éxito; los minutos de llegada quedan ocultos.
- Configuración: buscar/agregar, editar borrador/quitar, confirmar descarte al volver y valores actuales. La pantalla de borrador usa escala de texto ampliada y contenido desplazable.
- Widget compacto, mediano y amplio: muestra una, dos y tres ETA; los estados de datos antiguos, error y sin paradas se capturan en el compacto, el caso más restrictivo.
- Se verificó que las capturas y atributos accesibles de la galería no muestren códigos de parada. Los números 42, 40 y 71 son líneas de colectivo.
- Las pantallas son marcos HTML/CSS de referencia, no capturas de Compose/Glance en un launcher. Los PNG se generaron desde vistas a 1× y se exportaron a 3×.
- Los destinos y ETA se basan en el fixture local de API y están marcados como DEMO; no son información en vivo.
