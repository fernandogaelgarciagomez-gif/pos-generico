package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.UsuarioDao;
import com.fgsistemas.pos.model.Usuario;
import com.fgsistemas.pos.util.Sesion;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Inicio y cierre de sesión (docs/modulos/11-login.md).
 * Tras {@link #MAX_INTENTOS} fallos seguidos bloquea {@link #BLOQUEO}.
 * DECISION: el contador es global para la caja (una sola terminal), no por usuario.
 */
public class AuthService {

    public static final int MAX_INTENTOS = 5;
    public static final Duration BLOQUEO = Duration.ofSeconds(30);
    static final String MSG_CREDENCIALES = "Usuario o contraseña incorrectos";
    static final String MSG_INACTIVO = "Este usuario está desactivado. Contacta al administrador.";

    private static final AuthService INSTANCIA = new AuthService(Clock.systemUTC());

    private final UsuarioDao usuarioDao = new UsuarioDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();
    private final Clock reloj;

    private int fallidosSeguidos;
    private Instant bloqueadoHasta;

    AuthService(Clock reloj) {
        this.reloj = reloj;
    }

    public static AuthService instancia() {
        return INSTANCIA;
    }

    /** Segundos que faltan del bloqueo (0 si no está bloqueado). */
    public synchronized long segundosBloqueo() {
        if (bloqueadoHasta == null) {
            return 0;
        }
        long ms = Duration.between(reloj.instant(), bloqueadoHasta).toMillis();
        if (ms <= 0) {
            bloqueadoHasta = null;
            return 0;
        }
        return (ms + 999) / 1000;
    }

    /**
     * Valida credenciales e inicia la {@link Sesion}.
     *
     * @throws LoginException con el motivo (mensaje para el usuario)
     */
    public synchronized Usuario login(String usuario, String password) throws SQLException, LoginException {
        long espera = segundosBloqueo();
        if (espera > 0) {
            throw bloqueado(espera);
        }
        String clave = usuario == null ? "" : usuario.trim().toLowerCase();
        String pw = password == null ? "" : password;

        try (Connection c = Database.conectar()) {
            Optional<Usuario> encontrado = clave.isEmpty() ? Optional.empty() : usuarioDao.buscarPorUsuario(c, clave);
            boolean correcta = encontrado.isPresent()
                    ? coincide(pw, encontrado.get().getPasswordHash())
                    : coincide(pw, HashFalso.VALOR); // mismo tiempo de respuesta exista o no el usuario

            if (!correcta) {
                fallidosSeguidos++;
                bitacoraDao.registrar(c, encontrado.map(Usuario::getId).orElse(null),
                        "LOGIN_FALLIDO", "Usuario: " + clave);
                if (fallidosSeguidos >= MAX_INTENTOS) {
                    fallidosSeguidos = 0;
                    bloqueadoHasta = reloj.instant().plus(BLOQUEO);
                    bitacoraDao.registrar(c, null, "LOGIN_BLOQUEADO",
                            MAX_INTENTOS + " intentos fallidos; bloqueo de " + BLOQUEO.toSeconds() + " s");
                    throw bloqueado(BLOQUEO.toSeconds());
                }
                throw new LoginException(LoginException.Tipo.CREDENCIALES, MSG_CREDENCIALES, 0);
            }

            Usuario u = encontrado.get();
            if (!u.isActivo()) {
                bitacoraDao.registrar(c, u.getId(), "LOGIN_INACTIVO", "Intento de usuario desactivado");
                throw new LoginException(LoginException.Tipo.INACTIVO, MSG_INACTIVO, 0);
            }

            fallidosSeguidos = 0;
            bitacoraDao.registrar(c, u.getId(), "LOGIN", "Inicio de sesión");
            Sesion.iniciar(u);
            return u;
        }
    }

    /**
     * Autorización de administrador "en caliente" para una sola acción (p. ej. cancelar una venta
     * desde la sesión de un cajero). Queda en bitácora.
     *
     * @throws IllegalArgumentException credenciales inválidas o el usuario no es admin activo
     */
    public Autorizacion autorizarAdmin(String usuario, String password, String accion) throws SQLException {
        String clave = usuario == null ? "" : usuario.trim().toLowerCase();
        try (Connection c = Database.conectar()) {
            Optional<Usuario> u = clave.isEmpty() ? Optional.empty() : usuarioDao.buscarPorUsuario(c, clave);
            boolean ok = u.isPresent() && coincide(password == null ? "" : password, u.get().getPasswordHash())
                    && u.get().isActivo() && u.get().esAdmin();
            Long quienPide = Sesion.usuario() == null ? null : Sesion.usuario().getId();
            if (!ok) {
                bitacoraDao.registrar(c, quienPide, "AUTORIZACION_FALLIDA", accion + " · usuario: " + clave);
                throw new IllegalArgumentException("Usuario o contraseña de administrador incorrectos.");
            }
            bitacoraDao.registrar(c, quienPide, "AUTORIZACION", accion + " · autorizó " + u.get().getNombre());
            return new Autorizacion(u.get().getId(), u.get().getNombre());
        }
    }

    /** Cierra la sesión (el turno sigue abierto hasta el corte). */
    public void logout() {
        Usuario u = Sesion.usuario();
        if (u != null) {
            try (Connection c = Database.conectar()) {
                bitacoraDao.registrar(c, u.getId(), "LOGOUT", "Cierre de sesión");
            } catch (SQLException | IllegalStateException ignorada) {
                // No impedir el cierre de sesión por un fallo de bitácora.
            }
        }
        Sesion.cerrar();
    }

    private static boolean coincide(String password, String hash) {
        try {
            return hash != null && BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException hashInvalido) {
            return false;
        }
    }

    private static LoginException bloqueado(long segundos) {
        return new LoginException(LoginException.Tipo.BLOQUEADO,
                "Demasiados intentos. Espera " + segundos + " s.", segundos);
    }

    /** Hash calculado una sola vez y solo si se necesita. */
    private static final class HashFalso {
        static final String VALOR = BCrypt.hashpw("sin-usuario", BCrypt.gensalt());
    }
}
