# Tu Bondi Widget

**Los próximos colectivos de Córdoba, directamente en tu pantalla de inicio.**

Elegí tus paradas y consultá arribos, destinos y avisos sin abrir el navegador. Cada widget tiene su propia configuración.

<p align="center">
  <img src="docs/images/widget.png" alt="Widget de Tu Bondi en la pantalla de inicio con próximos arribos" width="280">
  <img src="docs/images/arribos.png" alt="Pantalla completa de arribos y avisos por parada" width="280">
</p>

<p align="center"><sub>Capturas reales del emulador Android; los arribos y avisos dependen del servicio.</sub></p>

## A mano cuando lo necesitás

- **Tus paradas.** Buscá por código, calle o barrio; si no aparece, ingresá el código exacto manualmente. Al elegir paradas por ruta podés filtrar líneas cuando están disponibles.
- **Información de un vistazo.** Consultá tiempo estimado, destino, distancia y avisos publicados por el servicio.
- **Más de un widget.** Configurá instancias independientes para distintas paradas.
- **Alertas opcionales.** Activá notificaciones de arribos próximos y definí el umbral de tiempo.
- **Sin conexión.** El buscador utiliza un catálogo incluido y el widget conserva todos los datos del último refresh exitoso si una actualización falla o supera su presupuesto; muestra el motivo y la hora de esos datos.

## Primeros pasos

La app requiere **Android 8.0 (API 26) o superior**. Para compilar necesitás Android Studio, Android SDK 35 y JDK 17 o superior.

```bash
git clone https://github.com/LucioB16/Tu-Bondi-Tracker-Widget.git
cd Tu-Bondi-Tracker-Widget
./gradlew assembleDebug
```

En Windows usá `gradlew.bat assembleDebug`. El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`; instalalo desde Android Studio o con `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

1. Mantené presionada la pantalla de inicio y agregá **TuBondi Widget** desde el selector de widgets.
2. Buscá una parada o agregá su código exacto manualmente; si elegís por ruta, podés seleccionar líneas específicas.
3. Ajustá la frecuencia automática (15–60 min) y las alertas, y tocá **Guardar**.
4. Usá **Actualizar** para pedir datos nuevos o tocá el encabezado para abrir la pantalla completa.

<p align="center"><img src="docs/images/configuracion.png" alt="Configuración del widget con buscador de paradas y rutas disponibles" width="280"></p>

## Actualizaciones y notificaciones

El refresco manual consulta el servicio cuando lo solicitás. El refresco periódico usa WorkManager: Android establece un **mínimo de 15 minutos** y puede demorar la ejecución según batería y condiciones del dispositivo. La disponibilidad y la precisión de los arribos dependen de la API de TuBondi.

El selector permite recorrer todas las rutas disponibles. El catálogo de paradas está incluido en la app y no se actualiza automáticamente; existe una fuente municipal candidata, pero su antigüedad, equivalencia de códigos y condiciones de uso no están verificadas. Hasta entonces, podés ingresar manualmente el código exacto de una parada ausente; agregarlo no consulta el servicio. Las líneas y rutas se consultan al abrir la configuración, con un respaldo incluido.

En Android 13 o posterior, las notificaciones requieren permiso. Si lo denegás, podés habilitarlo desde los ajustes de Android. El canal permite silenciar los avisos durante una hora; la app evita repetir una alerta para la misma parada y línea durante diez minutos.

## Desarrollo

El widget está construido con Kotlin y Jetpack Glance; la configuración y la pantalla completa usan Compose. DataStore conserva la configuración por widget, WorkManager programa las actualizaciones y OkHttp consulta el servicio.

```bash
./gradlew testDebugUnitTest
```

Las pruebas unitarias usan fixtures y respuestas simuladas; no necesitan acceso a la API. Para ejecutar y verificar el widget completo, instalá el APK en un dispositivo o emulador y agregalo al launcher.

La sesión de la API se mantiene en memoria mientras vive el proceso. Las preferencias se guardan localmente; al quitar un widget la app inicia la limpieza de su configuración y trabajo periódico. No se registran cookies en los logs HTTP configurados por defecto.

## Referencias

Basado en el comportamiento observado de [Tu-Bondi-Oma](https://github.com/LucioB16/Tu-Bondi-Oma) y en los datos de TuBondi. El acceso al servicio puede cambiar independientemente de esta app.

Licencia: consultá [LICENSE](LICENSE).
