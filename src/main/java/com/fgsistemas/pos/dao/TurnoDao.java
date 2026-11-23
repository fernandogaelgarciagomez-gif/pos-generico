package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.model.Turno;
import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class TurnoDao {

    private static final String SELECT =
            "SELECT t.id, t.usuario_id, t.abierto_en, t.fondo_inicial, t.cerrado_en, t.estado, "
                    + "u.nombre AS usuario_nombre FROM turnos t JOIN usuarios u ON u.id = t.usuario_id ";

    /** El turno abierto (una sola caja → a lo más uno), con el nombre de quien lo abrió. */
    public Optional<Turno> abierto(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE t.estado = 'ABIERTO' ORDER BY t.id LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
        }
    }

    public Optional<Turno> obtener(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE t.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    /** Marca el turno como CERRADO. @return true si estaba abierto. */
    public boolean cerrar(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE turnos SET estado = 'CERRADO', cerrado_en = ? WHERE id = ? AND estado = 'ABIERTO'")) {
            ps.setString(1, Fechas.ahoraTexto());
            ps.setLong(2, id);
            return ps.executeUpdate() == 1;
        }
    }

    private static Turno mapear(ResultSet rs) throws SQLException {
        Turno t = new Turno();
        t.setId(rs.getLong("id"));
        t.setUsuarioId(rs.getLong("usuario_id"));
        t.setAbiertoEn(Fechas.deTexto(rs.getString("abierto_en")));
        t.setFondoInicial(rs.getLong("fondo_inicial"));
        t.setCerradoEn(Fechas.deTexto(rs.getString("cerrado_en")));
        t.setEstado(Turno.Estado.valueOf(rs.getString("estado")));
        t.setUsuarioNombre(rs.getString("usuario_nombre"));
        return t;
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

    /** Fondo inicial del último turno abierto (para avisar si el fondo nuevo es muy distinto). */
    public Optional<Long> ultimoFondo(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT fondo_inicial FROM turnos ORDER BY id DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty();
        }
    }

    /** Fecha más reciente registrada en ventas o turnos (para detectar el reloj de la PC atrasado). */
    public Optional<java.time.LocalDateTime> ultimaFechaRegistrada(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT MAX(f) FROM (SELECT MAX(fecha) AS f FROM ventas "
                + "UNION ALL SELECT MAX(abierto_en) FROM turnos UNION ALL SELECT MAX(cerrado_en) FROM turnos)");
             ResultSet rs = ps.executeQuery()) {
            String f = rs.next() ? rs.getString(1) : null;
            return f == null ? Optional.empty() : Optional.of(com.fgsistemas.pos.util.Fechas.deTexto(f));
        }
    }
}
