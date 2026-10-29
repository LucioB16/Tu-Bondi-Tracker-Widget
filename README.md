# Tu Bondi Widget

**Consultá los próximos colectivos de Córdoba desde la pantalla de inicio.**

Elegí tus paradas y mirá arribos, destinos y avisos sin abrir otra app. Cada widget conserva su propia configuración.

<p align="center">
  <img src="docs/images/widget.png" alt="Widget de Tu Bondi con dos paradas y arribos de prueba locales" width="260">
  <img src="docs/images/arribos.png" alt="Pantalla completa con arribos de prueba locales para dos paradas" width="260">
</p>

<p align="center"><sub>Capturas reales en un emulador Android API 36. Los arribos son respuestas locales de QA, no información en vivo; la disponibilidad y precisión reales dependen del servicio de TuBondi.</sub></p>

## Qué podés hacer

- **Consultar desde el inicio.** El widget muestra tiempos estimados, destinos, distancias y avisos.
- **Elegir tus paradas.** Buscá por código, calle o barrio; si no encontrás una parada, ingresá su código exacto manualmente.
- **Separar contextos.** Cada widget puede tener su propia selección de paradas y líneas.
- **Configurar alertas.** Activá notificaciones de arribos próximos y elegí el umbral de tiempo.
- **Seguir con los últimos datos.** Si falla una actualización, el widget conserva los arribos del último refresh exitoso e indica su hora y el motivo del fallo.
- **Ajustar la lectura.** El modo de alto contraste se aplica al widget y a la pantalla completa; los controles exponen etiquetas y estados semánticos.

## Instalación y primeros pasos

La app requiere **Android 8.0 (API 26) o superior**. Para compilar necesitás Android Studio, Android SDK 35 y JDK 17 o superior.

```bash
git clone https://github.com/LucioB16/Tu-Bondi-Tracker-Widget.git
cd Tu-Bondi-Tracker-Widget
./gradlew assembleDebug
```

En Windows usá `gradlew.bat assembleDebug`. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`; instalalo desde Android Studio o con:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

1. Mantené presionada la pantalla de inicio y agregá **TuBondi Widget** desde el selector de widgets.
2. Buscá una parada o ingresá su código exacto. También podés elegir paradas desde las rutas disponibles.
3. Configurá la frecuencia de actualización (15–60 min), las alertas y tocá **Guardar**.
4. Tocá **Actualizar** para pedir datos nuevos o el encabezado para abrir la pantalla completa.

<p align="center"><img src="docs/images/configuracion.png" alt="Configuración con el catálogo offline de 160 rutas y opciones para agregar paradas" width="260"></p>

## Datos y actualizaciones

La actualización manual consulta el servicio cuando la solicitás. El refresco periódico usa WorkManager: Android establece un **mínimo de 15 minutos** y puede demorar la ejecución según la batería y las condiciones del dispositivo.

Las líneas y rutas se consultan al abrir la configuración; si la consulta falla, la app usa un respaldo incluido. El buscador de paradas usa un catálogo local incluido en la app. Ese catálogo no se descarga ni actualiza automáticamente: existe una fuente municipal candidata, pero su antigüedad, equivalencia de códigos y condiciones de uso no están verificadas. Mientras tanto, podés ingresar manualmente el código exacto de una parada ausente; agregarlo no consulta el servicio.

En Android 13 o posterior, las notificaciones requieren permiso. Si lo denegás, podés habilitarlo desde los ajustes de Android. El canal permite silenciar los avisos durante una hora; la app evita repetir una alerta para la misma parada y línea durante diez minutos.

## Desarrollo y QA

El widget está construido con Kotlin y Jetpack Glance; la configuración y la pantalla completa usan Compose. DataStore conserva la configuración por widget, WorkManager programa las actualizaciones y OkHttp consulta el servicio.

```bash
./gradlew testDebugUnitTest
```

Las pruebas unitarias usan fixtures y respuestas simuladas; no necesitan acceso a la API. Para probar el widget completo, instalá el APK en un dispositivo o emulador y agregalo al launcher.

El build debug usa la URL HTTPS de producción por defecto. Para QA aislado, iniciá el stub local, conectá el AVD con `adb reverse` y compilá debug con el override local:

```bash
python tools/mock_tubondi_api.py --mode success
adb reverse tcp:8765 tcp:8765
./gradlew assembleDebug -PtubondiApiBaseUrl=http://127.0.0.1:8765
```

El stub escucha exclusivamente en loopback y no contacta el servicio de producción. La variante release fija la URL HTTPS del servicio.

Los flows `c4-configure-*` parten de una instancia existente (ID 4 o 5) visible en launcher y abren su configuración mediante el control accesible `Configurar widget`. Antes de guardar en modo `success`, registrá `arrivals` y `responses408` con `Invoke-RestMethod 'http://127.0.0.1:8765/__control/stats'`; luego verificá que `arrivals` aumente y `responses408` no cambie antes de asumir que la caché quedó lista. Los contadores son acumulativos; para repetir QA no exijas `responses408=0` si ya se probó un error.

Para `*-408`, con la caché lista, fijá el modo y la demora con estos controles del stub:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8765/__control/mode?value=408'
Invoke-RestMethod 'http://127.0.0.1:8765/__control/delay?ms=8000'
maestro test --udid emulator-5564 flows/c4-fullscreen-408.yaml flows/c4-launcher-408.yaml
Invoke-RestMethod 'http://127.0.0.1:8765/__control/mode?value=success'
Invoke-RestMethod 'http://127.0.0.1:8765/__control/delay?ms=0'
```

La sesión de la API se mantiene en memoria mientras vive el proceso. Las preferencias se guardan localmente; al quitar una instancia se cancelan sus trabajos asociados y se purga su configuración sin afectar otros widgets. Los logs HTTP configurados por defecto no registran cookies.

## Referencias

Basado en el comportamiento observado de [Tu-Bondi-Oma](https://github.com/LucioB16/Tu-Bondi-Oma) y en los datos de TuBondi. El acceso al servicio puede cambiar independientemente de esta app.

Licencia: consultá [LICENSE](LICENSE).
