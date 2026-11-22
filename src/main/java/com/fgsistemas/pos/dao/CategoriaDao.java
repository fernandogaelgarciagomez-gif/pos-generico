package com.fgsistemas.pos.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CategoriaDao {

    public List<String> listarNombres(Connection c) throws SQLException {
        List<String> nombres = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT nombre FROM categorias ORDER BY nombre COLLATE NOCASE");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                nombres.add(rs.getString(1));
            }
        }
        return nombres;
    }

    /** Id de la categoría con ese nombre (sin distinguir mayúsculas); la crea si no existe. */
    public long buscarOCrear(Connection c, String nombre) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM categorias WHERE nombre = ? COLLATE NOCASE")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO categorias (nombre) VALUES (?)")) {
            ps.setString(1, nombre);
            ps.executeUpdate();
        }
        return Dao.ultimoId(c);
    }

    /** Categoría con cuántos productos (activos o no) la usan. */
    public record ConConteo(long id, String nombre, int productos) {
    }

    public List<ConConteo> listarConConteo(Connection c) throws SQLException {
        List<ConConteo> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT c.id, c.nombre, "
                + "(SELECT COUNT(*) FROM productos p WHERE p.categoria_id = c.id) "
                + "FROM categorias c ORDER BY c.nombre COLLATE NOCASE");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new ConConteo(rs.getLong(1), rs.getString(2), rs.getInt(3)));
            }
        }
        return lista;
    }

    public Optional<ConConteo> buscar(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT c.id, c.nombre, "
                + "(SELECT COUNT(*) FROM productos p WHERE p.categoria_id = c.id) FROM categorias c WHERE c.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new ConConteo(rs.getLong(1), rs.getString(2), rs.getInt(3)))
                        : Optional.empty();
            }
        }
    }

    /** ¿Otra categoría (distinta de {@code excluirId}) ya tiene ese nombre, sin distinguir mayúsculas? */
    public boolean existeNombre(Connection c, String nombre, long excluirId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM categorias WHERE nombre = ? COLLATE NOCASE AND id <> ?")) {
            ps.setString(1, nombre);
            ps.setLong(2, excluirId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void renombrar(Connection c, long id, String nombre) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE categorias SET nombre = ? WHERE id = ?")) {
            ps.setString(1, nombre);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void eliminar(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM categorias WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }
}
