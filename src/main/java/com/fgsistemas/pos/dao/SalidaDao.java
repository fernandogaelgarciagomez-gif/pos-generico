package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Salidas sin cobro (tablas {@code salidas} y {@code salida_detalle}, migración V3). */
public class SalidaDao {

    /** Un renglón del reporte de salidas: un producto de una salida. */
    public record Renglon(long salidaId, LocalDateTime fecha, String usuario, String motivo, String codigo,
                          String producto, TipoVenta tipo, String unidad, long cantidad, long costoUnitario,
                          long precioUnitario) {
    }

    public long insertar(Connection c, Long turnoId, long usuarioId, String motivo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO salidas (turno_id, usuario_id, motivo, fecha) VALUES (?, ?, ?, ?)")) {
            Dao.setLongONulo(ps, 1, turnoId);
            ps.setLong(2, usuarioId);
            ps.setString(3, motivo);
            ps.setString(4, Fechas.ahoraTexto());
            ps.executeUpdate();
        }
        return Dao.ultimoId(c);
    }

    public void insertarDetalle(Connection c, long salidaId, long productoId, long cantidad, long costoUnitario,
                                long precioUnitario) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO salida_detalle (salida_id, producto_id, cantidad, "
                + "costo_unitario, precio_unitario) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, salidaId);
            ps.setLong(2, productoId);
            ps.setLong(3, cantidad);
            ps.setLong(4, costoUnitario);
            ps.setLong(5, precioUnitario);
            ps.executeUpdate();
        }
    }

    /** Renglones de salidas con fecha en [desde, hasta), del más reciente al más antiguo. */
    public List<Renglon> listar(Connection c, LocalDateTime desde, LocalDateTime hasta) throws SQLException {
        List<Renglon> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT s.id, s.fecha, u.nombre, s.motivo, p.codigo, p.nombre, "
                + "p.tipo_venta, p.unidad, d.cantidad, d.costo_unitario, d.precio_unitario "
                + "FROM salida_detalle d JOIN salidas s ON s.id = d.salida_id "
                + "JOIN usuarios u ON u.id = s.usuario_id JOIN productos p ON p.id = d.producto_id "
                + "WHERE s.fecha >= ? AND s.fecha < ? ORDER BY s.id DESC, d.id")) {
            ps.setString(1, Fechas.aTexto(desde));
            ps.setString(2, Fechas.aTexto(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Renglon(rs.getLong(1), Fechas.deTexto(rs.getString(2)), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), TipoVenta.valueOf(rs.getString(7)),
                            rs.getString(8), rs.getLong(9), rs.getLong(10), rs.getLong(11)));
                }
            }
        }
        return lista;
    }
}
