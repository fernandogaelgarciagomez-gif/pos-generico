package com.fgsistemas.pos.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {

    private static final List<String> TABLAS = List.of(
            "schema_version", "negocio", "usuarios", "categorias", "productos", "clientes",
            "turnos", "ventas", "venta_detalle", "venta_pagos", "abonos", "entradas_mercancia",
            "entrada_detalle", "movimientos_inventario", "movimientos_caja", "cortes", "bitacora");

    @TempDir
    Path tmp;

    @Test
    void creaBdConTodasLasTablas() throws Exception {
        Path archivo = tmp.resolve("sub").resolve("pos.db");
        Database.inicializar(archivo);
        assertTrue(Files.exists(archivo));

        Set<String> existentes = new HashSet<>();
        try (Connection c = Database.conectar();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
            while (rs.next()) {
                existentes.add(rs.getString(1));
            }
            assertEquals(Migraciones.versionMasReciente(), Migraciones.versionActual(c));
        }
        for (String t : TABLAS) {
            assertTrue(existentes.contains(t), "Falta la tabla " + t);
        }
    }

    @Test
    void migrarDosVecesNoFalla() throws Exception {
        Path archivo = tmp.resolve("pos.db");
        Database.inicializar(archivo);
        Database.inicializar(archivo);
        try (Connection c = Database.conectar()) {
            assertEquals(Migraciones.versionMasReciente(), Migraciones.versionActual(c));
        }
    }

    @Test
    void llavesForaneasActivas() throws Exception {
        Database.inicializar(tmp.resolve("pos.db"));
        try (Connection c = Database.conectar(); Statement st = c.createStatement()) {
            assertThrows(SQLException.class, () -> st.executeUpdate(
                    "INSERT INTO turnos (usuario_id, abierto_en, fondo_inicial, estado) "
                            + "VALUES (999, '2026-11-21T08:00:00', 0, 'ABIERTO')"));
        }
    }

    @Test
    void transaccionHaceRollback() throws Exception {
        Database.inicializar(tmp.resolve("pos.db"));
        assertThrows(SQLException.class, () -> Database.enTransaccion(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("INSERT INTO categorias (nombre) VALUES ('Bebidas')");
                st.executeUpdate("INSERT INTO categorias (nombre) VALUES ('Bebidas')"); // UNIQUE
            }
            return null;
        }));
        try (Connection c = Database.conectar(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM categorias")) {
            rs.next();
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void negocioTieneDefaults() throws Exception {
        Database.inicializar(tmp.resolve("pos.db"));
        try (Connection c = Database.conectar(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO negocio (id, nombre) VALUES (1, 'Mi Tienda')");
            try (ResultSet rs = st.executeQuery(
                    "SELECT mensaje_ticket, stock_minimo_default, permitir_stock_negativo FROM negocio")) {
                rs.next();
                assertEquals("¡Gracias por su compra!", rs.getString(1));
                assertEquals(5000, rs.getLong(2));
                assertEquals(0, rs.getInt(3));
            }
        }
    }

    @Test
    void divideScriptRespetandoCadenasYComentarios() {
        List<String> s = Migraciones.dividir(
                "CREATE TABLE a (x TEXT DEFAULT 'a;b'); -- comentario; con punto y coma\n"
                        + "INSERT INTO a VALUES ('it''s');");
        assertEquals(2, s.size());
        assertTrue(s.get(0).contains("'a;b'"));
        assertTrue(s.get(1).contains("'it''s'"));
    }
}
