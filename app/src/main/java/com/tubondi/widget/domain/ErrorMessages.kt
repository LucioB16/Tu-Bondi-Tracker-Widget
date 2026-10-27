package com.tubondi.widget.domain

import java.io.IOException

/**
 * D5: los errores que ve el usuario son genéricos; nunca se expone el mensaje
 * crudo de la excepción (filtra URL del backend y detalles de OkHttp).
 */
fun friendlyErrorMessage(ex: Throwable): String = when {
    ex.message?.contains("408") == true -> "Sin servicio ahora"
    ex is IOException -> "Sin conexión con TuBondi"
    else -> "No se pudo actualizar"
}
