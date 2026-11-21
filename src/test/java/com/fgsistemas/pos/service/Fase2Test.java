package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.model.Turno;
import com.fgsistemas.pos.model.Usuario;
import com.fgsistemas.pos.service.ConfiguracionInicialService.DatosIniciales;
import com.fgsistemas.pos.util.Sesion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Criterios de la Fase 2: primera vez, login y turno. */
class Fase2Test {

    private static final DatosIniciales DATOS = new DatosIniciales(
            "María López", "maria", "secreta1", "secreta1", "Abarrotes La Esperanza", "5551234567", "");

    @TempDir
    Path tmp;

    private final ConfiguracionInicialService config = new ConfiguracionInicialService();

    @BeforeEach
    void bd() throws Exception {
        Database.inicializar(tmp.resolve("pos.db"));
    }

    @AfterEach
    void limpiar() {
        Sesion.cerrar();
    }

    // ---------- Bienvenida ----------

    @Test
    void bdVaciaRequiereConfiguracionYLuegoNo() throws Exception {
        assertTrue(config.requiereConfiguracion());
        config.configurar(DATOS);
        assertFalse(config.requiereConfiguracion());
    }

    @Test
    void contrasenaQuedaHasheadaYSeCreaNegocioYBitacora() throws Exception {
        config.configurar(DATOS);
        try (Connection c = Database.conectar(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT password_hash, rol FROM usuarios WHERE usuario='maria'")) {
                assertTrue(rs.next());
                String hash = rs.getString(1);
                assertNotEquals("secreta1", hash);
                assertTrue(hash.startsWith("$2a$"));
                assertEquals("ADMIN", rs.getString(2));
            }
            assertEquals("Abarrotes La Esperanza", uno(st, "SELECT nombre FROM negocio WHERE id=1"));
            assertEquals("1", uno(st, "SELECT COUNT(*) FROM bitacora WHERE accion='SISTEMA_CONFIGURADO'"));
        }
    }

