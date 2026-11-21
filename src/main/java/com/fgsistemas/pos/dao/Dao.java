package com.fgsistemas.pos.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

/**
 * Utilidades comunes de los DAOs. Los DAOs reciben la {@link Connection} desde el servicio,
 * que es quien decide la transacción. SQL siempre con PreparedStatement.
 */
final class Dao {

    private Dao() {
    }

    static long ultimoId(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static void setLongONulo(PreparedStatement ps, int indice, Long valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.INTEGER);
        } else {
            ps.setLong(indice, valor);
        }
    }

    static String vacioANulo(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
