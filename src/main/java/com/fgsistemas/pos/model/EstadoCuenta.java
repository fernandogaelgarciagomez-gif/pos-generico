package com.fgsistemas.pos.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Estado de cuenta de un cliente: ventas a fiado (cargo) y abonos mezclados por fecha,
 * con saldo acumulado. Las ventas canceladas aparecen marcadas pero no suman al saldo.
 */
public record EstadoCuenta(Cliente cliente, List<Movimiento> movimientos, long saldoFinal) {

    public enum Tipo { VENTA, ABONO }

    /**
     * @param cargo   parte fiada de la venta (solo esa, no el total si fue pago mixto)
     * @param ventaId id de la venta, para ver sus productos (null en abonos)
     */
    public record Movimiento(Tipo tipo, LocalDateTime fecha, String concepto, long cargo, long abono, long saldo,
                             Long ventaId, boolean cancelada) {
    }
}
