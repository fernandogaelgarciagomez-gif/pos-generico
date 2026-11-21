package com.fgsistemas.pos.model;

public enum Rol {
    ADMIN("Administrador"),
    CAJERO("Cajero");

    private final String etiqueta;

    Rol(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
