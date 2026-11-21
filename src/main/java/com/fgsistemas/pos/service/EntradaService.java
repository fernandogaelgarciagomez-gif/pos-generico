package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.EntradaDao;
import com.fgsistemas.pos.dao.MovimientoCajaDao;
import com.fgsistemas.pos.dao.MovimientoInventarioDao;
import com.fgsistemas.pos.dao.MovimientoInventarioDao.Tipo;
import com.fgsistemas.pos.dao.ProductoDao;
import com.fgsistemas.pos.model.EntradaDetalle;
import com.fgsistemas.pos.model.EntradaMercancia;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Sesion;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Entrada de mercancía (docs/modulos/16-entrada-mercancia.md). Solo admin. */
public class EntradaService {

    /** Renglón a guardar: producto, cantidad (milésimas) y costo unitario (centavos). */
    public record Linea(long productoId, long cantidad, long costoUnitario) {
    }

    /** Producto cuyo costo cambió con la entrada (para ofrecer actualizar el precio). */
    public record CambioCosto(Producto producto, long costoAnterior, long costoNuevo) {
    }

    public record Resultado(long entradaId, long totalCosto, List<CambioCosto> cambiosDeCosto) {
    }

    private final EntradaDao entradaDao = new EntradaDao();
    private final ProductoDao productoDao = new ProductoDao();
    private final MovimientoInventarioDao movimientoDao = new MovimientoInventarioDao();
    private final MovimientoCajaDao cajaDao = new MovimientoCajaDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();

    public static long totalCosto(List<Linea> lineas) {
        return lineas.stream().mapToLong(l -> Cantidad.importe(l.costoUnitario(), l.cantidad())).sum();
    }

    /**
     * Guarda la entrada en una sola transacción: encabezado + detalle, suma stock con kardex ENTRADA,
     * actualiza costos y, si se pagó de la caja, registra un RETIRO en el turno actual.
     */
    public Resultado guardar(String proveedor, String factura, String notas, List<Linea> lineas,
                             boolean pagadoDeCaja) throws SQLException {
        Sesion.requiere(Permiso.ENTRADA_MERCANCIA);
        if (lineas == null || lineas.isEmpty()) {
            throw new IllegalArgumentException("Agrega al menos un producto.");
        }
        if (pagadoDeCaja && Sesion.turno() == null) {
            throw new IllegalStateException("No hay turno abierto para registrar el pago de la caja.");
        }
        // Un renglón por producto (si se repitió, se suman cantidades y se usa el último costo).
        Map<Long, Linea> porProducto = new LinkedHashMap<>();
        for (Linea l : lineas) {
            if (l.cantidad() <= 0) {
                throw new IllegalArgumentException("Las cantidades deben ser mayores a cero.");
            }
            if (l.costoUnitario() < 0) {
                throw new IllegalArgumentException("El costo no puede ser negativo.");
            }
            porProducto.merge(l.productoId(), l,
                    (a, b) -> new Linea(a.productoId(), a.cantidad() + b.cantidad(), b.costoUnitario()));
        }
        List<Linea> finales = new ArrayList<>(porProducto.values());
        long total = totalCosto(finales);
        long usuarioId = Sesion.usuario().getId();
        Long turnoId = Sesion.turno() == null ? null : Sesion.turno().getId();

        return Database.enTransaccion(c -> {
            long entradaId = entradaDao.insertar(c, usuarioId, proveedor, factura, notas, total);
            List<CambioCosto> cambios = new ArrayList<>();
            for (Linea l : finales) {
                Producto p = productoDao.buscarPorId(c, l.productoId())
                        .orElseThrow(() -> new IllegalArgumentException("Un producto de la lista ya no existe."));
                if (!p.getTipoVenta().aceptaDecimales() && l.cantidad() % Cantidad.UNO != 0) {
                    throw new IllegalArgumentException(p.getNombre() + " se vende por pieza: la cantidad debe ser entera.");
                }
                entradaDao.insertarDetalle(c, entradaId, p.getId(), l.cantidad(), l.costoUnitario());
                long nuevoStock = p.getStock() + l.cantidad();
                productoDao.actualizarStock(c, p.getId(), nuevoStock);
                movimientoDao.registrar(c, p.getId(), Tipo.ENTRADA, l.cantidad(), nuevoStock, entradaId, usuarioId,
                        proveedor == null || proveedor.isBlank() ? null : "Proveedor: " + proveedor.trim());
                if (l.costoUnitario() != p.getCosto()) {
                    productoDao.actualizarCosto(c, p.getId(), l.costoUnitario());
                    cambios.add(new CambioCosto(p, p.getCosto(), l.costoUnitario()));
                }
            }
            if (pagadoDeCaja) {
                String concepto = "Pago de mercancía (entrada #" + entradaId + ")"
                        + (proveedor == null || proveedor.isBlank() ? "" : " · " + proveedor.trim());
                cajaDao.registrar(c, turnoId, usuarioId, MovimientoCajaDao.Tipo.RETIRO, total, concepto);
            }
            bitacoraDao.registrar(c, usuarioId, "ENTRADA_MERCANCIA", "Entrada #" + entradaId + " · "
                    + finales.size() + " productos · " + Dinero.formatear(total)
                    + (pagadoDeCaja ? " · pagada de la caja" : ""));
            return new Resultado(entradaId, total, cambios);
        });
    }

    public List<String> proveedores() throws SQLException {
        Sesion.requiere(Permiso.ENTRADA_MERCANCIA);
        try (Connection c = Database.conectar()) {
            return entradaDao.proveedores(c);
        }
    }

    public List<EntradaMercancia> historial(LocalDate desde, LocalDate hasta, String texto) throws SQLException {
        Sesion.requiere(Permiso.ENTRADA_MERCANCIA);
        try (Connection c = Database.conectar()) {
            return entradaDao.listar(c, desde, hasta, texto);
        }
    }

    public List<EntradaDetalle> detalle(long entradaId) throws SQLException {
        Sesion.requiere(Permiso.ENTRADA_MERCANCIA);
        try (Connection c = Database.conectar()) {
            return entradaDao.detalle(c, entradaId);
        }
    }
}
