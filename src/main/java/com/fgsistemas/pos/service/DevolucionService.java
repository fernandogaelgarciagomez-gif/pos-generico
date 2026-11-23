package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.DevolucionDao;
import com.fgsistemas.pos.dao.DevolucionDao.VentaBase;
import com.fgsistemas.pos.dao.DevolucionDao.Vendido;
import com.fgsistemas.pos.dao.MovimientoCajaDao;
import com.fgsistemas.pos.dao.MovimientoInventarioDao;
import com.fgsistemas.pos.dao.ProductoDao;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Sesion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Devolución parcial de una venta (24-cancelaciones.md, Fase 9): regresa al stock algunos productos y devuelve
 * su importe en efectivo como retiro de caja del turno actual. Sirve también para ventas de turnos ya cortados.
 * <p>
 * Reglas (decisiones 59–62):
 * <ul>
 *   <li>Nunca se devuelve más de lo vendido menos lo ya devuelto (por producto).</li>
 *   <li>El reembolso es proporcional al importe cobrado del producto y al descuento de la venta, y nunca
 *       pasa del total de la venta menos lo ya devuelto.</li>
 *   <li>Ventas con parte a fiado no se devuelven por aquí: se cancelan completas.</li>
 *   <li>El efectivo de la caja debe alcanzar para el reembolso.</li>
 *   <li>El cajero necesita autorización de admin (como cancelar).</li>
 * </ul>
 */
public class DevolucionService {

    /** Un producto de la venta con lo que aún se puede devolver. */
    public record Devolvible(long productoId, String codigo, String nombre, TipoVenta tipo, String unidad,
                             long vendido, long devuelto, long importeVendido) {
        public long disponible() {
            return vendido - devuelto;
        }
    }

    /** Lo que se quiere devolver de un producto (milésimas). */
    public record Linea(long productoId, long cantidad) {
    }

    public record Resultado(long devolucionId, long folio, long total) {
    }

    private final DevolucionDao dao = new DevolucionDao();
    private final ProductoDao productoDao = new ProductoDao();
    private final MovimientoInventarioDao movimientoDao = new MovimientoInventarioDao();
    private final MovimientoCajaDao cajaDao = new MovimientoCajaDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();

    /** Id de la venta con ese folio. */
    public long ventaPorFolio(long folio) throws SQLException {
        Sesion.requiere(Permiso.VENDER);
        try (Connection c = Database.conectar()) {
            return dao.idPorFolio(c, folio)
                    .orElseThrow(() -> new IllegalArgumentException("No existe la venta #" + folio + "."));
        }
    }

    /** Productos de la venta y cuánto se puede devolver de cada uno. Valida que la venta se pueda devolver. */
    public List<Devolvible> devolvibles(long ventaId) throws SQLException {
        Sesion.requiere(Permiso.VENDER);
        try (Connection c = Database.conectar()) {
            validarVenta(dao.venta(c, ventaId).orElseThrow(() -> new IllegalArgumentException("La venta no existe.")));
            return devolvibles(c, ventaId);
        }
    }

    /** Reembolso que corresponde a {@code lineas} (vista previa, sin guardar). */
    public long reembolso(long ventaId, List<Linea> lineas) throws SQLException {
        Sesion.requiere(Permiso.VENDER);
        try (Connection c = Database.conectar()) {
            VentaBase v = dao.venta(c, ventaId).orElseThrow(() -> new IllegalArgumentException("La venta no existe."));
            return calcular(c, v, lineas, false).total;
        }
    }

    public Resultado devolver(long ventaId, List<Linea> lineas, String motivo, Autorizacion autorizacion)
            throws SQLException {
        Sesion.requiere(Permiso.VENDER);
        if (Sesion.turno() == null) {
            throw new IllegalStateException("No hay turno abierto: el reembolso sale de la caja del turno.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo de la devolución es obligatorio.");
        }
        long usuarioId = Sesion.usuario().getId();
        long turnoId = Sesion.turno().getId();
        return Database.enTransaccion(c -> {
            Long autorizo = Autorizaciones.verificar(c, Permiso.CANCELAR_VENTA, autorizacion);
            VentaBase v = dao.venta(c, ventaId).orElseThrow(() -> new IllegalArgumentException("La venta no existe."));
            Calculo k = calcular(c, v, lineas, true);
            EfectivoEnCaja.exigir(c, turnoId, k.total, "el reembolso");
            long id = dao.insertar(c, ventaId, turnoId, usuarioId, autorizo, motivo.trim(), k.total);
            for (Map.Entry<Vendido, long[]> e : k.renglones.entrySet()) {
                Vendido vd = e.getKey();
                long cantidad = e.getValue()[0];
                dao.insertarDetalle(c, id, vd.productoId(), cantidad, e.getValue()[1], vd.costoUnitario());
                Producto p = productoDao.buscarPorId(c, vd.productoId()).orElseThrow();
                long nuevo = p.getStock() + cantidad;
                productoDao.actualizarStock(c, p.getId(), nuevo);
                movimientoDao.registrar(c, p.getId(), MovimientoInventarioDao.Tipo.DEVOLUCION, cantidad, nuevo, id,
                        usuarioId, "Venta #" + v.folio() + " · " + motivo.trim());
            }
            cajaDao.registrar(c, turnoId, usuarioId, MovimientoCajaDao.Tipo.RETIRO, k.total,
                    "Devolución #" + id + " de la venta #" + v.folio());
            bitacoraDao.registrar(c, usuarioId, "DEVOLUCION", "Devolución #" + id + " · venta #" + v.folio() + " · "
                    + Dinero.formatear(k.total) + " · " + motivo.trim()
                    + (autorizo == null ? "" : " · autorizó admin #" + autorizo));
            return new Resultado(id, v.folio(), k.total);
        });
    }

