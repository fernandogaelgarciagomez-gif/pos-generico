package com.fgsistemas.pos.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.regex.Pattern;

/**
 * Ayudas para operar todo sin mouse (docs/04-teclado-y-atajos.md).
 * Los atajos se registran con event filters sobre la raíz de cada pantalla o diálogo.
 */
public final class Teclado {

    private Teclado() {
    }

    /** Pone el foco en {@code nodo} cuando la vista ya está en pantalla. */
    public static void enfocar(Node nodo) {
        Platform.runLater(nodo::requestFocus);
    }

    /**
     * Enter en cada nodo avanza al siguiente; Enter en el último ejecuta {@code accionFinal}
     * (Guardar / Entrar / Cobrar). Si el último es un botón, Enter lo dispara.
     */
    public static void cadenaEnter(Runnable accionFinal, Node... nodos) {
        for (int i = 0; i < nodos.length; i++) {
            final int indice = i;
            nodos[i].addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() != KeyCode.ENTER || e.isShiftDown() || e.isControlDown() || e.isAltDown()) {
                    return;
                }
                e.consume();
                if (indice < nodos.length - 1) {
                    nodos[indice + 1].requestFocus();
                } else {
                    accionFinal.run();
                }
            });
        }
    }

    /** Orden explícito de Tab / Shift+Tab entre los nodos dados (circular). */
    public static void ordenTab(Node... nodos) {
        for (int i = 0; i < nodos.length; i++) {
            final int indice = i;
            nodos[i].addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() != KeyCode.TAB || e.isControlDown() || e.isAltDown()) {
                    return;
                }
                e.consume();
                int destino = e.isShiftDown()
                        ? (indice - 1 + nodos.length) % nodos.length
                        : (indice + 1) % nodos.length;
                nodos[destino].requestFocus();
            });
        }
    }

    /** Enter dispara el botón cuando tiene el foco (JavaFX por defecto solo usa Espacio). */
    public static void enterDispara(Button... botones) {
        for (Button b : botones) {
            b.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    e.consume();
                    b.fire();
                }
            });
        }
    }

    /** Al recibir foco selecciona todo, para que el cajero sobrescriba sin borrar. */
    public static void seleccionarTodoAlFoco(TextInputControl campo) {
        campo.focusedProperty().addListener((obs, antes, ahora) -> {
            if (ahora) {
                Platform.runLater(campo::selectAll);
            }
        });
    }

    /** Campo que solo acepta dígitos y, opcionalmente, un punto con hasta {@code maxDecimales}. */
    public static void soloNumeros(TextField campo, int maxDecimales) {
        Pattern permitido = maxDecimales <= 0
                ? Pattern.compile("\\d*")
                : Pattern.compile("\\d*(\\.\\d{0," + maxDecimales + "})?");
        campo.setTextFormatter(new TextFormatter<String>(cambio ->
                permitido.matcher(cambio.getControlNewText()).matches() ? cambio : null));
        seleccionarTodoAlFoco(campo);
    }

    /** Campo de dinero: dígitos con hasta 2 decimales. */
    public static void campoDinero(TextField campo) {
        soloNumeros(campo, 2);
    }

    /** ¿El nodo es un campo donde el usuario escribe texto? */
    public static boolean esCampoTexto(Node nodo) {
        return nodo instanceof TextInputControl
                || (nodo instanceof ComboBox<?> && ((ComboBox<?>) nodo).isEditable());
    }

    /** Atajo con combinación (F2, Ctrl+S, ...) sobre la raíz de una pantalla o diálogo. */
    public static void atajo(Node raiz, KeyCombination combinacion, Runnable accion) {
        raiz.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (combinacion.match(e)) {
                e.consume();
                accion.run();
            }
        });
    }

    public static void atajo(Node raiz, KeyCode tecla, Runnable accion) {
        atajo(raiz, new KeyCodeCombination(tecla), accion);
    }

    /**
     * Atajo de una sola letra/tecla sin modificadores. Solo se activa si el foco NO está
     * en un campo de texto (para no robarle letras al cajero).
     */
    public static void atajoLetra(Node raiz, KeyCode tecla, Runnable accion) {
        raiz.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != tecla || e.isControlDown() || e.isAltDown() || e.isShortcutDown()) {
                return;
            }
            Scene escena = raiz.getScene();
            Node foco = escena == null ? null : escena.getFocusOwner();
            if (foco != null && esCampoTexto(foco)) {
                return;
            }
            e.consume();
            accion.run();
        });
    }

    /** ComboBox cerrado: ↑ / ↓ cambian la opción sin abrir la lista (Alt+↓ la abre). */
    public static void comboConFlechas(ComboBox<?> combo) {
        combo.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (combo.isShowing() || e.isAltDown()) {
                return;
            }
            int total = combo.getItems().size();
            if (total == 0) {
                return;
            }
            int i = combo.getSelectionModel().getSelectedIndex();
            if (e.getCode() == KeyCode.DOWN) {
                combo.getSelectionModel().select(Math.min(i + 1, total - 1));
                e.consume();
            } else if (e.getCode() == KeyCode.UP) {
                combo.getSelectionModel().select(Math.max(i - 1, 0));
                e.consume();
            }
        });
    }

    /** "Nuevo": Insert y su alternativa Ctrl+N (laptops sin Insert). */
    public static void atajoNuevo(Node raiz, Runnable accion) {
        atajo(raiz, new KeyCodeCombination(KeyCode.INSERT), accion);
        atajo(raiz, new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), accion);
    }
}
