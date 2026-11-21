package com.fgsistemas.pos.util;

import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Turno;
import com.fgsistemas.pos.model.Usuario;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.service.Permisos;

/** Usuario y turno actuales. Una sola caja → un solo estado global. */
public final class Sesion {

    private static Usuario usuarioActual;
    private static Turno turnoActual;

    private Sesion() {
    }

    public static void iniciar(Usuario usuario) {
        usuarioActual = usuario;
        turnoActual = null;
    }

    /** Limpia la sesión. El turno sigue abierto en BD hasta hacer el corte. */
    public static void cerrar() {
        usuarioActual = null;
        turnoActual = null;
    }

    public static boolean haySesion() {
        return usuarioActual != null;
    }

    public static Usuario usuario() {
        return usuarioActual;
    }

    public static Turno turno() {
        return turnoActual;
    }

    public static void setTurno(Turno turno) {
        turnoActual = turno;
    }

    public static boolean esAdmin() {
        return usuarioActual != null && usuarioActual.esAdmin();
    }

    public static boolean tiene(Permiso permiso) {
        return Permisos.tiene(usuarioActual, permiso);
    }

    /** Para usar al inicio de cada método de servicio protegido. */
    public static void requiere(Permiso permiso) {
        if (!tiene(permiso)) {
            throw new PermisoDenegadoException(permiso);
        }
    }
}
