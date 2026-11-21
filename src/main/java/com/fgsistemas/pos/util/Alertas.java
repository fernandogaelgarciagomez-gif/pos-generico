package com.fgsistemas.pos.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Optional;

/**
 * Diálogos y toasts con el tema azul marino. Todos se cierran con Esc.
 * Enter activa el botón que tiene el foco.
 */
public final class Alertas {

    private static final ButtonType SI = new ButtonType("Sí", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType NO = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);
    private static final ButtonType ACEPTAR = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);

    private static int toastsVisibles;

    private Alertas() {
    }

    public static void error(String mensaje) {
        mostrar(Alert.AlertType.ERROR, "Error", mensaje);
    }

    public static void advertencia(String mensaje) {
        mostrar(Alert.AlertType.WARNING, "Atención", mensaje);
    }

    public static void info(String titulo, String mensaje) {
        mostrar(Alert.AlertType.INFORMATION, titulo, mensaje);
    }

    /** Confirmación normal: Enter = Sí, Esc = No. */
    public static boolean confirmar(String mensaje) {
        return preguntar(mensaje, false);
    }

    /**
     * Confirmación de acción peligrosa (cancelar venta, cerrar turno, borrar):
     * el foco y el botón predeterminado son "No"; para aceptar hay que pasar a "Sí" con Tab.
     */
    public static boolean confirmarPeligro(String mensaje) {
        return preguntar(mensaje, true);
    }

    private static boolean preguntar(String mensaje, boolean peligro) {
        Alert alerta = crear(Alert.AlertType.CONFIRMATION, "Confirmar", mensaje, SI, NO);
        DialogPane panel = alerta.getDialogPane();
        Button si = (Button) panel.lookupButton(SI);
        Button no = (Button) panel.lookupButton(NO);
        if (peligro) {
            si.setDefaultButton(false);
            si.getStyleClass().add("boton-peligro");
            no.setDefaultButton(true);
            alerta.setOnShown(e -> no.requestFocus());
        } else {
            alerta.setOnShown(e -> si.requestFocus());
        }
        Optional<ButtonType> r = alerta.showAndWait();
        return r.isPresent() && r.get() == SI;
    }

    private static void mostrar(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = crear(tipo, titulo, mensaje, ACEPTAR);
        alerta.showAndWait();
    }

    private static Alert crear(Alert.AlertType tipo, String titulo, String mensaje, ButtonType... botones) {
        Alert alerta = new Alert(tipo, mensaje, botones);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setGraphic(null);
        estilizar(alerta);
        return alerta;
    }

    /**
     * Aplica el tema, el dueño y el comportamiento de teclado a cualquier diálogo
     * (Enter activa el botón enfocado; Esc cierra si el diálogo tiene botón de cancelar).
     */
    public static void estilizar(Dialog<?> dialogo) {
        Stage dueno = Navegador.stage();
        if (dialogo.getOwner() == null && dueno != null && dueno.isShowing()) {
            dialogo.initOwner(dueno);
        }
        DialogPane panel = dialogo.getDialogPane();
        try {
            panel.getStylesheets().add(Navegador.hojaEstilos());
        } catch (IllegalStateException sinCss) {
            // Si faltara la hoja de estilos, el diálogo se muestra con el estilo por defecto.
        }
        panel.getStyleClass().add("dialogo");
        // Enter activa el botón enfocado (no siempre el predeterminado).
        panel.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Node foco = panel.getScene().getFocusOwner();
                if (foco instanceof Button) {
                    e.consume();
                    ((Button) foco).fire();
                }
            }
        });
    }

    /** Mensaje breve en la esquina inferior derecha (2.5 s). No roba el foco. */
    public static void toast(String mensaje) {
        Stage dueno = Navegador.stage();
        if (dueno == null || !dueno.isShowing()) {
            return;
        }
        Label etiqueta = new Label(mensaje);
        etiqueta.getStyleClass().add("toast");
        etiqueta.getStylesheets().add(Navegador.hojaEstilos());

        Popup popup = new Popup();
        popup.getContent().add(etiqueta);
        popup.setAutoFix(true);
        popup.setAutoHide(false);
        popup.show(dueno);
        // Si hay varios toasts a la vez, se apilan hacia arriba.
        int posicion = toastsVisibles++;
        popup.setX(dueno.getX() + dueno.getWidth() - popup.getWidth() - 24);
        popup.setY(dueno.getY() + dueno.getHeight() - popup.getHeight() - 72 - posicion * (popup.getHeight() + 12));

        FadeTransition entrada = new FadeTransition(Duration.millis(150), etiqueta);
        entrada.setFromValue(0);
        entrada.setToValue(1);
        PauseTransition pausa = new PauseTransition(Duration.millis(2500));
        FadeTransition salida = new FadeTransition(Duration.millis(300), etiqueta);
        salida.setFromValue(1);
        salida.setToValue(0);
        SequentialTransition secuencia = new SequentialTransition(entrada, pausa, salida);
        secuencia.setOnFinished(e -> {
            popup.hide();
            toastsVisibles = Math.max(0, toastsVisibles - 1);
        });
        secuencia.play();
    }
}
