# Firmar y verificar una release

La variante `release` firma el APK cuando están definidas estas cuatro variables de entorno:

- `TUBONDI_RELEASE_STORE_FILE`
- `TUBONDI_RELEASE_STORE_PASSWORD`
- `TUBONDI_RELEASE_KEY_ALIAS`
- `TUBONDI_RELEASE_KEY_PASSWORD`

Las credenciales y el keystore deben quedar fuera del repositorio. En este host se guardan bajo `~/.config/tu-bondi-release/` con permisos privados; el archivo `release-signing.env` contiene secretos y no debe compartirse ni versionarse.

Para compilar el APK firmado:

```bash
set -a
source ~/.config/tu-bondi-release/release-signing.env
set +a
bash gradlew assembleRelease
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`. Verificá la firma antes de distribuirlo:

```bash
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

Conservá copias cifradas del keystore y de sus credenciales en un lugar seguro. Las actualizaciones de esta app deben usar la misma clave; si se pierde, Android no aceptará el APK como actualización de las instalaciones firmadas con ella. No uses la clave debug para publicar.
