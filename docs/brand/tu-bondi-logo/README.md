# Tu Bondi — kit de identidad para Android

## Origen
Diseño de pictograma realizado con Tiento y GPT Image. El logo fuente es una imagen rasterizada PNG con transparencia, **no es un SVG/vector editable**.

## Entregables
- `preview_identidad.png`: vista previa de máscaras de iconos de launcher.
- `logo_transparent_1024.png`: versión sin fondo para usos de marca y documentación.
- `play_store_512.png`: icono plano opaco 512 × 512 px para ficha de tienda (revisar antes de publicar).
- `android-res/res/mipmap-anydpi-v26/ic_launcher.xml`: icono adaptativo Android 8.0+.
- `android-res/res/mipmap-anydpi-v26/ic_launcher_round.xml`: alias adaptable para launcher redondo.
- `android-res/res/drawable-nodpi/ic_launcher_foreground.png`: foreground 432 × 432 px, con símbolo dentro de zona segura (66/108 dp).
- `android-res/res/drawable-nodpi/ic_launcher_monochrome.png`: capa monocromática blanca, para themed icons desde Android 13.
- `android-res/res/drawable/ic_launcher_background.xml`: fondo plano #EAF4F6.
- `android-res/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher*.png`: iconos heredados, densidades estándar.

## Integración por coding agent
1. Inspeccionar antes `app/src/main/res` y los recursos existentes del proyecto para no sobreescribir cambios sin control.
2. Incorporar los recursos de `android-res/res` a `app/src/main/res` o al módulo de aplicación correspondiente, resolviendo conflictos de nombres.
3. En `AndroidManifest.xml`, comprobar que `<application>` apunte a `android:icon="@mipmap/ic_launcher"` y, si se usa, `android:roundIcon="@mipmap/ic_launcher_round"`.
4. Verificar en Image Asset Studio / emulator Android: máscaras circular, redondeada, squircle, light/dark y themed icons (Android 13+).
5. Verificar tamaño de la figura y que no haya recortes, sobre todo en pantallas de baja densidad y tamaños 48px.
6. No usar el launcher icon como icono de notificación: generar uno específico solo si se necesita.

## Especificación
- Símbolo: colectivo frontal y recorrido con marcador de parada.
- Paleta orientativa: azul petróleo / teal y acento ámbar; fondo #EAF4F6.
- Imagen fuente raster. No atribuir a estos archivos capacidades vectoriales.
- Los ejemplos de íconos temáticos dependen de la versión Android y del launcher.

La implementación definitiva y cualquier ajuste de recursos debe validarse dentro del repositorio real. Esto es un paquete listo para integrar, no evidencia de una build Android realizada.
