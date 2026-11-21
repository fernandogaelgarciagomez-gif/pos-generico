package com.fgsistemas.pos.model;

import com.fgsistemas.pos.service.Autorizacion;
import com.fgsistemas.pos.util.Cantidad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Venta en captura (aún no guardada). Cantidades en milésimas, dinero en centavos.
 * Productos por pieza se acumulan en un renglón; los de peso/fracción van cada uno en su renglón.
 */
public class Carrito {

    public static final class Renglon {
        private final Producto producto;
        private long cantidad;
        private final long precioUnitario;

        Renglon(Producto producto, long cantidad) {
            this.producto = producto;
            this.cantidad = cantidad;
            this.precioUnitario = producto.getPrecioVenta();
        }

        public Producto getProducto() {
            return producto;
        }

        public long getCantidad() {
            return cantidad;
        }

        public long getPrecioUnitario() {
            return precioUnitario;
        }

        public long importe() {
            return Cantidad.importe(precioUnitario, cantidad);
        }

        public String cantidadTexto() {
            return producto.formatear(cantidad);
        }
    }

    private final List<Renglon> renglones = new ArrayList<>();
    private long descuento;
    private Autorizacion autorizacionDescuento;
    private Cliente cliente;

    public Renglon agregar(Producto p, long cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero.");
        }
        if (!p.getTipoVenta().aceptaDecimales()) {
            if (cantidad % Cantidad.UNO != 0) {
                throw new IllegalArgumentException("Este producto se vende por pieza.");
            }
            for (Renglon r : renglones) {
                if (r.producto.getId() == p.getId() && r.precioUnitario == p.getPrecioVenta()) {
                    r.cantidad += cantidad;
                    return r;
                }
            }
        }
        Renglon nuevo = new Renglon(p, cantidad);
        renglones.add(nuevo);
        return nuevo;
    }

    public void cambiarCantidad(Renglon r, long cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero.");
        }
        if (!r.producto.getTipoVenta().aceptaDecimales() && cantidad % Cantidad.UNO != 0) {
            throw new IllegalArgumentException("Este producto se vende por pieza.");
        }
        r.cantidad = cantidad;
    }

    public void quitar(Renglon r) {
        renglones.remove(r);
    }

    /** Cantidad total de un producto en el carrito (para validar stock). */
    public long cantidadDe(long productoId) {
        return renglones.stream().filter(r -> r.producto.getId() == productoId).mapToLong(r -> r.cantidad).sum();
    }

    public long subtotal() {
        return renglones.stream().mapToLong(Renglon::importe).sum();
    }

    public long total() {
        return Math.max(0, subtotal() - descuento);
    }

    /** Piezas + un artículo por cada renglón a granel. */
    public long articulos() {
        long n = 0;
        for (Renglon r : renglones) {
            n += r.producto.getTipoVenta().aceptaDecimales() ? 1 : r.cantidad / Cantidad.UNO;
        }
        return n;
    }

    public boolean vacio() {
        return renglones.isEmpty();
    }

    public void limpiar() {
        renglones.clear();
        descuento = 0;
        autorizacionDescuento = null;
        cliente = null;
    }

    public List<Renglon> getRenglones() {
        return Collections.unmodifiableList(renglones);
    }

    public long getDescuento() {
        return descuento;
    }

    public void setDescuento(long descuento, Autorizacion autorizacion) {
        if (descuento < 0) {
            throw new IllegalArgumentException("El descuento no puede ser negativo.");
        }
        this.descuento = descuento;
        this.autorizacionDescuento = descuento == 0 ? null : autorizacion;
    }

    public Autorizacion getAutorizacionDescuento() {
        return autorizacionDescuento;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }
}
