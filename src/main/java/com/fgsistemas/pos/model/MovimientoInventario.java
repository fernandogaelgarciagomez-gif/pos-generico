package com.fgsistemas.pos.model;

import java.time.LocalDateTime;

/** Renglón del kardex. Cantidad y stock resultante en milésimas. */
public record MovimientoInventario(LocalDateTime fecha, String tipo, long cantidad, long stockResultante,
                                   Long referenciaId, String usuarioNombre, String motivo) {
}
