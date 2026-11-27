package com.fgsistemas.pos.service;

import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Rol;
import com.fgsistemas.pos.model.Usuario;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Qué permisos tiene cada rol (docs/modulos/21-usuarios-y-permisos.md).
 * Vive en código, no en BD. Toda consulta pasa por {@link #tiene(Usuario, Permiso)} para que
 * en el futuro se puedan agregar permisos personalizados por usuario sin tocar a los llamadores.
 */
public final class Permisos {

    private static final Map<Rol, Set<Permiso>> POR_ROL = new EnumMap<>(Rol.class);

    static {
        POR_ROL.put(Rol.ADMIN, Collections.unmodifiableSet(EnumSet.allOf(Permiso.class)));
        // CANCELAR_VENTA, DESCUENTOS y RETIROS_CAJA: el cajero los hace solo con autorización de admin.
        POR_ROL.put(Rol.CAJERO, Collections.unmodifiableSet(EnumSet.of(
                Permiso.VENDER,
                Permiso.VER_INVENTARIO,
                Permiso.VER_RESUMEN_DIA,
                Permiso.HACER_CORTE,
                Permiso.FIADOS_VENDER,
                Permiso.FIADOS_ABONAR,
                Permiso.SALIDA_SIN_COBRO)));
    }

    private Permisos() {
    }

    public static Set<Permiso> deRol(Rol rol) {
        return POR_ROL.getOrDefault(rol, Collections.emptySet());
    }

    public static boolean tiene(Usuario usuario, Permiso permiso) {
        if (usuario == null || !usuario.isActivo() || usuario.getRol() == null) {
            return false;
        }
        return deRol(usuario.getRol()).contains(permiso);
    }
}
