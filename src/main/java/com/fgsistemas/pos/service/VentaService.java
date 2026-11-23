package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.ClienteDao;
import com.fgsistemas.pos.dao.MovimientoInventarioDao;
import com.fgsistemas.pos.dao.MovimientoInventarioDao.Tipo;
import com.fgsistemas.pos.dao.NegocioDao;
import com.fgsistemas.pos.dao.ProductoDao;
import com.fgsistemas.pos.dao.VentaDao;
import com.fgsistemas.pos.model.Carrito;
import com.fgsistemas.pos.model.Cliente;
import com.fgsistemas.pos.model.MetodoPago;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.Venta;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Sesion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ventas y cancelaciones (docs/modulos/14-vender.md y 24-cancelaciones.md). */
public class VentaService {

    /** Un pago del cobro. {@code recibido} solo para efectivo (para calcular el cambio). */
    public record Pago(MetodoPago metodo, long monto, Long recibido, String referencia) {
    }

    public record VentaRegistrada(long id, long folio, long total, long cambio) {
    }

    public record Cancelacion(long folio, Long clienteId, long saldoCliente) {

        /** El cliente ya había abonado y quedó con saldo a favor. */
        public boolean saldoAFavor() {
            return clienteId != null && saldoCliente < 0;
        }
    }

    private final VentaDao ventaDao = new VentaDao();
    private final ProductoDao productoDao = new ProductoDao();
    private final MovimientoInventarioDao movimientoDao = new MovimientoInventarioDao();
    private final ClienteDao clienteDao = new ClienteDao();
    private final NegocioDao negocioDao = new NegocioDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();

    // ---------------------------------------------------------------- configuración para la pantalla

    /** Métodos de pago habilitados en Configuración (efectivo siempre). */
    public Set<MetodoPago> metodosHabilitados() throws SQLException {
        try (Connection c = Database.conectar()) {
            return metodosHabilitados(negocioDao.obtener(c).orElseGet(Negocio::new));
        }
    }

    private static Set<MetodoPago> metodosHabilitados(Negocio n) {
        Set<MetodoPago> s = EnumSet.of(MetodoPago.EFECTIVO);
        if (n.isMetodoTarjeta()) {
            s.add(MetodoPago.TARJETA);
        }
        if (n.isMetodoTransferencia()) {
            s.add(MetodoPago.TRANSFERENCIA);
        }
        if (n.isMetodoFiado()) {
            s.add(MetodoPago.FIADO);
        }
        return s;
    }

    public Negocio configuracion() throws SQLException {
        try (Connection c = Database.conectar()) {
            return negocioDao.obtener(c).orElseGet(Negocio::new);
        }
    }

    /** Descuento en centavos que corresponde a un porcentaje del subtotal (HALF_UP). */
    public static long descuentoPorPorcentaje(long subtotal, BigDecimal porcentaje) {
        return Dinero.porcentaje(subtotal, porcentaje);
    }

    // ---------------------------------------------------------------- cobrar

