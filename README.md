# Tu Bondi Widget

**Tus paradas y próximos colectivos, en la pantalla de inicio.**

<p align="center">
  <img src="docs/brand/tu-bondi-logo/logo_transparent_1024.png" alt="Logo de Tu Bondi" width="92">
</p>

Elegí tus paradas, seguí los arribos y recibí avisos desde un widget que podés adaptar a tu pantalla. Cada widget guarda sus propias paradas, líneas y ajustes.

<p align="center">
  <img src="docs/images/widget.png" alt="Widget amplio de Tu Bondi con arribos agrupados por parada" width="310">
  <img src="docs/images/arribos.png" alt="Pantalla de Arribos con líneas identificadas por color y última actualización" width="310">
</p>

<p align="center"><sub>Capturas reales en Android API 36. Los arribos y avisos usan respuestas simuladas de un servidor local de QA; no son datos en vivo.</sub></p>

## Funciones

- Widget adaptable con arribos agrupados por parada y avisos según el espacio disponible.
- Búsqueda por calle, barrio o nombre, con selección de líneas por parada.
- Hora y antigüedad calculadas desde la última actualización exitosa; una consulta fallida conserva datos recientes y los horarios dejan de mostrarse al vencer.
- Mapa para las paradas con coordenadas disponibles. Las demás se mantienen en la lista, sin ubicaciones inventadas.
- Tema claro/oscuro, alto contraste y notificaciones opcionales.

<p align="center"><img src="docs/images/configuracion.png" alt="Configuración de Tu Bondi con búsqueda de paradas, selecciones actuales y Guardar cambios" width="310"></p>

## Instalación

La app requiere **Android 8.0 (API 26) o superior**. Para compilar necesitás Android SDK 35 y JDK 17.

```bash
git clone https://github.com/LucioB16/Tu-Bondi-Tracker-Widget.git
cd Tu-Bondi-Tracker-Widget
bash gradlew assembleDebug
```

Instalá `app/build/outputs/apk/debug/app-debug.apk` desde Android Studio o con:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

1. Agregá **TuBondi Widget** desde el selector de widgets de Android.
2. Buscá una parada o elegí una desde Lugares frecuentes o un recorrido disponible.
3. Seleccioná las líneas que querés consultar. Sin filtro, se muestran todas.
4. Tocá **Guardar cambios** para aplicar la configuración. Si volvés con cambios pendientes, podés seguir editando o descartarlos.
5. Tocá el widget para abrir Arribos, actualizar los datos o cambiar los ajustes.

## Datos y privacidad

La búsqueda usa un catálogo local incluido en la app. No se descarga ni actualiza automáticamente: todavía falta validar una fuente municipal, la antigüedad de sus datos, la equivalencia de códigos y sus condiciones de uso. Los identificadores de paradas se usan internamente y no se muestran.

La app consulta los arribos del servicio de Tu Bondi cuando actualizás o se ejecuta la tarea programada. Android impone un mínimo de 15 minutos para el trabajo periódico y puede demorarlo según la batería y el dispositivo. La vigencia de los horarios sigue el intervalo configurado, entre 15 y 60 minutos; los fallos no adelantan ese vencimiento.

El mapa muestra solo coordenadas entregadas por el servicio para recorridos consultados. No solicita la ubicación del teléfono. El mapa requiere conexión para cargar su cartografía; su proveedor, atribución y condiciones están documentados en [`docs/map-provider-decision.md`](docs/map-provider-decision.md).

En Android 13 o posterior, las notificaciones requieren permiso. También se pueden silenciar desde el canal de notificaciones de Android.

## Desarrollo y pruebas

La aplicación usa Kotlin, Jetpack Compose y Material 3. Los widgets usan Jetpack Glance; DataStore conserva la configuración por widget y WorkManager programa la actualización periódica.

```bash
bash gradlew testDebugUnitTest
```

Las pruebas unitarias usan respuestas simuladas y no consultan el servicio. Para validar el flujo completo sin conectarse a producción, iniciá el stub local, conectá el emulador y compilá con el override:

```bash
python3 tools/mock_tubondi_api.py --mode success
adb reverse tcp:8765 tcp:8765
bash gradlew assembleDebug -PtubondiApiBaseUrl=http://127.0.0.1:8765
```

El stub escucha solo en `127.0.0.1`. La variante release mantiene la URL HTTPS del servicio. Los flujos de Maestro deben ejecutarse de a uno sobre el mismo emulador.

## Créditos

El ícono de la app usa el kit Android de Tu Bondi diseñado por Tiento. La referencia de comportamiento de transporte es [Tu-Bondi-Oma](https://github.com/LucioB16/Tu-Bondi-Oma).

Licencia: consultá [LICENSE](LICENSE).
