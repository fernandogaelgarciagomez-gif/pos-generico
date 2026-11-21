package com.fgsistemas.pos.service;

import com.fgsistemas.pos.model.Permiso;

/** Se lanza desde los servicios cuando el usuario actual no tiene el permiso requerido. */
public class PermisoDenegadoException extends RuntimeException {

    private final Permiso permiso;

    public PermisoDenegadoException(Permiso permiso) {
        super("No tienes permiso para realizar esta acción.");
        this.permiso = permiso;
    }

    public Permiso getPermiso() {
        return permiso;
    }
}
