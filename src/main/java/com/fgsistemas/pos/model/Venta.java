package com.fgsistemas.pos.model;

import com.fgsistemas.pos.util.Cantidad;

import java.time.LocalDateTime;

/** Tipos de solo lectura para consultar ventas ya registradas. */
public final class Venta {

    private Venta() {
    }

    /** Renglón de la lista "Ventas del turno". */
    public record Resumen(long id, long folio, LocalDateTime fecha, String usuarioNombre, String clienteNombre,
                          long total, String estado, String metodos, String motivoCancelacion) {

        public boolean cancelada() {
            return "CANCELADA".equals(estado);
        }
    }

    /** Producto vendido. */
    public record Renglon(String codigo, String nombre, TipoVenta tipoVenta, String unidad, long cantidad,
                          long precioUnitario, long importe) {

        public String cantidadTexto() {
            return Cantidad.formatear(cantidad, tipoVenta, unidad);
        }
    }

    /** Pago registrado. */
    public record Pago(MetodoPago metodo, long monto, Long recibido, Long cambio, String referencia) {
    }
}
