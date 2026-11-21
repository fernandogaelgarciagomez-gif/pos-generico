package com.fgsistemas.pos.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
}
