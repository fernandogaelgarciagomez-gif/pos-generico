package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Entradas y retiros de efectivo que no son ventas. */
public class MovimientoCajaDao {

    public enum Tipo { ENTRADA, RETIRO }

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
}
