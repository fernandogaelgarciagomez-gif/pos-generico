package com.fgsistemas.pos.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Acceso a la BD SQLite. Cada operación abre su propia conexión (SQLite local, es barato)
 * y la cierra con try-with-resources.
 */
public final class Database {

    private static volatile String url;
    private static volatile Path archivo;

    private Database() {
    }

    /** Crea el archivo (si no existe) y aplica las migraciones pendientes. */
    public static synchronized void inicializar(Path archivoBD) throws SQLException, IOException {
        Path absoluto = archivoBD.toAbsolutePath();
        Files.createDirectories(absoluto.getParent());
        archivo = absoluto;
        url = "jdbc:sqlite:" + absoluto;
        try (Connection c = conectar()) {
            int actual = Migraciones.versionActualSiExiste(c);
            if (actual > 0 && actual < Migraciones.versionMasReciente()) {
                respaldarAntesDeActualizar(absoluto, actual);
            }
            Migraciones.ejecutar(c);
        }
    }

    /**
     * Antes de cambiar el esquema de una BD con datos (versión nueva del sistema), se copia el archivo tal cual
     * a {@code respaldos/pos_AAAA-MM-DD_HHmm_antes-de-actualizar-vN.db}. Si la actualización falla, ahí están los
     * datos intactos (se restauran desde Configuración → Respaldos).
     */
    private static void respaldarAntesDeActualizar(Path bd, int versionActual) throws IOException {
        Path carpeta = bd.resolveSibling("respaldos");
        Files.createDirectories(carpeta);
        String sello = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
                .format(com.fgsistemas.pos.util.Fechas.ahora());
        Path destino = carpeta.resolve("pos_" + sello + "_antes-de-actualizar-v" + versionActual + ".db");
        Files.copy(bd, destino, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public static Path archivo() {
        return archivo;
    }

    /**
     * Nueva conexión con llaves foráneas activas.
     * DECISION: se usa el journal por defecto (DELETE) y no WAL, para que respaldar/restaurar
     * sea copiar un solo archivo sin -wal/-shm.
     */
    public static Connection conectar() throws SQLException {
        if (url == null) {
            throw new IllegalStateException("La base de datos no ha sido inicializada");
        }
        Connection c = DriverManager.getConnection(url);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException e) {
            c.close();
            throw e;
        }
        return c;
    }

    /** Trabajo que se ejecuta dentro de una transacción. */
    @FunctionalInterface
    public interface Trabajo<T> {
        T ejecutar(Connection c) throws SQLException;
    }

    /**
     * Ejecuta {@code trabajo} en una sola transacción: commit si todo sale bien,
     * rollback ante cualquier excepción (que se vuelve a lanzar).
     */
    public static <T> T enTransaccion(Trabajo<T> trabajo) throws SQLException {
        try (Connection c = conectar()) {
            c.setAutoCommit(false);
            try {
                T resultado = trabajo.ejecutar(c);
                c.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }
}
