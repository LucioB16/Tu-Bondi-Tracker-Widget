# Microcopy propuesta v2.2

Las frases entre comillas son el texto propuesto para la interfaz. El resto de la terminología técnica permanece en modelos y contratos internos.

| Contexto | Texto actual / problema | Texto propuesto |
|---|---|---|
| Actualización reciente | “Última actualización correcta” + metadatos explicativos | “Actualizado 08:25” y abajo “Hace 2 min”. Ambos valores salen de la última respuesta exitosa. |
| Datos antiguos | Explicación extensa sobre si los horarios coinciden con el servicio | “Datos desactualizados · Actualizado hace 22 min”. En cada fila: “Estimación vencida”. Debajo, “No mostramos horarios hasta recibir datos nuevos”. |
| Actualización en curso | “Actualizando; la hora corresponde al último éxito.” | “Actualizando…” junto al indicador; la hora y antigüedad existentes siguen asociadas al último éxito. |
| Error con datos previos | “No se pudo actualizar: {error}. Conservamos el último dato válido.” | “No se pudo actualizar · Se conservan los últimos datos”. Si aplica, mantener debajo la edad de esos datos. |
| Error sin datos | Errores genéricos con explicación aparte | “Sin conexión” o “No se pudo actualizar”, según el error conocido, con botón “Reintentar”. |
| Sin paradas | “Agregá lugares para ver sus colectivos.” | “Agregá una parada para ver sus colectivos.” Botón “Configurar widget”. |
| Búsqueda | Campo y encabezado actuales | “Buscar paradas” y placeholder “Calle, barrio o nombre”. En resultados ya agregados, “Agregada” con acción deshabilitada. |
| Sin resultados | Mensaje de búsqueda extensa | “No encontramos paradas” y “Probá con otra calle o barrio”. |
| Selección de líneas | “Líneas de esta parada” más ayuda “Si no elegís líneas, se muestran todas.” | “Líneas” con check visible. Si no hay filtro: estado breve “Todas las líneas”. |
| Cambios | Estado guardado mostrado continuamente | Mostrar “Cambios sin guardar” solo mientras el borrador difiere; botón “Guardar cambios” habilitado en ese estado. Guardado: feedback breve “Cambios guardados”. |
| Volver con borrador | Explica en el diálogo cómo funciona Agregar/Quitar | Título “¿Descartar cambios?”, cuerpo “Se perderán los cambios sin guardar.” Acciones “Seguir editando” y “Descartar”. |
| Aviso en widget pequeño/mediano | “Aviso en Arribos” / “Avisos disponibles · ver en Arribos” | “Avisos · Tocá para ver”. El toque abre Arribos. |
| Aviso en widget amplio | Solo rótulo de aviso | “Desvío por obra” con una o dos líneas del aviso y “Tocá para ver el aviso completo” cuando el texto excede el espacio. |
| Notificaciones | Actual “Línea 10 llega en 5 min a {lugar}” | Conservar: corto y natural. Voz: “Línea 10 llega en 5 minutos a {lugar}”. Nunca incluir códigos técnicos. |
| Accesibilidad | No repetir nombres técnicos internos ni siglas | “Línea 42. Destino: Patricios. Llega en 11 minutos.” Si no hay dato: “Sin horario estimado”. En estado antiguo: “Datos desactualizados. Estimación vencida”. |

La política de frescura conserva el último éxito tras errores, pero deja de presentar los minutos cuando los horarios vencen. La antigüedad se deriva del reloj de última actualización exitosa; no se simula un minuto actual para una respuesta vieja.
