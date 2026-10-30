# Tu Bondi Widget

**Consultá los próximos colectivos de Córdoba desde la pantalla de inicio.**

Elegí tus paradas y mirá arribos, destinos y avisos sin abrir otra app. Cada widget conserva su propia configuración.

<p align="center">
  <img src="docs/images/widget.png" alt="Captura real del widget amplio con dos paradas y cuatro arribos de prueba local" width="260">
  <img src="docs/images/arribos.png" alt="Captura real de Arribos con la última actualización y datos simulados de dos paradas" width="260">
</p>

<p align="center"><sub>Capturas reales en Android API 36. Los arribos provienen de un stub local de QA; no son información en vivo.</sub></p>

## Qué podés hacer

- **Consultar desde el inicio.** El widget muestra tiempos estimados, destinos, distancias y avisos.
- **Elegir tus paradas.** Buscá por nombre, calle o barrio. Los identificadores técnicos se usan internamente y no aparecen en la interfaz.
- **Separar contextos.** Cada widget puede tener su propia selección de paradas y líneas.
- **Configurar alertas.** Activá notificaciones de arribos próximos y elegí el umbral de tiempo.
- **Entender la vigencia.** Se muestra la última actualización correcta; si falla una consulta, los datos válidos se conservan. Las ETA se ocultan al superar 15 minutos desde ese éxito.
- **Ajustar la lectura.** El modo de alto contraste se aplica al widget y a la pantalla completa; los controles exponen etiquetas y estados semánticos.

## Instalación y primeros pasos

La app requiere **Android 8.0 (API 26) o superior**. Para compilar necesitás Android Studio, Android SDK 35 y JDK 17 o superior.

```bash
git clone https://github.com/LucioB16/Tu-Bondi-Tracker-Widget.git
cd Tu-Bondi-Tracker-Widget
bash gradlew assembleDebug
```

En Windows usá `gradlew.bat assembleDebug`. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`; instalalo desde Android Studio o con:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

1. Mantené presionada la pantalla de inicio y agregá **TuBondi Widget** desde el selector de widgets.
2. Buscá por calle, barrio o nombre, o elegí una parada desde Lugares frecuentes o una ruta disponible.
3. **Agregar** y **Quitar** modifican un borrador. Tocá **Guardar cambios** para aplicarlo; al volver con cambios pendientes podés seguir editando o descartarlos.
4. Configurá el intervalo (15–60 min), las notificaciones, su umbral y el contraste. Android puede demorar las tareas periódicas.
5. Tocá el widget para abrir Arribos; desde ahí podés actualizar y abrir Ajustes.

<p align="center"><img src="docs/images/configuracion.png" alt="Captura real de Configuración, con búsqueda descriptiva, paradas elegidas y Guardar cambios" width="260"></p>
<p align="center"><sub>La búsqueda del ejemplo usa nombres de lugares; los resultados y arribos de las capturas son simulados.</sub></p>

## Datos y actualizaciones

La actualización manual consulta el servicio cuando la solicitás. El refresco periódico usa WorkManager: Android establece un **mínimo de 15 minutos** y puede demorar la ejecución según la batería y las condiciones del dispositivo. El intervalo elegido puede llegar a 60 minutos.

La app calcula la edad desde el último éxito. Las estimaciones en minutos se ocultan a los 15 minutos, aunque el intervalo elegido sea mayor; una consulta fallida no invalida datos que aún estén dentro de ese margen. Las líneas y rutas se consultan al abrir Configuración; si esa consulta falla, se usa el respaldo incluido.

La búsqueda usa un catálogo local integrado en la app. No se descarga ni actualiza automáticamente: una fuente municipal candidata todavía requiere verificar antigüedad, equivalencia de códigos y condiciones de uso. Si una parada no aparece en la búsqueda, se puede intentar encontrarla desde una ruta disponible. No hay ingreso manual de códigos técnicos.

En Android 13 o posterior, las notificaciones requieren permiso. Si lo denegás, podés habilitarlo desde los ajustes de Android. El canal permite silenciar los avisos durante una hora; la app evita repetir una alerta para la misma parada y línea durante diez minutos.

## Desarrollo y QA

El widget está construido con Kotlin y Jetpack Glance; la configuración y la pantalla completa usan Compose. DataStore conserva la configuración por widget, WorkManager programa las actualizaciones y OkHttp consulta el servicio.

```bash
bash gradlew testDebugUnitTest
```

Las pruebas unitarias usan fixtures y respuestas simuladas; no necesitan acceso a la API. Para probar el widget completo, instalá el APK en un dispositivo o emulador y agregalo al launcher.

El build debug usa la URL HTTPS de producción por defecto. Para QA aislado, iniciá el stub local, conectá el AVD con `adb reverse` y compilá debug con el override local:

```bash
python3 tools/mock_tubondi_api.py --mode success
adb reverse tcp:8765 tcp:8765
bash gradlew assembleDebug -PtubondiApiBaseUrl=http://127.0.0.1:8765
```

El stub escucha exclusivamente en loopback y no contacta el servicio de producción. La variante release fija la URL HTTPS del servicio.

Los flows de Maestro se ejecutan **de a uno** sobre un emulador con el widget ya colocado; no los lances juntos porque comparten el mismo dispositivo. Los de configuración esperan que esté guardada la parada Duarte Quirós y que H. Irigoyen (Parque Las Tejas) siga sin agregar; uno comprueba el bloqueo de duplicados y el otro descarta el borrador.

Para `*-408`, con la caché lista, fijá el modo y la demora con estos controles del stub:

```bash
curl 'http://127.0.0.1:8765/__control/mode?value=408'
curl 'http://127.0.0.1:8765/__control/delay?ms=10000'
maestro test --udid emulator-5554 flows/c4-fullscreen-408.yaml
maestro test --udid emulator-5554 flows/c4-launcher-408.yaml
curl 'http://127.0.0.1:8765/__control/mode?value=success'
curl 'http://127.0.0.1:8765/__control/delay?ms=0'
```

La sesión del stub se mantiene en memoria mientras vive el proceso. Las preferencias se guardan localmente; al quitar una instancia se cancelan sus trabajos asociados y se purga su configuración sin afectar otros widgets. Los logs HTTP configurados por defecto no registran cookies.

## Referencias

Basado en el comportamiento observado de [Tu-Bondi-Oma](https://github.com/LucioB16/Tu-Bondi-Oma) y en los datos de TuBondi. El acceso al servicio puede cambiar independientemente de esta app.

Licencia: consultá [LICENSE](LICENSE).
