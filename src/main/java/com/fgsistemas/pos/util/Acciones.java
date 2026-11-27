package com.fgsistemas.pos.util;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Registra cada acción de una pantalla UNA sola vez: como botón visible (con su atajo escrito)
 * y como atajo de teclado. Así ninguna función existe solo como tecla (docs/05-decisiones.md #15).
 */
public final class Acciones {

    private Acciones() {
    }

    /**
     * Agrega un botón a {@code barra} y, si {@code tecla} no es null, registra el atajo en {@code raiz}.
     *
     * @param teclaTexto       texto del atajo que se muestra en el botón (ej. "F2", "Ins/Ctrl+N", "Enter")
     * @param soloFueraDeTexto si true, el atajo no se activa mientras se escribe en un campo de texto
     *                         (para letras sueltas, Supr, Ctrl+A...)
     */
    public static Button agregar(Pane barra, Node raiz, String texto, String teclaTexto,
                                 KeyCombination tecla, boolean soloFueraDeTexto, Runnable accion) {
        Button b = boton(texto, teclaTexto, accion);
        barra.getChildren().add(b);
        if (tecla != null) {
            atajo(raiz, tecla, soloFueraDeTexto, accion);
        }
        return b;
    }

    /**
     * Botón para regresar (Esc), igual en todas las pantallas: ámbar con flecha y SIEMPRE el primero de la barra,
     * para encontrarlo luego luego (pedido del cliente, decisión 87).
     */
    public static Button regresar(Pane barra, Node raiz, String texto, Runnable accion) {
        Button b = boton(texto, "Esc", accion);
        estiloRegresar(b);
        barra.getChildren().add(0, b);
        atajo(raiz, new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.ESCAPE), false, accion);
        return b;
    }

    /** Da el aspecto del botón de regresar (ámbar con flecha) a un botón ya creado con {@link #boton}. */
    public static Button estiloRegresar(Button b) {
        b.getStyleClass().add("boton-regresar");
        org.kordamp.ikonli.javafx.FontIcon flecha = new org.kordamp.ikonli.javafx.FontIcon("mdi2a-arrow-left-bold");
        flecha.getStyleClass().add("icono-regresar");
        HBox grafico = b.getGraphic() == null ? new HBox(6, flecha) : new HBox(6, flecha, b.getGraphic());
        grafico.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        b.setGraphic(grafico);
        b.setContentDisplay(ContentDisplay.LEFT);
        return b;
    }

    /** Botón de acción sin registrar atajo (el atajo lo maneja la pantalla, p. ej. Enter en una tabla). */
    public static Button boton(String texto, String teclaTexto, Runnable accion) {
        Button b = new Button(texto);
        b.getStyleClass().add("boton-accion");
        b.setFocusTraversable(false); // no estorba el flujo con Tab
        b.setMinWidth(Region.USE_PREF_SIZE); // nunca recortar el texto ("Ajust…")
        if (teclaTexto != null && !teclaTexto.isBlank()) {
            Label chip = new Label(teclaTexto);
            chip.getStyleClass().add("tecla-chip");
            b.setGraphic(chip);
            b.setContentDisplay(ContentDisplay.LEFT);
        }
        b.setOnAction(e -> accion.run());
        return b;
    }

    public static void atajo(Node raiz, KeyCombination tecla, boolean soloFueraDeTexto, Runnable accion) {
        raiz.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!tecla.match(e)) {
                return;
            }
            if (soloFueraDeTexto) {
                Scene escena = raiz.getScene();
                Node foco = escena == null ? null : escena.getFocusOwner();
                if (foco != null && Teclado.esCampoTexto(foco)) {
                    return;
                }
            }
            e.consume();
            accion.run();
        });
    }

    /**
     * Empuja los botones siguientes al extremo derecho de la barra (solo en HBox).
     * En la barra de acciones (FlowPane, que salta de fila si no caben) no hace nada.
     */
    public static void espaciador(Pane barra) {
        if (!(barra instanceof HBox)) {
            return;
        }
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        barra.getChildren().add(r);
    }
}
