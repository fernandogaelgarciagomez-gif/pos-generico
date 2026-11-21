package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.model.Rol;
import com.fgsistemas.pos.model.Usuario;
import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UsuarioDao {

    private static final String COLUMNAS =
            "id, nombre, usuario, password_hash, rol, activo, creado_en";

    /** ¿Existe algún usuario con rol ADMIN? Si no, la app muestra la bienvenida. */
    public boolean existeAdmin(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM usuarios WHERE rol = 'ADMIN' LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    public Optional<Usuario> buscarPorUsuario(Connection c, String usuario) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNAS + " FROM usuarios WHERE usuario = ?")) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Usuario> buscarPorId(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNAS + " FROM usuarios WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    /** Inserta y regresa el id. {@code passwordHash} ya debe venir hasheado con BCrypt. */
    public long insertar(Connection c, Usuario u) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO usuarios (nombre, usuario, password_hash, rol, activo, creado_en) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, u.getNombre());
            ps.setString(2, u.getUsuario());
            ps.setString(3, u.getPasswordHash());
            ps.setString(4, u.getRol().name());
            ps.setInt(5, u.isActivo() ? 1 : 0);
            ps.setString(6, Fechas.aTexto(u.getCreadoEn() != null ? u.getCreadoEn() : Fechas.ahora()));
            ps.executeUpdate();
        }
        long id = Dao.ultimoId(c);
        u.setId(id);
        return id;
    }

    public List<Usuario> listar(Connection c) throws SQLException {
        List<Usuario> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNAS + " FROM usuarios ORDER BY activo DESC, nombre COLLATE NOCASE");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    public void actualizar(Connection c, long id, String nombre, Rol rol, boolean activo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE usuarios SET nombre = ?, rol = ?, activo = ? WHERE id = ?")) {
            ps.setString(1, nombre);
            ps.setString(2, rol.name());
            ps.setInt(3, activo ? 1 : 0);
            ps.setLong(4, id);
            ps.executeUpdate();
        }
    }

    public void actualizarPassword(Connection c, long id, String passwordHash) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE usuarios SET password_hash = ? WHERE id = ?")) {
            ps.setString(1, passwordHash);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public int contarAdminsActivos(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM usuarios WHERE rol = 'ADMIN' AND activo = 1");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private Usuario mapear(ResultSet rs) throws SQLException {
        Usuario u = new Usuario();
        u.setId(rs.getLong("id"));
        u.setNombre(rs.getString("nombre"));
        u.setUsuario(rs.getString("usuario"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRol(Rol.valueOf(rs.getString("rol")));
        u.setActivo(rs.getInt("activo") == 1);
        u.setCreadoEn(Fechas.deTexto(rs.getString("creado_en")));
        return u;
    }
}
