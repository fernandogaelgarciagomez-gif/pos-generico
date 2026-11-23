package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Devoluciones parciales de ventas (tablas {@code devoluciones} y {@code devolucion_detalle}, migración V2). */
public class DevolucionDao {

    /** Lo vendido de un producto en una venta (sumando sus renglones). */
    public record Vendido(long productoId, String codigo, String nombre, TipoVenta tipo, String unidad,
                          long cantidad, long importe, long costoUnitario) {
    }

    /** Encabezado de la venta que se quiere devolver. */
    public record VentaBase(long id, long folio, String estado, long subtotal, long total, boolean tieneFiado) {
    }

    public Optional<VentaBase> venta(Connection c, long ventaId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT v.id, v.folio, v.estado, v.subtotal, v.total, "
                + "EXISTS (SELECT 1 FROM venta_pagos p WHERE p.venta_id = v.id AND p.metodo = 'FIADO') "
                + "FROM ventas v WHERE v.id = ?")) {
            ps.setLong(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new VentaBase(rs.getLong(1), rs.getLong(2), rs.getString(3),
                        rs.getLong(4), rs.getLong(5), rs.getInt(6) == 1)) : Optional.empty();
            }
        }
    }

    public Optional<Long> idPorFolio(Connection c, long folio) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM ventas WHERE folio = ?")) {
            ps.setLong(1, folio);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty();
            }
        }
    }

    public List<Vendido> vendido(Connection c, long ventaId) throws SQLException {
        List<Vendido> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT d.producto_id, p.codigo, p.nombre, p.tipo_venta, p.unidad, "
                + "SUM(d.cantidad), SUM(d.importe), MAX(d.costo_unitario) FROM venta_detalle d "
                + "JOIN productos p ON p.id = d.producto_id WHERE d.venta_id = ? "
                + "GROUP BY d.producto_id ORDER BY MIN(d.id)")) {
            ps.setLong(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Vendido(rs.getLong(1), rs.getString(2), rs.getString(3),
                            TipoVenta.valueOf(rs.getString(4)), rs.getString(5), rs.getLong(6), rs.getLong(7),
                            rs.getLong(8)));
                }
            }
        }
        return lista;
    }

    /** Cantidad ya devuelta de cada producto de la venta. */
    public Map<Long, Long> devueltoPorProducto(Connection c, long ventaId) throws SQLException {
        Map<Long, Long> m = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT dd.producto_id, SUM(dd.cantidad) FROM devolucion_detalle dd "
                + "JOIN devoluciones d ON d.id = dd.devolucion_id WHERE d.venta_id = ? GROUP BY dd.producto_id")) {
            ps.setLong(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    m.put(rs.getLong(1), rs.getLong(2));
                }
            }
        }
        return m;
    }

    public long totalDevuelto(Connection c, long ventaId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(total), 0) FROM devoluciones WHERE venta_id = ?")) {
            ps.setLong(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public long insertar(Connection c, long ventaId, long turnoId, long usuarioId, Long autorizadaPor, String motivo,
                         long total) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO devoluciones (venta_id, turno_id, usuario_id, "
                + "autorizada_por, motivo, total, fecha) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, ventaId);
            ps.setLong(2, turnoId);
            ps.setLong(3, usuarioId);
            Dao.setLongONulo(ps, 4, autorizadaPor);
            ps.setString(5, motivo);
            ps.setLong(6, total);
            ps.setString(7, Fechas.ahoraTexto());
            ps.executeUpdate();
        }
        return Dao.ultimoId(c);
    }

    public void insertarDetalle(Connection c, long devolucionId, long productoId, long cantidad, long importe,
                                long costoUnitario) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO devolucion_detalle (devolucion_id, producto_id, "
                + "cantidad, importe, costo_unitario) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, devolucionId);
            ps.setLong(2, productoId);
            ps.setLong(3, cantidad);
            ps.setLong(4, importe);
            ps.setLong(5, costoUnitario);
            ps.executeUpdate();
        }
    }
}
