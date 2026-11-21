package com.fgsistemas.pos.model;

import java.time.LocalDateTime;

/** Encabezado de una entrada de mercancía (para el historial). */
public record EntradaMercancia(long id, LocalDateTime fecha, String usuarioNombre, String proveedor,
                               String factura, String notas, long totalCosto, int renglones) {
}
