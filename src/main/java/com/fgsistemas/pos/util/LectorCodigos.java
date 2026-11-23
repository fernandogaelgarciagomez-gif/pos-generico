package com.fgsistemas.pos.util;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;

/**
 * Lector de código de barras USB genérico (modo teclado: "escribe" el código y manda Enter).
 * <p>
 * Si el foco no está en un campo de texto (p. ej. quedó en la tabla o en un botón después de un clic),
 * lo que teclee el lector se manda al campo de código para que el producto entre igual. Así no se pierde
 * una lectura ni se activa por accidente otra cosa.
 */
public final class LectorCodigos {

    private LectorCodigos() {
    }

    /** Redirige al campo {@code destino} las teclas escritas en {@code raiz} fuera de un campo de texto. */
    public static void capturarEn(Node raiz, TextField destino) {
        raiz.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            Scene escena = raiz.getScene();
            Node foco = escena == null ? null : escena.getFocusOwner();
            if (e.getTarget() == destino || foco == destino || (foco != null && Teclado.esCampoTexto(foco)) || destino.isDisabled()) {
                return;
            }
            String ch = e.getCharacter();
            if (ch == null || ch.isEmpty() || Character.isISOControl(ch.charAt(0))
                    || e.isControlDown() || e.isAltDown() || e.isMetaDown()) {
                return;
            }
            e.consume();
            destino.requestFocus();
            destino.positionCaret(destino.getText().length());
            Event.fireEvent(destino, e.copyFor(destino, destino));
        });
    }
}
