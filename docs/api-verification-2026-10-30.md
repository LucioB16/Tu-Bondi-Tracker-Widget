# Verificación del contrato API de Tu Bondi

Consulta de solo lectura realizada el 2026-10-30 a `https://micronauta4.dnsalias.net`, con `conf=cbaciudad`. Se inicializó una sesión efímera y no se guardaron cookies, huellas ni credenciales.

## Respuestas observadas

- `lineasyrutas`: HTTP 200, 73 líneas. Cada línea incluye `linea_id` como texto, `linea_nombre`, `cliente` numérico, `color` como texto, y rutas con `ruta_id`, `ruta_nombre` y `sentido`.
- `seleccionatraza` para una ruta: HTTP 200, 27 paradas. `paradas[]` incluye `codigo`, `linea`, `cliente`, `color`, `nombre`, `parada_nombre`, `lat` y `lon`. Las coordenadas de la respuesta probada son cadenas numéricas, por ejemplo `"-31.364435"` y `"-64.187030"`.
- `proximos_arribos` para una parada de esa selección: HTTP 200. Cada arribo probado incluye `linea`, `linea_nombre`, `cliente` numérico y `color` como texto.

## Discrepancia de colores y decisión

En el mismo muestreo, la línea visible `A` con la misma identidad de API (ID de línea `1`, cliente `408`) tuvo `#0099CB` en el catálogo y en arribos, pero `#f9014b` en las filas de selección de recorrido. Se interpreta el campo `color` de `seleccionatraza` como color de la traza/selección, no como una fuente confiable para pintar el número de línea. La UI resuelve el color desde el catálogo vigente por `(cliente, linea_id)`. Si el color de arribos es válido pero contradice al catálogo para esa misma clave, la UI usa el indicador neutro. La clave faltante o duplicada con colores contradictorios también queda neutra. Nunca se usa el nombre visible de línea como clave.

Los fixtures anteriores no reflejaban completamente la estructura viva: selección guarda lat/lon como cadenas y usa `linea`, `cliente`, `nombre`; el DTO anterior esperaba números, `lineas[]` y `descripcion`. El DTO se amplió para aceptar la forma observada además de los ejemplos históricos. Una respuesta de ruta confirma presencia de coordenadas, pero no implica cobertura de todas las paradas del catálogo offline.

## Alcance de la observación

La prueba confirma los campos y tipos de una consulta actual de catálogo, selección y arribos. No establece que toda parada tenga coordenadas ni que todos los colores sean completos o consistentes; el comportamiento sigue siendo conservador para datos faltantes, inválidos o discordantes.
