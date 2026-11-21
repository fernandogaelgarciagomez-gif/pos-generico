package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.model.Turno;
import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class TurnoDao {

    /** El turno abierto (una sola caja → a lo más uno), con el nombre de quien lo abrió. */
    public Optional<Turno> abierto(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT t.id, t.usuario_id, t.abierto_en, t.fondo_inicial, t.cerrado_en, t.estado, "
                        + "u.nombre AS usuario_nombre "
                        + "FROM turnos t JOIN usuarios u ON u.id = t.usuario_id "
                        + "WHERE t.estado = 'ABIERTO' ORDER BY t.id LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            Turno t = new Turno();
            t.setId(rs.getLong("id"));
            t.setUsuarioId(rs.getLong("usuario_id"));
            t.setAbiertoEn(Fechas.deTexto(rs.getString("abierto_en")));
            t.setFondoInicial(rs.getLong("fondo_inicial"));
            t.setCerradoEn(Fechas.deTexto(rs.getString("cerrado_en")));
            t.setEstado(Turno.Estado.valueOf(rs.getString("estado")));
            t.setUsuarioNombre(rs.getString("usuario_nombre"));
            return Optional.of(t);
        }
    }

    public long insertar(Connection c, Turno t) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO turnos (usuario_id, abierto_en, fondo_inicial, estado) VALUES (?, ?, ?, 'ABIERTO')")) {
            ps.setLong(1, t.getUsuarioId());
            ps.setString(2, Fechas.aTexto(t.getAbiertoEn()));
            ps.setLong(3, t.getFondoInicial());
            ps.executeUpdate();
        }
        long id = Dao.ultimoId(c);
        t.setId(id);
        return id;
    }
}
