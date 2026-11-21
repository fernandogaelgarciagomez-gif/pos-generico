package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.model.MetodoPago;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.model.Venta;
import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VentaDao {

    /** Total vendido y número de ventas (solo COMPLETADAS; las canceladas no cuentan). */
    public record Totales(long total, int ventas) {
    }

    /** Datos mínimos para validar una cancelación. */
    public record Estado(long id, long folio, String estado, long turnoId, String turnoEstado, Long clienteId) {
    }

    // ---------------------------------------------------------------- totales

    public Totales delTurno(Connection c, long turnoId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(total), 0), COUNT(*) FROM ventas WHERE turno_id = ? AND estado = 'COMPLETADA'")) {
            ps.setLong(1, turnoId);
            return leer(ps);
        }
    }

    public Totales delDia(Connection c, LocalDate dia) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(total), 0), COUNT(*) FROM ventas "
                        + "WHERE fecha >= ? AND fecha < ? AND estado = 'COMPLETADA'")) {
            ps.setString(1, Fechas.aTexto(dia.atStartOfDay()));
            ps.setString(2, Fechas.aTexto(dia.plusDays(1).atStartOfDay()));
            return leer(ps);
        }
    }

    private static Totales leer(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new Totales(rs.getLong(1), rs.getInt(2));
        }
    }

    // ---------------------------------------------------------------- alta

    public long siguienteFolio(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(folio), 0) + 1 FROM ventas");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public long insertar(Connection c, long folio, long turnoId, long usuarioId, Long clienteId, long subtotal,
                         long descuento, long total, Long autorizadaPor) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ventas (folio, turno_id, usuario_id, cliente_id, subtotal, descuento, total, estado, "
                        + "fecha, autorizada_por) VALUES (?, ?, ?, ?, ?, ?, ?, 'COMPLETADA', ?, ?)")) {
            ps.setLong(1, folio);
            ps.setLong(2, turnoId);
            ps.setLong(3, usuarioId);
            Dao.setLongONulo(ps, 4, clienteId);
            ps.setLong(5, subtotal);
            ps.setLong(6, descuento);
            ps.setLong(7, total);
            ps.setString(8, Fechas.ahoraTexto());
            Dao.setLongONulo(ps, 9, autorizadaPor);
            ps.executeUpdate();
        }
        return Dao.ultimoId(c);
    }

    public void insertarDetalle(Connection c, long ventaId, long productoId, long cantidad, long precioUnitario,
                                long costoUnitario, long importe) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO venta_detalle (venta_id, producto_id, cantidad, precio_unitario, costo_unitario, importe) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, ventaId);
            ps.setLong(2, productoId);
            ps.setLong(3, cantidad);
            ps.setLong(4, precioUnitario);
            ps.setLong(5, costoUnitario);
            ps.setLong(6, importe);
            ps.executeUpdate();
        }
    }

    public void insertarPago(Connection c, long ventaId, MetodoPago metodo, long monto, Long recibido, Long cambio,
                             String referencia) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO venta_pagos (venta_id, metodo, monto, recibido, cambio, referencia) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, ventaId);
            ps.setString(2, metodo.name());
            ps.setLong(3, monto);
            Dao.setLongONulo(ps, 4, recibido);
            Dao.setLongONulo(ps, 5, cambio);
            if (referencia == null || referencia.isBlank()) {
                ps.setNull(6, Types.VARCHAR);
            } else {
                ps.setString(6, referencia.trim());
            }
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- consultas

    private static final String SELECT_RESUMEN =
            "SELECT v.id, v.folio, v.fecha, u.nombre, cl.nombre, v.total, v.estado, "
                    + "(SELECT GROUP_CONCAT(p.metodo, ',') FROM venta_pagos p WHERE p.venta_id = v.id), "
                    + "v.motivo_cancelacion "
                    + "FROM ventas v JOIN usuarios u ON u.id = v.usuario_id "
                    + "LEFT JOIN clientes cl ON cl.id = v.cliente_id ";

    public List<Venta.Resumen> listarDelTurno(Connection c, long turnoId) throws SQLException {
        List<Venta.Resumen> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_RESUMEN + "WHERE v.turno_id = ? ORDER BY v.folio DESC")) {
            ps.setLong(1, turnoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(resumen(rs));
                }
            }
        }
        return lista;
    }

    public Optional<Venta.Resumen> resumen(Connection c, long ventaId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT_RESUMEN + "WHERE v.id = ?")) {
            ps.setLong(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(resumen(rs)) : Optional.empty();
            }
        }
    }

    private static Venta.Resumen resumen(ResultSet rs) throws SQLException {
        return new Venta.Resumen(rs.getLong(1), rs.getLong(2), Fechas.deTexto(rs.getString(3)),
                rs.getString(4), rs.getString(5), rs.getLong(6), rs.getString(7),
                metodosTexto(rs.getString(8)), rs.getString(9));
    }

    private static String metodosTexto(String metodos) {
        if (metodos == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String m : metodos.split(",")) {
            String etiqueta = MetodoPago.valueOf(m).etiqueta();
            if (sb.indexOf(etiqueta) < 0) {
                sb.append(sb.length() == 0 ? "" : " + ").append(etiqueta);
            }
        }
        return sb.toString();
    }

    public List<Venta.Renglon> renglones(Connection c, long ventaId) throws SQLException {
        List<Venta.Renglon> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT p.codigo, p.nombre, p.tipo_venta, p.unidad, d.cantidad, d.precio_unitario, d.importe "
                        + "FROM venta_detalle d JOIN productos p ON p.id = d.producto_id "
                        + "WHERE d.venta_id = ? ORDER BY d.id")) {
            ps.setLong(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Venta.Renglon(rs.getString(1), rs.getString(2), TipoVenta.valueOf(rs.getString(3)),
                            rs.getString(4), rs.getLong(5), rs.getLong(6), rs.getLong(7)));
                }
            }
        }
        return lista;
    }

    /** Renglones crudos (producto y cantidad) para regresar stock al cancelar. */
    public record DetalleCrudo(long productoId, long cantidad) {
    }

    public List<DetalleCrudo> detalleCrudo(Connection c, long ventaId) throws SQLException {
        List<DetalleCrudo> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT producto_id, cantidad FROM venta_detalle WHERE venta_id = ? ORDER BY id")) {
            ps.setLong(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new DetalleCrudo(rs.getLong(1), rs.getLong(2)));
                }
            }
        }
        return lista;
    }

    public List<Venta.Pago> pagos(Connection c, long ventaId) throws SQLException {
        List<Venta.Pago> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT metodo, monto, recibido, cambio, referencia FROM venta_pagos WHERE venta_id = ? ORDER BY id")) {
            ps.setLong(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long rec = rs.getLong(3);
                    Long recibido = rs.wasNull() ? null : rec;
                    long cam = rs.getLong(4);
                    Long cambio = rs.wasNull() ? null : cam;
                    lista.add(new Venta.Pago(MetodoPago.valueOf(rs.getString(1)), rs.getLong(2), recibido, cambio,
                            rs.getString(5)));
                }
            }
        }
        return lista;
    }

    public Optional<Estado> estado(Connection c, long ventaId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT v.id, v.folio, v.estado, v.turno_id, t.estado, v.cliente_id "
                        + "FROM ventas v JOIN turnos t ON t.id = v.turno_id WHERE v.id = ?")) {
            ps.setLong(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                long cli = rs.getLong(6);
                Long clienteId = rs.wasNull() ? null : cli;
                return Optional.of(new Estado(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4),
                        rs.getString(5), clienteId));
            }
        }
    }

    public void marcarCancelada(Connection c, long ventaId, long canceladaPor, Long autorizadaPor, String motivo)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE ventas SET estado = 'CANCELADA', cancelada_en = ?, cancelada_por = ?, "
                        + "autorizada_por = COALESCE(?, autorizada_por), motivo_cancelacion = ? WHERE id = ?")) {
            ps.setString(1, Fechas.ahoraTexto());
            ps.setLong(2, canceladaPor);
            Dao.setLongONulo(ps, 3, autorizadaPor);
            ps.setString(4, motivo);
            ps.setLong(5, ventaId);
            ps.executeUpdate();
        }
    }
}
