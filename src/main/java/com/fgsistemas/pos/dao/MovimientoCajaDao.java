package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Entradas y retiros de efectivo que no son ventas. */
public class MovimientoCajaDao {

    public enum Tipo { ENTRADA, RETIRO }

    public record Movimiento(LocalDateTime fecha, Tipo tipo, long monto, String concepto, String usuarioNombre) {
    }

    public long registrar(Connection c, long turnoId, long usuarioId, Tipo tipo, long monto, String concepto)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO movimientos_caja (turno_id, usuario_id, tipo, monto, concepto, fecha) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, turnoId);
            ps.setLong(2, usuarioId);
            ps.setString(3, tipo.name());
            ps.setLong(4, monto);
            ps.setString(5, concepto);
            ps.setString(6, Fechas.ahoraTexto());
            ps.executeUpdate();
        }
        return Dao.ultimoId(c);
    }

    public List<Movimiento> delTurno(Connection c, long turnoId) throws SQLException {
        List<Movimiento> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT m.fecha, m.tipo, m.monto, m.concepto, u.nombre FROM movimientos_caja m "
                        + "JOIN usuarios u ON u.id = m.usuario_id WHERE m.turno_id = ? ORDER BY m.fecha, m.id")) {
            ps.setLong(1, turnoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Movimiento(Fechas.deTexto(rs.getString(1)), Tipo.valueOf(rs.getString(2)),
                            rs.getLong(3), rs.getString(4), rs.getString(5)));
                }
            }
        }
        return lista;
    }
}
