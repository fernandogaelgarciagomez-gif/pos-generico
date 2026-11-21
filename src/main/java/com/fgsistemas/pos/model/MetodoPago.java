package com.fgsistemas.pos.model;

public enum MetodoPago {
    EFECTIVO("Efectivo", "E"),
    TARJETA("Tarjeta", "T"),
    TRANSFERENCIA("Transferencia", "R"),
    FIADO("Fiado", "F");

    private final String etiqueta;
    private final String tecla;

    MetodoPago(String etiqueta, String tecla) {
        this.etiqueta = etiqueta;
        this.tecla = tecla;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public String tecla() {
        return tecla;
    }
}
