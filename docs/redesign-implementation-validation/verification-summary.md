# Verificación final del rediseño

**Fecha:** 2026-10-30

**Dispositivo:** `TuBondi_Test_API36` (`emulator-5554`, Android API 36)
**Build QA:** debug con `-PtubondiApiBaseUrl=http://127.0.0.1:8765`; el stub escucha solo en loopback y no reenvía a producción.

## Build y pruebas

Comando ejecutado:

```bash
bash gradlew testDebugUnitTest assembleDebug assembleRelease -PtubondiApiBaseUrl=http://127.0.0.1:8765
```

Resultado: build debug y release exitosos; **60 tests en 13 suites**, cero fallos, errores o skips. La variante release genera `app-release.apk` firmado (versión 1.1.0, código 2). `apksigner verify --verbose` confirmó la firma v2 con un certificado RSA-2048.

Cinco flujos Maestro pasaron en el APK final:

| Flujo | Duración | Evidencia JUnit |
|---|---:|---|
| `c4-configure-aa50` | 29 s | [resultado](maestro-reactive-state-2026-10-30/c4-configure-aa50-junit.xml) |
| `c4-configure-bb17` | 24 s | [resultado](maestro-reactive-state-2026-10-30/c4-configure-bb17-junit.xml) |
| `c4-refresh-408` | 14 s | [resultado](maestro-reactive-state-2026-10-30/c4-refresh-408-junit.xml) |
| `c4-fullscreen-408` | 19 s | [resultado](maestro-reactive-state-2026-10-30/c4-fullscreen-408-junit.xml) |
| `c4-launcher-408` | 14 s | [resultado](maestro-reactive-state-2026-10-30/c4-launcher-408-junit.xml) |

Los flujos validan búsqueda sin mostrar el ID técnico, selección ya agregada sin duplicación, descarte del borrador, refresco fallido conservando el último dato y actualización del widget desde el launcher.

## Corrección encontrada durante QA

Una primera prueba del widget verificó un 408 persistido en DataStore mientras el launcher seguía presentando los RemoteViews anteriores. La causa era leer las preferencias antes de iniciar la composición Glance, cuyo estado podía continuar activo durante una actualización. `TransitAppWidget` ahora lee `currentState<Preferences>()` dentro de `provideContent`, para que las sesiones activas reciban el estado actualizado.

Después de la corrección, Maestro pasó el flujo 408 y capturas actuales verificaron el banner de error, la conservación de ETA recientes, la recuperación al siguiente éxito y el cambio de la hora de actualización:

- [Widget con error y últimos datos válidos](widget-reactive-error.png)
- [Widget recuperado después de un éxito](widget-error-recovered.png)
- [Arribos recuperados después de un éxito](arrivals-success-recovered-latest.png)
- [Widget final en launcher](widget-reactive-success-final.png)

La guía oficial de Glance también requiere observar los cambios dentro de la composición: [`currentState`](https://developer.android.com/reference/kotlin/androidx/glance/currentState.composable) expone el estado persistido y `update`/`updateAll` actualizan las instancias del widget.

## Verificación visual y accesibilidad

- Las capturas del README se tomaron de la app en API 36. Las filas y tiempos vienen del stub local y se identifican como datos de prueba.
- El set de capturas de emulador cubre widget compacto/mediano/amplio en claro y oscuro, además de Arribos y Configuración; contiene ejemplos de error, datos desactualizados, lista vacía, fuente al 130 % y orientación horizontal. Los PNG de Glance muestran el área real del widget dentro del launcher; la galería v2.1 es el marco de referencia HTML/CSS.
- Las capturas reales mantienen el lenguaje visual aprobado. Compose conserva tarjetas por parada y estados tipados; Glance muestra la prioridad y el número de filas que caben en el tamaño host.
- Evidencia visual seleccionada: Arribos [claro](arrivals-light-final.png), [oscuro](arrivals-dark.png), [error](arrivals-light-error-final.png), [vacío con fuente grande](arrivals-empty-narrow-font-130.png) y [horizontal](arrivals-landscape-settled.png); Configuración [clara](config-light-final.png), [oscura](config-stops-dark.png), [descarte](config-discard-dialog-light.png) y [fuente grande](config-narrow-font-130-fixed.png); widgets [compacto claro/oscuro](widget-compact-light.png) / [oscuro](widget-compact-dark.png), [mediano claro/oscuro](widget-medium-light.png) / [oscuro](widget-medium-dark.png), [amplio claro/oscuro](widget-wide-light-final.png) / [oscuro](widget-wide-dark-after-color-fix.png), [antiguo](widget-medium-light-stale.png), [alto contraste](widget-high-contrast.png), [error](widget-reactive-error.png) y [recuperación](widget-error-recovered.png).
- Se comprobaron catorce pares principales de texto/superficie; el contraste calculado va de **4.81:1 a 15.81:1**. Se mantuvieron etiquetas además del color para ETA, error y antigüedad.
- TalkBack se probó manualmente en Arribos, Configuración y acciones del widget; evidencia: `talkback-arrivals-*`, `talkback-config-*` y `talkback-widget-focus-final.png`. La última corrección no cambió semántica. Tras ella también se recorrieron con `Tab` las pestañas, el buscador y los controles de paradas. El servicio quedó desactivado al cerrar QA.

## Límites y estado local

- **D4 continúa pendiente:** se usa el catálogo local incluido en la app. No se activa descarga automática hasta validar la fuente municipal candidata, su vigencia, equivalencia de identificadores y condiciones de uso.
- No se utilizó la API de producción durante la verificación; `adb reverse` conectó el emulador únicamente al stub de loopback.
- `PLAN.md` no está versionado y sigue excluido mediante un `core.excludesFile` propio del worktree. El keystore de publicación está fuera del repositorio; el procedimiento repetible está en [`docs/RELEASING.md`](../RELEASING.md).

## Release v1.2.0 — 2026-10-30

- Revisión final local: testDebugUnitTest, assembleDebug y assembleRelease pasaron; 77 tests en 17 suites, cero fallos, errores o skips. Build serial, sin QEMU activo.
- Búsqueda de catálogo: 5.050 paradas, 20 consultas repetidas en 79 ms; índice normalizado cacheado y consulta en Dispatchers.Default.
- Prueba release aislada: modo avión activo; v1.1.0 y después v1.2.0 se instalaron con la misma firma SHA-256 e03c896e614033a6840d500444e82dc23e589ed109a1b8f3efc7530c16b408f5. v1.2.0 abrió correctamente y mostró el estado sin conexión sin consultar producción.
- Recorrido API 36 de release: selector real muestra preview 3×2 y descripción de demo; se colocó el widget, se agregó una parada local, se guardó, se abrió Arribos y se revisaron Paradas/Ajustes; permiso POST_NOTIFICATIONS concedido. El widget y Arribos mostraron el error esperado en modo avión.
- Ícono temático: Pixel Launcher API 36 muestra la variante monocromática en la pantalla de inicio; evidencia local ignorada: launcher-themed-icon-v1.2.png.
- Capturas README reales revisadas: docs/images/widget.png, docs/images/arribos.png y docs/images/configuracion.png. ReadmeBloom audit y git diff --check pasan. El ZIP de revisión v2.2 pasa unzip -t y permanece excluido localmente.
- Se agregó staleWideWidgetKeepsStopAndLineDetailsButHidesExpiredArrivalTime; conserva nombre de parada, línea y destino mientras oculta la estimación vencida.
