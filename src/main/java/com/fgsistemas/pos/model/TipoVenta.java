package com.fgsistemas.pos.model;

/** Cómo se vende un producto. Las cantidades siempre se guardan en milésimas. */
public enum TipoVenta {
    PIEZA("Pieza", "pza"),
    PESO("Peso", "kg"),
    FRACCION("Fracción", "m");

    private final String etiqueta;
    private final String unidadDefault;

    TipoVenta(String etiqueta, String unidadDefault) {
        this.etiqueta = etiqueta;
        this.unidadDefault = unidadDefault;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public String unidadDefault() {
        return unidadDefault;
    }

    /** PIEZA solo acepta enteros; PESO y FRACCION hasta 3 decimales. */
    public boolean aceptaDecimales() {
        return this != PIEZA;
    }
}
