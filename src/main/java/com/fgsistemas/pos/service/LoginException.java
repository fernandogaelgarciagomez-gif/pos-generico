package com.fgsistemas.pos.service;

/** Login rechazado. El mensaje ya está listo para mostrarse al usuario. */
public class LoginException extends Exception {

    public enum Tipo { CREDENCIALES, INACTIVO, BLOQUEADO }

    private final Tipo tipo;
    private final long segundosBloqueo;

    public LoginException(Tipo tipo, String mensaje, long segundosBloqueo) {
        super(mensaje);
        this.tipo = tipo;
        this.segundosBloqueo = segundosBloqueo;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public long getSegundosBloqueo() {
        return segundosBloqueo;
    }
}