    /**
     * Registra la venta en UNA transacción: venta + detalle + pagos, resta stock con kardex VENTA y,
     * si hay parte fiada, la suma al saldo del cliente. Si algo falla, no queda nada guardado.
     *
     * @throws IllegalArgumentException datos inválidos (mensaje para el usuario)
     * @throws IllegalStateException    sin turno abierto
     */
    public VentaRegistrada cobrar(Carrito carrito, List<Pago> pagos) throws SQLException {
        Sesion.requiere(Permiso.VENDER);
        if (Sesion.turno() == null) {
            throw new IllegalStateException("No hay turno abierto.");
        }
        if (carrito == null || carrito.vacio()) {
            throw new IllegalArgumentException("La venta no tiene productos.");
        }
        for (Carrito.Renglon r : carrito.getRenglones()) {
            if (r.getCantidad() <= 0 || r.getCantidad() > Cantidad.MAXIMO) {
                throw new IllegalArgumentException("Revisa la cantidad de \"" + r.getProducto().getNombre() + "\".");
            }
        }
        long subtotal = carrito.subtotal();
        long descuento = carrito.getDescuento();
        if (descuento > subtotal) {
            throw new IllegalArgumentException("El descuento no puede ser mayor al subtotal.");
        }
        long total = subtotal - descuento;
        Cliente cliente = carrito.getCliente();

        // Pagos: cuadran exacto al centavo
        if (pagos == null || pagos.isEmpty()) {
            throw new IllegalArgumentException("Indica cómo se paga la venta.");
        }
        long suma = 0;
        long cambio = 0;
        boolean hayFiado = false;
        for (Pago p : pagos) {
            if (p.monto() <= 0) {
                throw new IllegalArgumentException("Cada pago debe ser mayor a cero.");
            }
            suma += p.monto();
            if (p.metodo() == MetodoPago.FIADO) {
                hayFiado = true;
            }
            if (p.metodo() == MetodoPago.EFECTIVO && p.recibido() != null) {
                if (p.recibido() < p.monto()) {
                    throw new IllegalArgumentException("El efectivo recibido es menor que el monto a pagar.");
                }
                cambio += p.recibido() - p.monto();
            }
        }
        if (suma != total) {
            throw new IllegalArgumentException("Los pagos (" + Dinero.formatear(suma) + ") no cuadran con el total ("
                    + Dinero.formatear(total) + ").");
        }
        if (hayFiado) {
            Sesion.requiere(Permiso.FIADOS_VENDER);
            if (cliente == null) {
                throw new IllegalArgumentException("Para vender a fiado elige un cliente (F4).");
            }
        }
        long fiado = pagos.stream().filter(p -> p.metodo() == MetodoPago.FIADO).mapToLong(Pago::monto).sum();
        long usuarioId = Sesion.usuario().getId();
        long turnoId = Sesion.turno().getId();
        final long cambioFinal = cambio;

        return Database.enTransaccion(c -> {
            Negocio config = negocioDao.obtener(c).orElseGet(Negocio::new);
            Set<MetodoPago> habilitados = metodosHabilitados(config);
            for (Pago p : pagos) {
                if (!habilitados.contains(p.metodo())) {
                    throw new IllegalArgumentException("El método " + p.metodo().etiqueta() + " está deshabilitado.");
                }
            }
            Long autorizo = null;
            if (descuento > 0) {
                autorizo = Autorizaciones.verificar(c, Permiso.DESCUENTOS, carrito.getAutorizacionDescuento());
                BigDecimal maximo = BigDecimal.valueOf(config.getDescuentoMaximoPct());
                if (BigDecimal.valueOf(descuento).multiply(BigDecimal.valueOf(100))
                        .compareTo(BigDecimal.valueOf(subtotal).multiply(maximo)) > 0) {
                    throw new IllegalArgumentException("El descuento supera el máximo permitido ("
                            + config.getDescuentoMaximoPct() + "%).");
                }
            }
            if (cliente != null && clienteDao.buscarPorId(c, cliente.getId()).filter(Cliente::isActivo).isEmpty()) {
                throw new IllegalArgumentException("El cliente ya no está activo.");
            }

            // Stock: validar por producto (sumando renglones) con datos frescos de la BD.
            Map<Long, Producto> productos = new LinkedHashMap<>();
            Map<Long, Long> cantidades = new LinkedHashMap<>();
            for (Carrito.Renglon r : carrito.getRenglones()) {
                long id = r.getProducto().getId();
                if (!productos.containsKey(id)) {
                    Producto p = productoDao.buscarPorId(c, id)
                            .filter(Producto::isActivo)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "\"" + r.getProducto().getNombre() + "\" ya no está disponible."));
                    productos.put(id, p);
                }
                cantidades.merge(id, r.getCantidad(), Long::sum);
            }
            for (Map.Entry<Long, Long> e : cantidades.entrySet()) {
                Producto p = productos.get(e.getKey());
                if (!config.isPermitirStockNegativo() && p.getStock() < e.getValue()) {
                    throw new IllegalArgumentException("No hay suficiente \"" + p.getNombre() + "\": quedan "
                            + p.stockTexto() + " y la venta lleva " + p.formatear(e.getValue()) + ".");
                }
            }

            long folio = ventaDao.siguienteFolio(c);
            long ventaId = ventaDao.insertar(c, folio, turnoId, usuarioId, cliente == null ? null : cliente.getId(),
                    subtotal, descuento, total, autorizo);
            for (Carrito.Renglon r : carrito.getRenglones()) {
                Producto p = productos.get(r.getProducto().getId());
                ventaDao.insertarDetalle(c, ventaId, p.getId(), r.getCantidad(), r.getPrecioUnitario(), p.getCosto(),
                        Cantidad.importe(r.getPrecioUnitario(), r.getCantidad()));
            }
            for (Map.Entry<Long, Long> e : cantidades.entrySet()) {
                Producto p = productos.get(e.getKey());
                long nuevo = p.getStock() - e.getValue();
                productoDao.actualizarStock(c, p.getId(), nuevo);
                movimientoDao.registrar(c, p.getId(), Tipo.VENTA, -e.getValue(), nuevo, ventaId, usuarioId, null);
            }
            for (Pago p : pagos) {
                boolean efectivoConRecibido = p.metodo() == MetodoPago.EFECTIVO && p.recibido() != null;
                ventaDao.insertarPago(c, ventaId, p.metodo(), p.monto(),
                        efectivoConRecibido ? p.recibido() : null,
                        efectivoConRecibido ? p.recibido() - p.monto() : null,
                        p.referencia());
            }
            if (fiado > 0) {
                clienteDao.sumarSaldo(c, cliente.getId(), fiado);
            }
            if (autorizo != null) {
                bitacoraDao.registrar(c, usuarioId, "DESCUENTO_AUTORIZADO", "Venta #" + folio + " · "
                        + Dinero.formatear(descuento) + " · admin #" + autorizo);
            }
            return new VentaRegistrada(ventaId, folio, total, cambioFinal);
        });
    }

    // ---------------------------------------------------------------- consultas

    public List<Venta.Resumen> ventasDelTurno() throws SQLException {
        Sesion.requiere(Permiso.VENDER);
        if (Sesion.turno() == null) {
            return List.of();
        }
        try (Connection c = Database.conectar()) {
            return ventaDao.listarDelTurno(c, Sesion.turno().getId());
        }
    }

    public Venta.Resumen resumen(long ventaId) throws SQLException {
        Sesion.requiere(Permiso.VENDER);
        try (Connection c = Database.conectar()) {
            return ventaDao.resumen(c, ventaId).orElseThrow(() -> new IllegalArgumentException("La venta no existe."));
        }
    }

    public List<Venta.Renglon> renglones(long ventaId) throws SQLException {
        Sesion.requiere(Permiso.VENDER);
        try (Connection c = Database.conectar()) {
            return ventaDao.renglones(c, ventaId);
        }
    }

    public List<Venta.Pago> pagos(long ventaId) throws SQLException {
        Sesion.requiere(Permiso.VENDER);
        try (Connection c = Database.conectar()) {
            return ventaDao.pagos(c, ventaId);
        }
    }

    // ---------------------------------------------------------------- cancelar

    /**
     * Cancela una venta (nunca se borra): estado CANCELADA, regresa stock con kardex CANCELACION y
     * resta la parte fiada del saldo del cliente. Solo ventas de un turno que sigue abierto.
     */
    public Cancelacion cancelar(long ventaId, String motivo, Autorizacion autorizacion) throws SQLException {
        Sesion.requiere(Permiso.VENDER);
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo de la cancelación es obligatorio.");
        }
        long usuarioId = Sesion.usuario().getId();
        return Database.enTransaccion(c -> {
            Long autorizo = Autorizaciones.verificar(c, Permiso.CANCELAR_VENTA, autorizacion);
            VentaDao.Estado v = ventaDao.estado(c, ventaId)
                    .orElseThrow(() -> new IllegalArgumentException("La venta no existe."));
            if (!"COMPLETADA".equals(v.estado())) {
                throw new IllegalArgumentException("La venta #" + v.folio() + " ya estaba cancelada.");
            }
            if (!"ABIERTO".equals(v.turnoEstado())) {
                throw new IllegalArgumentException("La venta #" + v.folio() + " es de un turno ya cortado: "
                        + "no se puede cancelar (haz una devolución en el turno actual).");
            }
            if (new com.fgsistemas.pos.dao.DevolucionDao().totalDevuelto(c, ventaId) > 0) {
                // Errores humanos: cancelar después de una devolución regresaría stock y dinero dos veces.
                throw new IllegalArgumentException("La venta #" + v.folio() + " ya tiene devoluciones: no se puede "
                        + "cancelar completa. Devuelve los productos que faltan (D).");
            }
            ventaDao.marcarCancelada(c, ventaId, usuarioId, autorizo, motivo.trim());
            for (VentaDao.DetalleCrudo d : ventaDao.detalleCrudo(c, ventaId)) {
                Producto p = productoDao.buscarPorId(c, d.productoId()).orElseThrow();
                long nuevo = p.getStock() + d.cantidad();
                productoDao.actualizarStock(c, p.getId(), nuevo);
                movimientoDao.registrar(c, p.getId(), Tipo.CANCELACION, d.cantidad(), nuevo, ventaId, usuarioId,
                        motivo.trim());
            }
            long fiado = ventaDao.pagos(c, ventaId).stream()
                    .filter(p -> p.metodo() == MetodoPago.FIADO).mapToLong(Venta.Pago::monto).sum();
            long saldo = 0;
            if (fiado > 0 && v.clienteId() != null) {
                saldo = clienteDao.sumarSaldo(c, v.clienteId(), -fiado);
            }
            bitacoraDao.registrar(c, usuarioId, "VENTA_CANCELADA", "Venta #" + v.folio() + " · " + motivo.trim()
                    + (autorizo == null ? "" : " · autorizó admin #" + autorizo));
            return new Cancelacion(v.folio(), fiado > 0 ? v.clienteId() : null, saldo);
        });
    }

    /** Porcentaje que representa un descuento sobre el subtotal (1 decimal), para mostrar. */
    public static String porcentajeTexto(long descuento, long subtotal) {
        if (subtotal <= 0) {
            return "0%";
        }
        return BigDecimal.valueOf(descuento).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(subtotal), 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }
}
