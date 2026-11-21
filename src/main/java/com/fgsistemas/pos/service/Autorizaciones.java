package com.fgsistemas.pos.service;

import com.fgsistemas.pos.dao.UsuarioDao;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Usuario;
import com.fgsistemas.pos.util.Sesion;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/** Valida "tiene el permiso, o trae la autorización de un admin activo". */
final class Autorizaciones {

    private static final UsuarioDao USUARIOS = new UsuarioDao();

    private Autorizaciones() {
    }

    /**
     * @return el id del admin que autorizó, o null si el usuario actual tiene el permiso por sí mismo
     * @throws PermisoDenegadoException si no tiene el permiso ni una autorización válida
     */
    static Long verificar(Connection c, Permiso permiso, Autorizacion autorizacion) throws SQLException {
        if (Sesion.tiene(permiso)) {
            return null;
        }
        if (autorizacion == null) {
            throw new PermisoDenegadoException(permiso);
        }
        Optional<Usuario> admin = USUARIOS.buscarPorId(c, autorizacion.adminId());
        if (admin.isEmpty() || !admin.get().isActivo() || !admin.get().esAdmin()) {
            throw new PermisoDenegadoException(permiso);
        }
        return admin.get().getId();
    }
}
