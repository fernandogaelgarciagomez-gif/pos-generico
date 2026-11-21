package com.fgsistemas.pos.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Cifras de caja de un turno (o de todo un día). Todo en centavos; solo cuentan las ventas COMPLETADAS.
 * Lo usan el Resumen del día, el Corte de caja y Cortes pasados, para que los números coincidan.
 *
 * @param fondo          fondo inicial del turno (en "todo el día": suma de fondos de los turnos de hoy)
 * @param ventas         monto cobrado por método (efectivo = monto exacto, ya sin el cambio)
 * @param numPorMetodo   número de ventas que usaron cada método (una venta mixta cuenta en cada uno)
 * @param abonos         abonos de fiados por método
 * @param ganancia       Σ (precio − costo) × cantidad − descuentos; null si el usuario no puede verla
 * @param porHora        total vendido por hora del día (índice 0–23)
 */
public record ResumenCaja(long fondo, Map<MetodoPago, Long> ventas, Map<MetodoPago, Integer> numPorMetodo,
                          Map<MetodoPago, Long> abonos, long entradas, long retiros, int numVentas,
                          long totalVendido, long descuentos, int numCanceladas, long montoCanceladas,
                          Long ganancia, List<ProductoVendido> top, long[] porHora) {

    /** Producto del "Top 10" (cantidad en milésimas). */
    public record ProductoVendido(String codigo, String nombre, TipoVenta tipoVenta, String unidad, long cantidad,
                                  long importe) {
    }

    public long ventas(MetodoPago m) {
        return ventas.getOrDefault(m, 0L);
    }

    public int numPorMetodo(MetodoPago m) {
        return numPorMetodo.getOrDefault(m, 0);
    }

    public long abonos(MetodoPago m) {
        return abonos.getOrDefault(m, 0L);
    }

    /** Abonos con tarjeta + transferencia (no entran a la caja). */
    public long abonosOtros() {
        return abonos(MetodoPago.TARJETA) + abonos(MetodoPago.TRANSFERENCIA);
    }

    public long totalAbonos() {
        return abonos(MetodoPago.EFECTIVO) + abonosOtros();
    }

    /** Fondo + ventas en efectivo + abonos en efectivo + entradas − retiros. */
    public long efectivoEsperado() {
        return fondo + ventas(MetodoPago.EFECTIVO) + abonos(MetodoPago.EFECTIVO) + entradas - retiros;
    }

    /** Total vendido ÷ número de ventas, redondeado al centavo (HALF_UP). */
    public long ticketPromedio() {
        if (numVentas == 0) {
            return 0;
        }
        return BigDecimal.valueOf(totalVendido).divide(BigDecimal.valueOf(numVentas), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
