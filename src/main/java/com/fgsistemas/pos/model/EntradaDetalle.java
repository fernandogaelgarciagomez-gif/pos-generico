package com.fgsistemas.pos.model;

import com.fgsistemas.pos.util.Cantidad;

/** Renglón guardado de una entrada (para ver el detalle en el historial). */
public record EntradaDetalle(String codigo, String nombre, TipoVenta tipoVenta, String unidad,
                             long cantidad, long costoUnitario) {

    public long subtotal() {
        return Cantidad.importe(costoUnitario, cantidad);
    }
}
