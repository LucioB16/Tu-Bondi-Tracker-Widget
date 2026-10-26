# TuBondi Glance Widget

Widget nativo para Android (Kotlin) que muestra próximos arribos de colectivos en Córdoba usando la API de TuBondi. Incluye buscador de paradas con catálogo offline, pantalla completa de arribos, alertas de línea (cortes/anulaciones), notificaciones de llegada y configuración multi-instancia.

## ¿Cómo funciona?

```
Usuario ➜ ConfigActivity (Compose)
          ➜ TuBondiHttpClient (handshake anti-408: PHPSESSID + fp + headers de browser)
              ➜ TransitRepository (mapea DTO ➜ dominio, presupuesto de ciclo 30s)
                  ➜ DataStore (preferencias por widget)
                      ➜ WorkManager RefreshWorker (periódico / manual)
                          ➜ ArrivalNotifier (canal arrivals_alerts, dedup + silencio)
                              ➜ TransitAppWidget (Jetpack Glance)
                                  ➜ ArrivalsActivity (pantalla completa, D1)
```

1. Al añadir el widget, se abre la configuración para descubrir líneas/rutas, buscar paradas (catálogo offline de 5050 paradas + 24 hubs) y filtrar líneas.
2. Se guardan las preferencias por `appWidgetId` en DataStore; al eliminar el widget se limpian config y work pendiente (SA-9).
3. `RefreshWorker` consulta la API dentro de un presupuesto de 30 s por ciclo, actualiza el widget (Glance) y evalúa notificaciones. Si falla, **retiene el último dato válido** marcándolo como degradado (SA-8).
4. El widget scrollea con `LazyColumn`, ofrece botón "Actualizar" (con feedback "Actualizando…") y al tocarlo abre la pantalla completa de arribos.

## Handshake con la API

La API de TuBondi exige un handshake para no responder 408 (receta verificada contra la API real, ver `docs/SDD_API_CONTRACT.md`):

1. `GET` de la página urbano con UA de navegador → `PHPSESSID` + `operacion` (gate de frescura, no se envía).
2. Fingerprint aleatorio de 64 hex registrado con `POST ?cmd=vista`.
3. Todo POST de comando lleva `X-Device-FlakerParam: <fp>`, `Origin`, `Referer`, headers de browser y `Content-Type` **sin** charset.
4. Ante 408/403/cuerpo vacío: re-sesión + 1 reintento. `SerializationException` no se reintenta.

El estado de sesión vive solo en memoria del proceso: nunca se persiste ni se loguea.

## Instalación

Requisitos:

- Android Studio con AGP 8.6.1+ (compileSdk 35)
- JDK 17+
- Android SDK 26+ (minSdk)

Comandos útiles:
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Uso

1. Mantén presionado el escritorio, elegí **Widgets → Browse → TuBondi Widget** y arrastralo a una celda.
2. Se abre la configuración Compose:
   - **Buscador de paradas**: por código, calle o barrio (≤25 resultados, funciona offline con el catálogo embebido) + acceso rápido a hubs.
   - Líneas/rutas siempre frescas de la API (fallback offline si no hay red).
   - Tocá "+ Agregar" para sumar paradas; filtrá líneas por parada (todas por defecto).
   - Frecuencia de refresco (por defecto **15 min**, el mínimo efectivo de WorkManager; valores menores aplican solo al refresco manual), umbral de notificaciones, alto contraste y conf.
3. Al guardar: primer refresh inmediato + refresco periódico (≥15 min por políticas del SO).
4. El widget muestra cada parada con sus líneas (color, destino, distancia y avisos) y botón "Actualizar". Tocar el widget abre la **pantalla completa** (reloj, arribos agrupados y avisos de la línea); ⚙ vuelve a la configuración.

## Notificaciones

- Al activarlas en Android 13+ se pide el permiso `POST_NOTIFICATIONS`; si se deniega, los avisos quedan desactivados sin romper el widget.
- Se desduplican por parada/línea (10 min) y respetan la acción "Silenciar 1 h".

## Limitaciones del sistema

- `PeriodicWorkRequest` mínimo de ~15 min: los valores menores elegidos aplican solo al refresco manual.
- Los widgets no ejecutan animaciones continuas; el scroll se resuelve con `LazyColumn`.

## Configuración avanzada

- `BaseUrl` y `conf` por defecto: `https://micronauta4.dnsalias.net` / `cbaciudad` (editables en la configuración).
- Catálogo de paradas embebido como fallback offline; se regenera con el cosechador del repositorio de referencia.
- Logs HTTP: `HttpLoggingInterceptor` nivel BASIC (nunca registra cookies).

## Privacidad

- `PHPSESSID`, `operacion` y `fp` viven solo en memoria del proceso durante el ciclo de refresh; no se persisten ni registran en logs.
- La configuración de usuario se guarda localmente en DataStore (por widget) y se elimina al quitar el widget.

## Pruebas

23 pruebas unitarias (sin red):

- Parseo de los 6 fixtures reales de la API + ETA (regla de Oma: 11/21/49, "arribando" → 0).
- Handshake anti-408 con MockWebServer (orden GET → vista → comando, headers exactos, re-sesión por 408/403/vacío).
- Presupuesto de ciclo 30 s con reloj simulado; retención de último dato válido.
- Filtro por `line_id` real; buscador del catálogo; dedup de notificaciones; DataStore por widget.
- `./gradlew testDebugUnitTest`

## Créditos y referencias

- API TuBondi (Municipalidad de Córdoba).
- Jetpack Glance, WorkManager, Hilt, OkHttp, DataStore.
- Comportamiento de referencia: repositorio Tu Bondi (C#) y Tu-Bondi-Oma (contratos y receta anti-408).

## Licencia

Consulta `LICENSE` para más detalles.
