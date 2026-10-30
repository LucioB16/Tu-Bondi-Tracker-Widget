# Revisión puntual v2.1

**Estado:** dirección visual base aprobada; esta revisión sigue pendiente de aprobación. No se modificó Kotlin, Compose ni Glance.

## Decisiones representadas en las capturas

- Cuando la última actualización supera el límite de frescura propuesto o falla una actualización, se ocultan todas las ETA en minutos. Se muestra la hora/antigüedad del último dato confiable y una acción para actualizar desde Arribos.
- `Agregar` y `Quitar` modifican un borrador local. `Guardar cambios` lo aplica al widget. Volver con cambios pendientes abre una confirmación con “Seguir editando” y “Descartar”.
- El widget compacto no lleva accesos en el encabezado. Tocar la tarjeta abre Arribos; desde allí se puede actualizar o abrir Configuración. El mediano y el amplio conservan la actualización directa.
- Ajustes muestra los valores iniciales actuales del código: actualización cada 15 min, notificaciones activadas (con permiso concedido en el ejemplo), umbral de 5 min y alto contraste desactivado. El valor de servicio se presenta como “Predeterminado”, sin el nombre técnico.
- La UI muestra los nombres descriptivos del catálogo y usa el barrio como contexto. Los códigos de parada no aparecen en títulos, resultados, chips, atributos accesibles ni contenido de widgets. Los números 42, 40 y 71 identifican líneas de colectivo, no paradas.
- Para una parada sin nombre: usar `Parada en {barrio}` si existe el barrio; si no hay nombre ni ubicación legible, mostrar `Ubicación sin nombre`. No derivar ni inventar calles desde el código.
- Arribos y destinos de ejemplo se basan en el fixture local de respuesta API y están marcados como DEMO; no son datos en vivo. Los nombres de lugar provienen del catálogo local.
- En Glance, las listas se resumen por tamaño; no se simula scroll dentro del widget. Arribos y Configuración sí presentan contenido desplazable y una captura con escala de texto ampliada.

## Decisiones pendientes de aprobación

1. **Frescura de ETA:** recomiendo ocultarlas si el último éxito tiene más de 15 min o si la última consulta falló. Es una regla conservadora para tiempos que cambian minuto a minuto; con actualización automática configurada en 60 min, puede dejar el widget sin ETA durante parte del intervalo. Confirmar este límite o indicar otro.
2. **Compacto sin botones:** confirmar que tocar la tarjeta completa para abrir Arribos y llegar desde allí a Actualizar/Configurar es preferible a conservar un botón directo de actualización.
3. **Borrador y salida:** confirmar que Guardar sea el único paso que aplica cambios y que volver con cambios pendientes pregunte antes de descartarlos.
4. **Ajuste avanzado:** confirmar el texto “Configuración avanzada del servicio / Servicio de datos / Predeterminado”. No se expone el parámetro técnico en el nombre de una parada.
5. **Búsqueda manual:** la app actual permite escribir un código de parada. Para que no aparezca en ninguna pantalla, recomiendo buscar por nombre, calle o barrio y resolver el código internamente. Si una parada manual no tiene metadatos descriptivos, mostrar `Ubicación sin nombre` hasta que la API/catálogo entregue una ubicación; no mostrar el código ni fabricar una dirección. Confirmar si se conserva la entrada manual de códigos bajo esta regla.
6. **Valores por widget:** los valores de la captura son los defaults del código, no los datos persistidos de una instancia concreta del emulador. En implementación, cada ajuste debe mostrar el valor leído para ese widget.

## Base de nombres verificada

- El ejemplo de Duarte Quirós coincide con el nombre y barrio disponibles en el catálogo local; se normalizaron mayúsculas, acentos y guiones solo para presentación.
- Los nombres de H. Irigoyen (Parque Las Tejas), Av. Maipú 355, Hipólito Irigoyen 660/386 y Maestro López (UTN) también provienen del catálogo.
- La etiqueta `BB17` de la v2 no está en el catálogo local y se retiró de la nueva propuesta; no se le asignó una ubicación inventada.

La exportación PNG contiene 22 capturas: cinco pantallas × dos temas y seis variantes de widget × dos temas. Las dimensiones corresponden a marcos HTML/CSS de referencia, no al runtime del launcher/Glance.
