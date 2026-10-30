# Revisión de los issues abiertos — propuesta v2.2

Consulta directa a GitHub: 2026-10-30, repositorio `LucioB16/Tu-Bondi-Tracker-Widget`. Hay exactamente ocho issues abiertos. Los ocho tienen cero comentarios; no hay conversaciones adicionales ni cambios de alcance que incorporar. No se cerró ni modificó ningún issue.

## Resumen comparativo

| Issue | Estado en el código actual | Integración v2.2 | Prioridad / esfuerzo | Dependencias |
|---|---|---|---|---|
| [#1 Crear e integrar el logo de Tu Bondi](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/1) | Pendiente: el launcher usa el recurso genérico `ic_widget_placeholder`. | Independiente; usar la identidad aprobada en recursos de app. | P2 · M | Definir/validar marca antes de dibujar recursos finales. |
| [#2 Mostrar una preview del widget en el selector de Android](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/2) | Pendiente: `previewImage` apunta al placeholder y no hay preview representativa. | Alineado con el widget v2.2; fase aparte de producto. | P2 · S | Widget visual v2.2 acordado; comprobar compatibilidad del host y API Glance actual. |
| [#3 Evitar que la búsqueda de paradas se congele al escribir](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/3) | Probable causa localizada: búsqueda lineal y normalización por parada desde el flujo de UI; requiere medir para cerrar diagnóstico. | Independiente de la piel visual; hacerlo antes de reorganizar el buscador. | P1 · M | Ninguna. |
| [#4 Mostrar las paradas en un mapa](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/4) | Parcial en modelo remoto; catálogo offline sin coordenadas. El fixture de selección trae coordenadas como texto y no coincide con el DTO actual; decoder/cobertura real por confirmar. | Fase separada; no bloquear v2.2. | P3 · L | Confirmar forma, tipo y cobertura de coordenadas; proveedor/licencia/tiles. |
| [#5 Permitir filtrar las líneas por parada configurada](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/5) | Parcial en UI/persistencia/filtro. El fixture de selección no coincide con `StopDto`/`StopLineDto`; el circuito real de líneas por parada y su clave aún deben verificarse. | Integrable; revisar contrato, feedback visual e instancias y cubrir flujo completo. | P1 · M | Reconciliar el contrato de selección; confirmar clave y color API. |
| [#6 Simplificar el flujo de configuración del widget](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/6) | Parcialmente resuelto: pestañas, borrador, Guardar fijo y confirmación de descarte existen; Paradas sigue siendo una lista larga. | Integrable con el pase de microcopy v2.2; conservar la estructura actual y probar el scroll. | P1 · M | #3 para fluidez al buscar; #5 para presentar líneas por parada. |
| [#7 Ocultar el tramo de recorrido debajo de la línea en el widget](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/7) | Pendiente: `ArrivalRow` del widget muestra `routeDescription`; Arribos también lo muestra. | Integrable: widget conciso; Arribos conserva el recorrido completo. | P1 · M | Definir un destino breve con campos API existentes; verificar formatos antes de codificar. |
| [#8 Adaptar la visualización de avisos al tamaño del widget](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/8) | Pendiente: el widget no incluye el texto del aviso; solo muestra rótulos como “Aviso en Arribos”. | Integrable: contenido en amplio, acceso breve en tamaños menores. | P2 · M | #7 libera densidad vertical; validación en Glance/launcher para los tamaños reales. |

**Leyenda de esfuerzo:** S = pequeño (hasta 1 día), M = mediano (1–3 días), L = grande (3–5 días), estimaciones de implementación más verificación en este repositorio.

## Clasificación y alcance

- **Integrables en v2.2:** [#5](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/5) filtros por parada y color de línea; [#6](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/6) microcopy y flujo de configuración; [#7](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/7) filas de widget; [#8](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/8) avisos por tamaño. #5 requiere primero validar el contrato de líneas por parada.
- **Funcionales/técnicos independientes:** [#1](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/1) identidad del launcher y [#2](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/2) preview del selector pueden implementarse en fases de producto después de aprobar dirección y layout. [#3](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/3) es optimización técnica del buscador y conviene resolverla antes de medir/reorganizar #6.
- **Bloqueado por datos/decisión externa:** [#4](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/4) mapa. Antes requiere verificar el payload y cobertura de coordenadas, compatibilidad de decodificación y elegir proveedor/tiles con sus condiciones.
- **Duplicados/conflictos:** no hay issues duplicados. #7 y #8 comparten espacio vertical y deben secuenciarse (#7 libera espacio para #8); #1 y #2 comparten identidad pero no dependencia directa. #5 y v2.2 comparten el tratamiento de colores; el contrato actual del fixture y el DTO discrepan, por eso no conviene asumir todavía que el color por cliente se puede propagar a chips por parada.

## Análisis individual

### #1 — Crear e integrar el logo de Tu Bondi

- **GitHub:** [#1](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/1) · etiqueta `enhancement` · comentarios: 0.
- **Pedido original:** identidad reconocible y legible en pequeño; ícono adaptable y fondos claro/oscuro; variante monocromática cuando corresponda; fuentes versionadas y documentadas.
- **Estado real:** `AndroidManifest.xml` referencia `@mipmap/ic_launcher` y `@mipmap/ic_launcher_round`, pero el XML adaptativo usa el vector genérico `ic_widget_placeholder`. No hay identidad propia suficiente para cumplir el objetivo.
- **Propuesta:** crear un símbolo vectorial simple derivado de la marca “Tu Bondi”; exportar foreground/background adaptativos y versión monocromática tematizable. Revisar a 24, 48 y 108 px; documentar el fuente vectorial. No incorporar bibliotecas de iconos para esto.
- **Archivos probables:** `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`, recursos `drawable`/`mipmap`, `app/src/main/AndroidManifest.xml` y nota breve de marca en `docs/`.
- **Impacto v2.2:** la paleta y el acento cálido/frío del rediseño se conservan; el logo no reemplaza el color API de cada línea.
- **Prioridad/esfuerzo/dependencias:** P2 · M · sin dependencia técnica; primero acordar forma final de la marca con el usuario si la propuesta de logo cambia su identidad.
- **Aceptación:** launcher muestra el mismo símbolo en íconos adaptativo/redondo; legible en pequeño y sobre claro/oscuro; aparece variante monocromática en launcher que la soporte; fuentes de recursos se pueden regenerar; sin distorsión en emulador.
- **Pruebas:** inspección de recursos Android, build, capturas de launcher en ambos temas y comprobación manual de ícono temático en versión Android compatible.

### #2 — Preview en el selector de widgets

- **GitHub:** [#2](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/2) · `enhancement` · comentarios: 0.
- **Pedido original:** preview actual, datos ficticios y sin códigos; metadatos de Android/Glance; verificación en el selector real.
- **Estado real:** `app/src/main/res/xml/transit_widget_info.xml` configura `android:previewImage` con `ic_widget_placeholder`; no representa el diseño. No se encontró metadata `previewLayout`.
- **Propuesta:** usar un render estático derivado de la galería final, con parada y líneas ficticias claramente reconocibles, sin IDs internos. Declarar el mecanismo compatible con `androidx.glance:glance-appwidget` actualmente usado; conservar imagen de respaldo para launchers que no rendericen un layout de preview. No elevar la versión de Glance como parte del arreglo salvo que la verificación del selector demuestre que el contrato actual no alcanza.
- **Archivos probables:** `app/src/main/res/xml/transit_widget_info.xml`, recurso de preview, quizá layout de preview si es compatible; `app/build.gradle.kts` solo si la API instalada lo requiere.
- **Impacto v2.2:** aplicar el número de línea con un acento de color API, microcopy sin “ETA” y nombre descriptivo; no anunciar datos en vivo.
- **Prioridad/esfuerzo/dependencias:** P2 · S · alinear primero con el diseño del widget v2.2; no depende del logo (#1).
- **Aceptación:** preview no usa placeholder, no filtra códigos y refleja estructura/tema aprobados; aparece en selector real de Android; alternativa de respaldo cubre launcher compatible con imagen.
- **Pruebas:** inspección XML/recursos, build e instalación, captura del selector con widget compacto y comparación con el arte estático.

### #3 — Búsqueda de paradas sin bloqueos

- **GitHub:** [#3](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/3) · etiqueta `bug` · comentarios: 0.
- **Pedido original:** escribir, borrar y pegar sin congelar; filtrar fuera del hilo principal; evaluar debounce; solo resultados de la consulta más reciente y sin duplicados; catálogo completo y dispositivo representativo.
- **Estado real:** `CatalogRepository.search()` filtra el catálogo completo, normalizando nombre y barrio durante cada llamada. `ConfigViewModel.search()` actualiza estado y lanza búsqueda en `viewModelScope`; por defecto ese scope usa Main. Esto puede bloquear la UI por letra. Falta perfilar el comportamiento real antes de declarar causa raíz cerrada.
- **Propuesta:** normalizar un índice de búsqueda una sola vez al cargar catálogo; ejecutar la consulta en dispatcher de CPU; usar `debounce` corto + `mapLatest`/cancelación de consultas obsoletas; mantener el campo inmediato y deduplicar por código interno sin exponerlo. Elegir el debounce tras medir, no asumir una demora larga.
- **Archivos probables:** `data/catalog/CatalogRepository.kt`, `ui/config/ConfigViewModel.kt`, tests de catálogo/ViewModel; `ConfigActivity.kt` solo para estado de búsqueda.
- **Impacto v2.2:** el indicador de búsqueda y resultados se mantienen concisos; no cambiar la organización visual por la optimización.
- **Prioridad/esfuerzo/dependencias:** P1 · M · ninguna; completarlo antes del rediseño del flujo #6.
- **Aceptación:** input continúa respondiendo a texto, borrar y pegado con catálogo completo; no hay frame drop perceptible durante búsqueda en emulador representativo; una consulta lenta no pisa a la más reciente; sin duplicados; códigos técnicos no llegan a UI ni accesibilidad.
- **Pruebas:** unitarias con catálogo completo, consultas rápidas consecutivas y cancelación; test de ViewModel; trace/medición en emulador de escritura, borrado y pegado.

### #4 — Explorar paradas en mapa

- **GitHub:** [#4](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/4) · `enhancement` · comentarios: 0.
- **Pedido original:** mapear solo coordenadas presentes; abrir marcador con nombre descriptivo y selección; no exponer IDs ni inventar ubicaciones; mantener búsqueda/lista y manejar paradas sin coordenadas.
- **Estado real:** `StopDto` y `Stop` declaran latitud/longitud opcionales; `TransitRepository` las copia. El catálogo local las deja en `null`, así que el buscador no obtiene coordenadas de esa fuente. Además, el fixture guardado `03-route_selection.json` incluye `lat` y `lon` como cadenas (por ejemplo `"-31.411505"`), mientras `StopDto` las declara `Double?`. El fixture tiene `linea`, `cliente`, `color` y `parada_nombre` en cada entrada; el DTO espera `lineas[]` y no declara `cliente` ni `color` en `StopDto`. Esto demuestra una discrepancia entre el ejemplo guardado y el modelo, pero por sí solo no demuestra la respuesta viva actual. No se identificó SDK de mapa ni fuente de tiles.
- **Propuesta:** antes de un mapa, confirmar una respuesta real vigente y reconciliar el DTO con el contrato documentado. Verificar tolerancia de coordenadas numéricas y cadenas numéricas válidas, rechazar valores inválidos y medir cobertura/origen. Mantener la lista como fuente completa y agregar mapa solo para stops con lat/lon válidos; los demás quedan accesibles desde lista. Marcador abre tarjeta descriptiva con acción de agregar. No geocodificar calles ni convertir códigos en coordenadas. Elegir SDK/tiles después de revisar licencias, atribución, privacidad, offline y costo.
- **Archivos probables:** `data/catalog/CatalogRepository.kt`, `domain/model/DomainModels.kt`, `data/remote/dto/DtoModels.kt`, nueva pantalla/componente Compose y dependencias solo después de elegir proveedor.
- **Impacto v2.2:** independiente; el mapa no es requisito para buscar, agregar o filtrar. No sumarlo a los mockups v2.2 como navegación aprobada.
- **Prioridad/esfuerzo/dependencias:** P3 · L · bloqueado por cobertura/origen fiable de coordenadas y decisión de proveedor/condiciones de uso.
- **Aceptación:** el parser del contrato confirmado conserva coordenadas reales válidas y descarta inválidas; solo aparecen puntos con coordenadas reales; tap muestra nombre y agrega sin mostrar el ID; faltantes visibles desde lista; búsqueda sigue disponible; sin permiso de ubicación del usuario si no hace falta.
- **Pruebas:** fixtures de coordenadas numéricas, cadenas numéricas, ausentes e inválidas según el contrato confirmado; tests de decodificación e integración de selección; verificación visual/funcional con mapa en dispositivo y atribución del proveedor.

### #5 — Filtrar líneas por parada configurada

- **GitHub:** [#5](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/5) · `enhancement` · comentarios: 0.
- **Pedido original:** activar/desactivar líneas por parada y widget, guardar independientemente, reflejar en widget/Arribos; comportamiento de filtro vacío explícito y sin residuos al quitar elementos.
- **Estado real:** ya existe `ConfigViewModel.toggleLine(stopCode, lineId, enabled)`; la selección se persiste en `StopSelection.selectedLines` por configuración de widget; `TransitRepository.filterArrivals()` aplica el filtro. `SelectedStopCard` presenta `FilterChip` y hay pruebas unitarias de toggle/filtro. Pero el ejemplo `03-route_selection.json` trae una parada por entrada con campos `linea` y `cliente`, mientras `StopDto` espera `lineas[]` y `StopLineDto` no conserva cliente. `fetchStopsForRoute()` deriva las líneas desde ese DTO. Por eso la implementación del toggle no demuestra por sí sola que las líneas ofrecidas por parada, el filtro persistido y el color correspondan a la respuesta real actual. El fixture también es una muestra guardada, no prueba concluyente del payload vivo.
- **Propuesta:** primero capturar/confirmar el contrato vivo de selección y reconciliar DTO y pruebas; luego conservar el modelo si la clave de línea actual resulta única para el filtro. Si el contrato confirma que ID de línea se repite entre clientes en una misma selección, usar una clave compuesta en selección/filtro con migración compatible de preferencias. Simplificar “Si no elegís líneas…” y usar chip seleccionado con check + borde/acento API. Sin líneas elegidas significa “todas”; al quitar parada, limpiar su selección al guardar. Para color usar el registro de catálogo cuando coincida con la clave confirmada; si falta un campo o hay discrepancia, neutro.
- **Archivos probables:** `ui/config/ConfigActivity.kt`, `ui/config/ConfigViewModel.kt`, `domain/model/DomainModels.kt`, `data/repo/TransitRepository.kt` y tests existentes.
- **Impacto v2.2:** integrado directamente en chips de línea con color; color no será la única señal de selección.
- **Prioridad/esfuerzo/dependencias:** P1 · M · primero reconciliar/validar el payload de selección; luego depende del identificador y color API confirmados para una apariencia consistente.
- **Aceptación:** selección persiste de forma independiente por widget; llega igual a Arribos y widget; quitar una línea/parada no deja filtro obsoleto; vacío conserva el comportamiento “todas”; selección accesible sin depender del color.
- **Pruebas:** tests de decodificación con payload real representativo; unitarias de toggle/filtrado y claves duplicadas entre clientes si aplica; preferencias con dos IDs de widget; flujo configurar→guardar→Arribos/widget y chips con contraste.

### #6 — Simplificar configuración

- **GitHub:** [#6](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/6) · `enhancement` · comentarios: 0.
- **Pedido original:** orden claro, menos scroll, evaluar tabs/pasos sin asumir; Guardar fijo; Volver confirma; mantener borrador al cambiar sección; probar pantallas pequeñas y escala grande.
- **Estado real:** ya hay pestañas “Paradas/Ajustes”, borrador por instancia, Guardar abajo y confirmación de descarte. En Paradas se apilan búsqueda, hubs/resultados y todas las tarjetas/chips en una columna desplazable. Siguen textos de ayuda redundantes. Es avance parcial, no un flujo completamente optimizado.
- **Propuesta:** conservar las dos pestañas existentes, evitar una wizard con pasos adicionales. Compactar la zona de búsqueda, mantener paradas seleccionadas agrupadas y colapsables solo si las pruebas de accesibilidad lo permiten; al seleccionar un resultado, el chip con check deja clara la selección. Etiqueta breve “Cambios sin guardar” aparece solo cuando hay cambios; Guardar se habilita entonces; Volver ofrece “Seguir editando” y “Descartar”. No afirmar que se reduce scroll sin probar 360 dp y fuente ampliada.
- **Archivos probables:** `ui/config/ConfigActivity.kt` y tests de ViewModel/Compose.
- **Impacto v2.2:** el pase principal de microcopy y representación de color; preserva navegación aprobada.
- **Prioridad/esfuerzo/dependencias:** P1 · M · después de #3; valida también #5.
- **Aceptación:** flujo para buscar/agregar, filtrar líneas, quitar, guardar o descartar se completa sin ambigüedad; draft sobrevive al cambio de pestaña; Guardar siempre accesible; en 360 dp y 130% de fuente no se ocultan acciones ni chips.
- **Pruebas:** UI instrumentada del flujo guardar/volver/descartar; screenshot tests claro/oscuro; TalkBack, teclado, fuente 130% y viewport pequeño; medición de scroll con escenario de varias paradas.

### #7 — Quitar recorrido largo del widget

- **GitHub:** [#7](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/7) · `enhancement` · comentarios: 0.
- **Pedido original:** quitar el subtítulo desde/hasta debajo de la línea, conservar número/destino/tiempo legibles, mantener el recorrido completo en Arribos y validar tamaños.
- **Estado real:** `widget/TransitAppWidget.kt:ArrivalRow` dibuja `arrival.routeDescription` debajo de la línea; `ui/arrivals/ArrivalsActivity.kt` lo muestra en la pantalla completa. El DTO contiene también `sentido`, pero las muestras reales usan valores internos como `I` y `V`, no un destino entendible.
- **Propuesta:** el widget elimina la ruta completa y muestra el número de línea, un destino breve fiable y el tiempo. Añadir una propiedad derivada de presentación solo si se puede resolver a partir de los campos/contrato reales (`routeDescription` contiene ejemplos “origen A destino”); validar delimitadores y fallback sobre todos los fixtures antes de codificar. Nunca mostrar `I`/`V` como destino ni inventar terminales. Arribos conserva el recorrido completo. Compacto prioriza número+destino+minutos; mediano/amplio mantienen los mismos campos con más filas.
- **Archivos probables:** `widget/TransitAppWidget.kt`, transformación de datos de llegada en `TransitRepository.kt` o helper de dominio, y tests de esa transformación; Arribos no pierde `routeDescription`.
- **Impacto v2.2:** alto; aligera widget y deja espacio al color de línea y a avisos en alto.
- **Prioridad/esfuerzo/dependencias:** P1 · M · integrar al rediseño; confirmar contrato del destino corto antes de implementarlo.
- **Aceptación:** compacto/mediano/amplio muestran línea, destino inequívoco y tiempo sin texto de recorrido completo; Arribos mantiene el trayecto; formatos de ruta desconocidos no producen terminales falsas; sin “ETA” visible/accesible.
- **Pruebas:** unitarias del destino con todos los formatos observados, casos incompletos y texto largo; capturas reales del widget en tres tamaños y Arribos; escala grande.

### #8 — Avisos según alto del widget

- **GitHub:** [#8](https://github.com/LucioB16/Tu-Bondi-Tracker-Widget/issues/8) · `enhancement` · comentarios: 0.
- **Pedido original:** mostrar aviso separado de arribos cuando alcance el alto; CTA comprensible en pequeño; al tocar llegar a Arribos con avisos fáciles de encontrar; textos extensos sin recorte engañoso.
- **Estado real:** `TransitAppWidget.kt` detecta mensajes con `hasNotice()`, pero en tamaño amplio solo muestra “⚠ Aviso en Arribos”; en el estado sin grupo aparece “Avisos disponibles · ver en Arribos”. No renderiza el contenido en ningún tamaño y no hay scroll interno de Glance.
- **Propuesta:** definir densidad por tamaño usando `LocalSize` y `SizeMode.Exact` existente. Compacto/mediano: banda de una línea “Avisos · Tocá para ver” como parte de la tarjeta que ya abre Arribos. Amplio: un aviso con título/texto limpiado, separado por superficie/icono de las filas, limitado a 2 líneas con elipsis no engañosa (“Ver aviso completo” abre Arribos). Mantener número/destino/tiempo primero; si no caben, reservar solo la CTA. No agregar navegación profunda hasta confirmar que la pantalla Arribos desplaza/enfoca Avisos de forma estable.
- **Archivos probables:** `widget/TransitAppWidget.kt`, helper de agregación `collectAvisos()`, intents/actividad de Arribos solo si se necesita un destino directo y pruebas Glance/UI.
- **Impacto v2.2:** integrado en el widget amplio; CTA concisa en compacto/mediano. El color del aviso sigue acompañado de icono/texto.
- **Prioridad/esfuerzo/dependencias:** P2 · M · después de #7 para recuperar espacio; requiere validación con launcher real porque Glance no ofrece scroll interno.
- **Aceptación:** texto de aviso aparece separado del listado en tamaño que lo permite; en tamaños menores existe CTA “Tocá para ver avisos”; nunca oculta o reemplaza la fila prioritaria; textos largos no se presentan truncados como completos; tap abre Arribos; sin “Avisos en Arribos”.
- **Pruebas:** unitarias de agregación/limpieza de avisos y selección de layout por tamaño; Glance instrumentation/launcher en 180×110, 270×180 y 360×240 dp; avisos cortos, extensos y múltiples, ambos temas, fuente ampliada.

## Sinergias y conflictos

- **#5 + color de líneas v2.2:** usar el mismo acento por cliente e ID de línea en chips, Arribos y widget; el estado seleccionado usa check/borde y texto, no solo el color.
- **#6 + #3:** optimizar primero la búsqueda; luego medir el flujo reorganizado. Así una mejora visual no enmascara una búsqueda que sigue bloqueando el hilo principal.
- **#7 + #8:** quitar la ruta completa del widget libera espacio para el aviso sin sacrificar la prioridad de línea, destino y tiempo.
- **#1 + #2:** ambos tratan identidad visual, pero el preview del widget no debe esperar al logo de launcher; los issues pueden cerrarse de forma independiente.
- **#4 es independiente y bloqueado por datos/proveedor:** no se debe prometer mapa hasta conocer cobertura real de coordenadas y condiciones de tiles.
- **Conflicto de fuentes y forma del contrato:** snapshots guardados informan color `#RRGGBB`, pero no prueban que todas las respuestas actuales mantengan ese formato. La línea visible 42/ID `42`, cliente `411`, aparece `#ff9500` en catálogo y arribos, pero `#fe3434` en selección de ruta; la visible 71/ID `71` tiene clientes 422 y 420 en catálogo/arribos. No fijar color por número visible ni mezclar operadores. Propuesta: clave `(cliente, line_id)` solo donde el contrato confirma ambos; catálogo como fuente preferida cuando la clave coincide, color de parada/arribo como respaldo si la clave coincide y falta el canónico. Si falta una parte de la clave, el color no es `#RRGGBB` válido o hay discrepancia de valores para la misma clave, usar neutro hasta confirmar la fuente. Hoy `StopLineDto` no conserva cliente y el fixture de selección no coincide con su estructura. Resolver primero esta discrepancia antes de afirmar que la clave compuesta funciona en chips por parada. Los swatches de la galería son muestras visuales de snapshots, no una tabla de colores de producción.
- **Contrato de selección/coordenadas por verificar antes de implementar:** `03-route_selection.json` representa `lat`/`lon` como cadenas y campos de línea/cliente/color a nivel de elemento; `StopDto` declara `Double? lat/lon`, `lineas[]` y omite `cliente`/`color`. La documentación/comentario del DTO y el fixture no están alineados. Confirmar payload vivo y decodificación antes de ampliar modelos o contar con coordenadas y líneas filtrables.

## Orden propuesto

1. **Base v2.2:** establecer una paleta de línea dinámica segura y rehacer microcopy/estados sin siglas técnicas; validar HTML/PNG y obtener aprobación visual y técnica.
2. **Rendimiento #3** y verificación del filtro **#5**: trabajo de bajo riesgo que estabiliza el buscador y la selección por parada.
3. **Configuración #6**: integrar colores/chips, copy y medición de scroll con los resultados rápidos de #3.
4. **Widget #7**, luego **#8**: separar recorrido y destino fiable, después asignar el espacio recuperado a avisos.
5. **#2 preview** con el layout ya estable; **#1 logo** puede avanzar en paralelo tras acordar identidad.
6. **#4 mapa** en fase independiente cuando se confirmen cobertura de coordenadas, origen y proveedor.

Esta propuesta no autoriza ni incluye cambios en código Kotlin/Compose/Glance ni cierres de issues. Se requiere aprobación del usuario tanto para la dirección v2.2 como para este plan técnico.
