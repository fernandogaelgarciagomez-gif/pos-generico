package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.model.MovimientoInventario;
import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MovimientoInventarioDao {

    public enum Tipo { VENTA, CANCELACION, ENTRADA, AJUSTE, DEVOLUCION }

    public void registrar(Connection c, long productoId, Tipo tipo, long cantidad, long stockResultante,
                          Long referenciaId, long usuarioId, String motivo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO movimientos_inventario (producto_id, tipo, cantidad, stock_resultante, referencia_id, "
                        + "usuario_id, motivo, fecha) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, productoId);
            ps.setString(2, tipo.name());
            ps.setLong(3, cantidad);
            ps.setLong(4, stockResultante);
            Dao.setLongONulo(ps, 5, referenciaId);
            ps.setLong(6, usuarioId);
            ps.setString(7, motivo);
            ps.setString(8, Fechas.ahoraTexto());
            ps.executeUpdate();
        }
    }

    /** Kardex del producto, del más reciente al más antiguo. */
    public List<MovimientoInventario> listarPorProducto(Connection c, long productoId) throws SQLException {
        List<MovimientoInventario> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT m.fecha, m.tipo, m.cantidad, m.stock_resultante, m.referencia_id, u.nombre, m.motivo "
                        + "FROM movimientos_inventario m JOIN usuarios u ON u.id = m.usuario_id "
                        + "WHERE m.producto_id = ? ORDER BY m.id DESC")) {
            ps.setLong(1, productoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long ref = rs.getLong(5);
                    Long referencia = rs.wasNull() ? null : ref;
                    lista.add(new MovimientoInventario(Fechas.deTexto(rs.getString(1)), rs.getString(2),
                            rs.getLong(3), rs.getLong(4), referencia, rs.getString(6), rs.getString(7)));
                }
            }
        }
        return lista;
    }
}
