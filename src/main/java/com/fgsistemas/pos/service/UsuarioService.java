package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.UsuarioDao;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Rol;
import com.fgsistemas.pos.model.Usuario;
import com.fgsistemas.pos.util.Sesion;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Administración de usuarios (docs/modulos/21-usuarios-y-permisos.md).
 * Los usuarios nunca se borran (tienen ventas asociadas): solo se desactivan.
 */
public class UsuarioService {

    static final String MSG_ULTIMO_ADMIN = "Debe quedar al menos un administrador activo.";
    static final String MSG_A_SI_MISMO = "No puedes desactivarte ni quitarte el rol de administrador a ti mismo.";

    private final UsuarioDao usuarioDao = new UsuarioDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();

    public List<Usuario> listar() throws SQLException {
        Sesion.requiere(Permiso.USUARIOS);
        try (Connection c = Database.conectar()) {
            return usuarioDao.listar(c);
        }
    }

    /** @throws IllegalArgumentException datos inválidos o usuario repetido (mensaje para el usuario) */
    public Usuario crear(String nombre, String usuario, String password, String confirmacion, Rol rol)
            throws SQLException {
        Sesion.requiere(Permiso.USUARIOS);
        Validacion.exigir(Validacion.nombre(nombre));
        Validacion.exigir(Validacion.usuario(usuario));
        Validacion.exigir(Validacion.password(password));
        Validacion.exigir(Validacion.confirmacion(password, confirmacion));
        if (rol == null) {
            throw new IllegalArgumentException("Elige un rol.");
        }
        Usuario nuevo = new Usuario();
        nuevo.setNombre(nombre.trim());
        nuevo.setUsuario(usuario);
        nuevo.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        nuevo.setRol(rol);
        return Database.enTransaccion(c -> {
            if (usuarioDao.buscarPorUsuario(c, usuario).isPresent()) {
                throw new IllegalArgumentException("El usuario \"" + usuario + "\" ya existe.");
            }
            usuarioDao.insertar(c, nuevo);
            bitacoraDao.registrar(c, yo(), "USUARIO_CREADO",
                    nuevo.getUsuario() + " (" + rol.etiqueta() + ")");
            return nuevo;
        });
    }

    /**
     * Cambia nombre, rol y estado.
     * DECISION: un admin tampoco puede quitarse a sí mismo el rol de admin (se quedaría fuera de esta pantalla).
     */
    public void editar(long id, String nombre, Rol rol, boolean activo) throws SQLException {
        Sesion.requiere(Permiso.USUARIOS);
        Validacion.exigir(Validacion.nombre(nombre));
        if (rol == null) {
            throw new IllegalArgumentException("Elige un rol.");
        }
        String nombreLimpio = nombre.trim();
        Database.enTransaccion(c -> {
            Usuario actual = usuarioDao.buscarPorId(c, id)
                    .orElseThrow(() -> new IllegalArgumentException("El usuario ya no existe."));
            validarReglasAdmin(c, actual, rol, activo);
            usuarioDao.actualizar(c, id, nombreLimpio, rol, activo);

            List<String> cambios = new ArrayList<>();
            if (!actual.getNombre().equals(nombreLimpio)) {
                cambios.add("nombre: " + actual.getNombre() + " → " + nombreLimpio);
            }
            if (actual.getRol() != rol) {
                cambios.add("rol: " + actual.getRol().etiqueta() + " → " + rol.etiqueta());
            }
            if (actual.isActivo() != activo) {
                cambios.add(activo ? "reactivado" : "desactivado");
            }
            if (!cambios.isEmpty()) {
                String accion = (actual.isActivo() && !activo) ? "USUARIO_DESACTIVADO" : "USUARIO_EDITADO";
                bitacoraDao.registrar(c, yo(), accion, actual.getUsuario() + ": " + String.join(", ", cambios));
            }
            return null;
        });
        if (Sesion.usuario() != null && Sesion.usuario().getId() == id) {
            Sesion.usuario().setNombre(nombreLimpio);
        }
    }

    public void desactivar(long id) throws SQLException {
        Sesion.requiere(Permiso.USUARIOS);
        Usuario u;
        try (Connection c = Database.conectar()) {
            u = usuarioDao.buscarPorId(c, id)
                    .orElseThrow(() -> new IllegalArgumentException("El usuario ya no existe."));
        }
        editar(id, u.getNombre(), u.getRol(), false);
    }

    /** El admin asigna una contraseña nueva a otro usuario. */
    public void restablecerPassword(long id, String password, String confirmacion) throws SQLException {
        Sesion.requiere(Permiso.USUARIOS);
        Validacion.exigir(Validacion.password(password));
        Validacion.exigir(Validacion.confirmacion(password, confirmacion));
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        Database.enTransaccion(c -> {
            Usuario u = usuarioDao.buscarPorId(c, id)
                    .orElseThrow(() -> new IllegalArgumentException("El usuario ya no existe."));
            usuarioDao.actualizarPassword(c, id, hash);
            bitacoraDao.registrar(c, yo(), "PASSWORD_RESTABLECIDA", u.getUsuario());
            return null;
        });
    }

    /** Cualquier usuario cambia su propia contraseña (Ctrl+P), pidiendo la actual. */
    public void cambiarPropiaPassword(String actual, String nueva, String confirmacion) throws SQLException {
        Usuario u = Sesion.usuario();
        if (u == null) {
            throw new IllegalStateException("No hay sesión iniciada.");
        }
        if (actual == null || actual.isEmpty()) {
            throw new IllegalArgumentException("Escribe tu contraseña actual.");
        }
        Validacion.exigir(Validacion.password(nueva));
        Validacion.exigir(Validacion.confirmacion(nueva, confirmacion));
        String hash = BCrypt.hashpw(nueva, BCrypt.gensalt());
        Database.enTransaccion(c -> {
            Usuario enBd = usuarioDao.buscarPorId(c, u.getId())
                    .orElseThrow(() -> new IllegalStateException("Tu usuario ya no existe."));
            if (!BCrypt.checkpw(actual, enBd.getPasswordHash())) {
                throw new IllegalArgumentException("La contraseña actual no es correcta.");
            }
            usuarioDao.actualizarPassword(c, u.getId(), hash);
            bitacoraDao.registrar(c, u.getId(), "PASSWORD_CAMBIADA", "Cambió su propia contraseña");
            return null;
        });
        u.setPasswordHash(hash);
    }

    private void validarReglasAdmin(Connection c, Usuario actual, Rol rolNuevo, boolean activoNuevo)
            throws SQLException {
        boolean dejaDeSerAdminActivo = actual.esAdmin() && actual.isActivo()
                && (rolNuevo != Rol.ADMIN || !activoNuevo);
        if (!dejaDeSerAdminActivo) {
            return;
        }
        if (Sesion.usuario() != null && Sesion.usuario().getId() == actual.getId()) {
            throw new IllegalArgumentException(MSG_A_SI_MISMO);
        }
        if (usuarioDao.contarAdminsActivos(c) <= 1) {
            throw new IllegalArgumentException(MSG_ULTIMO_ADMIN);
        }
    }

    private static Long yo() {
        return Sesion.usuario() == null ? null : Sesion.usuario().getId();
    }
}
