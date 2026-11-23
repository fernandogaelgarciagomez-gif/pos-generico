package com.fgsistemas.pos;

/**
 * Punto de entrada del .jar y del instalador (Fase 9).
 * JavaFX exige que la clase principal de un jar "normal" (sin módulos) NO extienda Application;
 * por eso este lanzador solo llama a {@link App#main}.
 */
public final class Lanzador {

    private Lanzador() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