    // ------------------------------------------------------------------ cálculo

    private static final class Calculo {
        final Map<Vendido, long[]> renglones = new LinkedHashMap<>(); // [cantidad, importe]
        long total;
    }

    private Calculo calcular(Connection c, VentaBase v, List<Linea> lineas, boolean estricto) throws SQLException {
        validarVenta(v);
        if (lineas == null || lineas.stream().noneMatch(l -> l.cantidad() > 0)) {
            throw new IllegalArgumentException("Indica cuánto se devuelve de al menos un producto.");
        }
        Map<Long, Vendido> vendidos = new LinkedHashMap<>();
        for (Vendido vd : dao.vendido(c, v.id())) {
            vendidos.put(vd.productoId(), vd);
        }
        Map<Long, Long> devuelto = dao.devueltoPorProducto(c, v.id());
        Map<Long, Long> pedido = new LinkedHashMap<>();
        for (Linea l : lineas) {
            if (l.cantidad() < 0) {
                throw new IllegalArgumentException("La cantidad a devolver no puede ser negativa.");
            }
            if (l.cantidad() > 0) {
                pedido.merge(l.productoId(), l.cantidad(), Long::sum);
            }
        }
        Calculo k = new Calculo();
        for (Map.Entry<Long, Long> e : pedido.entrySet()) {
            Vendido vd = vendidos.get(e.getKey());
            if (vd == null) {
                throw new IllegalArgumentException("Un producto no es de la venta #" + v.folio() + ".");
            }
            long q = e.getValue();
            long disponible = vd.cantidad() - devuelto.getOrDefault(vd.productoId(), 0L);
            if (q > disponible) {
                throw new IllegalArgumentException("De \"" + vd.nombre() + "\" solo se pueden devolver "
                        + Cantidad.formatear(Math.max(disponible, 0), vd.tipo(), vd.unidad()) + ".");
            }
            if (!vd.tipo().aceptaDecimales() && q % Cantidad.UNO != 0) {
                throw new IllegalArgumentException("\"" + vd.nombre() + "\" se vende por pieza: devuelve piezas enteras.");
            }
            // Proporcional a lo cobrado del producto y al descuento de la venta (HALF_UP a centavos).
            BigDecimal importe = BigDecimal.valueOf(vd.importe()).multiply(BigDecimal.valueOf(q))
                    .multiply(BigDecimal.valueOf(v.total()));
            BigDecimal divisor = BigDecimal.valueOf(vd.cantidad()).multiply(BigDecimal.valueOf(Math.max(v.subtotal(), 1)));
            long reembolso = importe.divide(divisor, 0, RoundingMode.HALF_UP).longValueExact();
            k.renglones.put(vd, new long[]{q, reembolso});
            k.total += reembolso;
        }
        long maximo = v.total() - dao.totalDevuelto(c, v.id());
        if (k.total > maximo) {
            // Por redondeo, lo último que se devuelve se ajusta para no pasar del total cobrado.
            long exceso = k.total - maximo;
            for (long[] r : k.renglones.values()) {
                long quitar = Math.min(exceso, r[1]);
                r[1] -= quitar;
                exceso -= quitar;
            }
            k.total = maximo;
        }
        return k;
    }

    private static void validarVenta(VentaBase v) {
        if (!"COMPLETADA".equals(v.estado())) {
            throw new IllegalArgumentException("La venta #" + v.folio() + " está cancelada: no hay nada que devolver.");
        }
        if (v.tieneFiado()) {
            throw new IllegalArgumentException("La venta #" + v.folio() + " tiene parte a fiado: no se puede devolver "
                    + "por partes. Cancélala completa (C) para que se ajuste el saldo del cliente.");
        }
    }

    private List<Devolvible> devolvibles(Connection c, long ventaId) throws SQLException {
        Map<Long, Long> devuelto = dao.devueltoPorProducto(c, ventaId);
        List<Devolvible> lista = new ArrayList<>();
        for (Vendido vd : dao.vendido(c, ventaId)) {
            lista.add(new Devolvible(vd.productoId(), vd.codigo(), vd.nombre(), vd.tipo(), vd.unidad(), vd.cantidad(),
                    devuelto.getOrDefault(vd.productoId(), 0L), vd.importe()));
        }
        return lista;
    }
}
