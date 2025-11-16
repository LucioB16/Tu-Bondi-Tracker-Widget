# TuBondi Glance Widget

Widget nativo para Android (Kotlin) que muestra próximos arribos de colectivos en Córdoba usando la API de TuBondi. Permite configurar paradas, líneas, frecuencia de actualización y notificaciones de llegada.

## ¿Cómo funciona?

```
Usuario ➜ ConfigActivity (Compose)
          ➜ TuBondiHttpClient (OkHttp + cookies PHPSESSID)
              ➜ TransitRepository (mapea DTO ➜ dominio)
                  ➜ DataStore (preferencias por widget)
                      ➜ WorkManager RefreshWorker (periódico / manual)
                          ➜ ArrivalNotifier (canal arrivals_alerts)
                              ➜ TransitAppWidget (Jetpack Glance)
```

1. Al añadir el widget, se abre la configuración para descubrir líneas/rutas, seleccionar paradas y líneas.
2. Se guardan las preferencias por `appWidgetId` en DataStore.
3. `RefreshWorker` consulta la API, actualiza el estado del widget (Glance + `PreferencesGlanceStateDefinition`) y evalúa notificaciones.
4. El widget usa `LazyColumn` para ser scrolleable y ofrece botón "Actualizar" (WorkManager OneTime) + acceso rápido a ajustes.

## Instalación

Requisitos:

- Android Studio Iguana o superior
- JDK 17
- Android SDK 26+

Comandos útiles:
```bash
./gradlew assembleDebug
./gradlew test
```

## Uso

1. Mantén presionado el escritorio y agrega **TuBondi Widget**.
2. Se abrirá la configuración Compose:
   - Descarga líneas/rutas con `GetLinesAndRoutes`.
   - Toca "Agregar paradas" para cada ruta; podrás marcar las paradas deseadas y filtrar líneas (todas seleccionadas por defecto).
   - Ajusta frecuencia (1-60 min), umbral de notificaciones (min), alto contraste y si quieres avisos.
3. Guarda la configuración; se ejecuta un primer refresh y queda programado uno periódico (≥15 min por políticas del SO). Si eliges menos de 15 min, el valor se respeta para refrescos manuales.
4. El widget muestra cada parada con sus líneas y ETA; el botón **Actualizar** fuerza un `OneTimeWorkRequest`. Tocar la cabecera abre de nuevo la configuración.

## Limitaciones del sistema

- Android sólo permite `PeriodicWorkRequest` ≥ 15 min. El valor elegido se guarda y se aplica cuando el SO lo permite; para intervalos menores se sugiere usar el botón de refresh.
- Los widgets no pueden ejecutar animaciones continuas; el scroll se resuelve con `LazyColumn`.
- Las notificaciones se desduplican por parada/línea y respetan la acción "Silenciar 1 h".

## Configuración avanzada

- `BaseUrl` y `conf` por defecto: `https://micronauta4.dnsalias.net` / `cbaciudad`. Puedes modificarlos en `TuBondiHttpClient` si necesitas apuntar a otra ciudad.
- Se usa `OkHttp` con `JavaNetCookieJar` para persistir `PHPSESSID` y reintentar cuando expira.
- Logs HTTP: `HttpLoggingInterceptor` nivel BASIC (puedes cambiarlo en `TuBondiHttpClient`).
- Alto contraste: toggle en la configuración que ajusta los colores del widget.

## Pruebas

```bash
./gradlew test
```

Incluye pruebas unitarias para:

- Parsing de ejemplos JSON de la API.
- Reintento ante expiración de cookie en el cliente HTTP.
- Filtro de líneas/paradas en el repositorio.
- Notificaciones (cálculo de "cercano" + deduplicación + silencio).
- DataStore por widget.
- ViewModel de configuración (selección de paradas/líneas).

## Privacidad

- Sólo se almacena `PHPSESSID` en `CookieManager` de OkHttp; no se registra en logs.
- Configuraciones de usuario se guardan localmente en DataStore (por widget) y pueden eliminarse quitando el widget.

## Créditos y referencias

- API TuBondi (Municipalidad de Córdoba / TuBondi).
- Jetpack Glance, WorkManager, Hilt.
- Basado en las especificaciones del repositorio original Tu Bondi Tracker (C#) usado como referencia de contratos.

## Licencia

Consulta `LICENSE` para más detalles.