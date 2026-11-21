package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class BitacoraDao {

    public void registrar(Connection c, Long usuarioId, String accion, String detalle) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bitacora (usuario_id, accion, detalle, fecha) VALUES (?, ?, ?, ?)")) {
            Dao.setLongONulo(ps, 1, usuarioId);
            ps.setString(2, accion);
            ps.setString(3, detalle);
            ps.setString(4, Fechas.ahoraTexto());
            ps.executeUpdate();
        }
    }
}