    @Test
    void noSeConfiguraConDatosInvalidos() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> config.configurar(new DatosIniciales(
                "María", "maria", "secreta1", "otra", "Tienda", null, null)));
        assertThrows(IllegalArgumentException.class, () -> config.configurar(new DatosIniciales(
                "María", "Maria Lopez", "secreta1", "secreta1", "Tienda", null, null)));
        assertThrows(IllegalArgumentException.class, () -> config.configurar(new DatosIniciales(
                "María", "maria", "123", "123", "Tienda", null, null)));
        assertThrows(IllegalArgumentException.class, () -> config.configurar(new DatosIniciales(
                "María", "maria", "secreta1", "secreta1", "  ", null, null)));
        assertTrue(config.requiereConfiguracion(), "No debe quedar nada guardado");
    }

    @Test
    void noSePuedeConfigurarDosVeces() throws Exception {
        config.configurar(DATOS);
        assertThrows(IllegalStateException.class, () -> config.configurar(DATOS));
    }

    // ---------- Login ----------

    @Test
    void loginCorrectoIniciaSesion() throws Exception {
        config.configurar(DATOS);
        Usuario u = new AuthService(Clock.systemUTC()).login("  MARIA ", "secreta1");
        assertEquals("maria", u.getUsuario());
        assertSame(u, Sesion.usuario());
    }

    @Test
    void loginIncorrectoNoDiceQueFallo() throws Exception {
        config.configurar(DATOS);
        AuthService auth = new AuthService(Clock.systemUTC());
        LoginException malaPw = assertThrows(LoginException.class, () -> auth.login("maria", "mala"));
        LoginException noExiste = assertThrows(LoginException.class, () -> auth.login("nadie", "secreta1"));
        assertEquals(AuthService.MSG_CREDENCIALES, malaPw.getMessage());
        assertEquals(malaPw.getMessage(), noExiste.getMessage());
        assertFalse(Sesion.haySesion());
    }

    @Test
    void usuarioInactivo() throws Exception {
        config.configurar(DATOS);
        try (Connection c = Database.conectar(); Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE usuarios SET activo = 0 WHERE usuario = 'maria'");
        }
        LoginException e = assertThrows(LoginException.class,
                () -> new AuthService(Clock.systemUTC()).login("maria", "secreta1"));
        assertEquals(LoginException.Tipo.INACTIVO, e.getTipo());
        assertEquals(AuthService.MSG_INACTIVO, e.getMessage());
    }

    @Test
    void bloqueoTrasCincoIntentos() throws Exception {
        config.configurar(DATOS);
        RelojMovible reloj = new RelojMovible();
        AuthService auth = new AuthService(reloj);

        for (int i = 1; i < AuthService.MAX_INTENTOS; i++) {
            LoginException e = assertThrows(LoginException.class, () -> auth.login("maria", "mala"));
            assertEquals(LoginException.Tipo.CREDENCIALES, e.getTipo());
        }
        LoginException quinto = assertThrows(LoginException.class, () -> auth.login("maria", "mala"));
        assertEquals(LoginException.Tipo.BLOQUEADO, quinto.getTipo());
        assertEquals(30, auth.segundosBloqueo());

        // Bloqueado: ni con la contraseña correcta.
        LoginException durante = assertThrows(LoginException.class, () -> auth.login("maria", "secreta1"));
        assertEquals(LoginException.Tipo.BLOQUEADO, durante.getTipo());

        reloj.avanzar(Duration.ofSeconds(10));
        assertEquals(20, auth.segundosBloqueo());
        reloj.avanzar(Duration.ofSeconds(21));
        assertEquals(0, auth.segundosBloqueo());
        auth.login("maria", "secreta1");
        assertTrue(Sesion.haySesion());
    }

    @Test
    void loginsQuedanEnBitacora() throws Exception {
        config.configurar(DATOS);
        AuthService auth = new AuthService(Clock.systemUTC());
        assertThrows(LoginException.class, () -> auth.login("maria", "mala"));
        auth.login("maria", "secreta1");
        try (Connection c = Database.conectar(); Statement st = c.createStatement()) {
            assertEquals("1", uno(st, "SELECT COUNT(*) FROM bitacora WHERE accion='LOGIN_FALLIDO'"));
            assertEquals("1", uno(st, "SELECT COUNT(*) FROM bitacora WHERE accion='LOGIN'"));
        }
    }

    // ---------- Turno ----------

    @Test
    void sinSesionNoSeAbreTurno() {
        assertThrows(IllegalStateException.class, () -> new TurnoService().abrir(0));
    }

    @Test
    void abrirTurnoYMantenerloAbierto() throws Exception {
        config.configurar(DATOS);
        new AuthService(Clock.systemUTC()).login("maria", "secreta1");
        TurnoService turnos = new TurnoService();
        assertTrue(turnos.turnoAbierto().isEmpty());

        Turno t = turnos.abrir(50000);
        assertSame(t, Sesion.turno());

        // "Cerrar y abrir la app": otra consulta encuentra el mismo turno con su fondo.
        Sesion.cerrar();
        Turno abierto = turnos.turnoAbierto().orElseThrow();
        assertEquals(t.getId(), abierto.getId());
        assertEquals(50000, abierto.getFondoInicial());
        assertEquals("María López", abierto.getUsuarioNombre());
    }

    @Test
    void soloUnTurnoAbiertoYFondoNoNegativo() throws Exception {
        config.configurar(DATOS);
        new AuthService(Clock.systemUTC()).login("maria", "secreta1");
        TurnoService turnos = new TurnoService();
        assertThrows(IllegalArgumentException.class, () -> turnos.abrir(-1));
        turnos.abrir(0);
        assertThrows(IllegalStateException.class, () -> turnos.abrir(100));
    }

    @Test
    void detectaTurnoDeDiaAnterior() {
        Turno t = new Turno();
        t.setAbiertoEn(LocalDateTime.of(2026, 11, 20, 22, 0));
        assertTrue(TurnoService.esDeDiaAnterior(t, LocalDate.of(2026, 11, 21)));
        assertFalse(TurnoService.esDeDiaAnterior(t, LocalDate.of(2026, 11, 20)));
    }

    // ---------- utilidades ----------

    private static String uno(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static final class RelojMovible extends Clock {
        private Instant ahora = Instant.parse("2026-11-21T15:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
