package com.fgsistemas.pos.model;

import java.time.LocalDateTime;

/**
 * Corte de caja (tabla {@code cortes}). Antes de guardarse tiene {@code id = 0} (vista previa del Paso 2).
 *
 * @param usuarioNombre      quien hizo el corte
 * @param turnoUsuarioNombre quien abrió el turno
 * @param montoCanceladas    no se guarda: se calcula de las ventas del turno (no cambian tras el corte)
 * @param ganancia           null si el usuario no puede verla
 */
public record Corte(long id, long turnoId, String usuarioNombre, String turnoUsuarioNombre, LocalDateTime fecha,
                    LocalDateTime abiertoEn, long fondoInicial, long ventasEfectivo, long ventasTarjeta,
                    long ventasTransferencia, long ventasFiado, long abonosEfectivo, long abonosOtros,
                    long entradasCaja, long retirosCaja, long efectivoEsperado, long efectivoContado,
                    long diferencia, int numVentas, int numCanceladas, long montoCanceladas, Long ganancia,
                    String notas) {

    /** Total vendido = suma de lo cobrado por todos los métodos (ventas completadas). */
    public long totalVendido() {
        return ventasEfectivo + ventasTarjeta + ventasTransferencia + ventasFiado;
    }

    /**
     * Total con todo y fondo: lo que debe haber en caja (fondo + efectivo + abonos en efectivo + entradas
     * − retiros) más lo cobrado con tarjeta y transferencia (ventas y abonos). El fiado no cuenta: no se cobró.
     */
    public long totalConFondo() {
        return efectivoEsperado + ventasTarjeta + ventasTransferencia + abonosOtros;
    }

    /** Arma el corte a partir de las cifras del turno y lo contado. */
    public static Corte de(Turno turno, ResumenCaja r, long contado, String notas, String usuarioNombre,
                           LocalDateTime fecha) {
        long esperado = r.efectivoEsperado();
        return new Corte(0, turno.getId(), usuarioNombre, turno.getUsuarioNombre(), fecha, turno.getAbiertoEn(),
                r.fondo(), r.ventas(MetodoPago.EFECTIVO), r.ventas(MetodoPago.TARJETA),
                r.ventas(MetodoPago.TRANSFERENCIA), r.ventas(MetodoPago.FIADO), r.abonos(MetodoPago.EFECTIVO),
                r.abonosOtros(), r.entradas(), r.retiros(), esperado, contado, contado - esperado, r.numVentas(),
                r.numCanceladas(), r.montoCanceladas(), r.ganancia(), notas);
    }
}
