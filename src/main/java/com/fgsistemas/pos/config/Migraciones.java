package com.fgsistemas.pos.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Migraciones versionadas. Cada script de {@link #SCRIPTS} es una versión (índice + 1).
 * Para cambiar el esquema: agregar un script nuevo al final, nunca editar uno ya publicado.
 */
public final class Migraciones {

    static final String[] SCRIPTS = {
            "/db/V1__esquema_inicial.sql",
            "/db/V2__devoluciones.sql",
    };

    private Migraciones() {
    }

    public static int versionMasReciente() {
        return SCRIPTS.length;
    }

    static void ejecutar(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
        }
        int actual = versionActual(c);
        for (int i = actual; i < SCRIPTS.length; i++) {
            int version = i + 1;
            List<String> sentencias = dividir(leer(SCRIPTS[i]));
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                for (String sql : sentencias) {
                    st.execute(sql);
                }
                st.execute("DELETE FROM schema_version");
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO schema_version (version) VALUES (?)")) {
                    ps.setInt(1, version);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw new SQLException("Error al aplicar la migración V" + version + ": " + e.getMessage(), e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Versión de la BD, o 0 si es nueva (aún no tiene la tabla schema_version). */
    static int versionActualSiExiste(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'schema_version'");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return 0;
            }
        }
        return versionActual(c);
    }

    public static int versionActual(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String leer(String recurso) throws SQLException {
        try (InputStream in = Migraciones.class.getResourceAsStream(recurso)) {
            if (in == null) {
                throw new SQLException("No se encontró el script de migración " + recurso);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("No se pudo leer " + recurso, e);
        }
    }

    /** Divide un script en sentencias por ';', ignorando comentarios "--" y respetando cadenas '...'. */
    static List<String> dividir(String script) {
        List<String> sentencias = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean enCadena = false;
        for (int i = 0; i < script.length(); i++) {
            char ch = script.charAt(i);
            if (enCadena) {
                actual.append(ch);
                if (ch == '\'') {
                    enCadena = false; // '' (comilla escapada) cierra y vuelve a abrir
                }
                continue;
            }
            if (ch == '\'') {
                enCadena = true;
                actual.append(ch);
            } else if (ch == '-' && i + 1 < script.length() && script.charAt(i + 1) == '-') {
                while (i < script.length() && script.charAt(i) != '\n') {
                    i++;
                }
                actual.append('\n');
            } else if (ch == ';') {
                agregar(sentencias, actual);
            } else {
                actual.append(ch);
            }
        }
        agregar(sentencias, actual);
        return sentencias;
    }

    private static void agregar(List<String> sentencias, StringBuilder sb) {
        String s = sb.toString().trim();
        if (!s.isEmpty()) {
            sentencias.add(s);
        }
        sb.setLength(0);
    }
}
