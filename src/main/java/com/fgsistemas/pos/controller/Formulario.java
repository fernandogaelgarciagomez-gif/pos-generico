package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Teclado;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Diálogo de formulario con el tema y navegación por teclado:
 * Enter avanza entre campos y en el último guarda; Esc cancela.
 * La acción de guardar regresa un mensaje de error (se muestra y el diálogo sigue abierto) o null.
 */
final class Formulario {

    private final Dialog<ButtonType> dialogo = new Dialog<>();
    private final ButtonType guardar;
    private final GridPane grid = new GridPane();
    private final Label lblError = new Label();
    private final List<Node> campos = new ArrayList<>();

    Formulario(String titulo, String textoGuardar) {
        dialogo.setTitle(titulo);
        guardar = new ButtonType(textoGuardar, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelar = new ButtonType("Cancelar (Esc)", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogo.getDialogPane().getButtonTypes().addAll(guardar, cancelar);

        grid.setHgap(16);
        grid.setVgap(10);
        ColumnConstraints etiquetas = new ColumnConstraints();
        ColumnConstraints controles = new ColumnConstraints(420);
        controles.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(etiquetas, controles);

        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("subtitulo");
        lblError.getStyleClass().add("texto-error");
        lblError.setWrapText(true);
        lblError.setMaxWidth(460);

        VBox contenido = new VBox(16, lblTitulo, grid, lblError);
        dialogo.getDialogPane().setContent(contenido);
        Alertas.estilizar(dialogo);
    }

    Formulario campo(String etiqueta, Node control) {
        int fila = campos.size();
        grid.add(new Label(etiqueta), 0, fila);
        grid.add(control, 1, fila);
        campos.add(control);
        return this;
    }

    /** Campo con botones al lado (p. ej. "+ Nueva categoría"). Solo {@code control} entra en la cadena de Enter. */
    Formulario campo(String etiqueta, Node control, Node... extras) {
        int fila = campos.size();
        javafx.scene.layout.HBox caja = new javafx.scene.layout.HBox(8);
        caja.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        caja.getChildren().add(control);
        javafx.scene.layout.HBox.setHgrow(control, Priority.ALWAYS);
        caja.getChildren().addAll(extras);
        grid.add(new Label(etiqueta), 0, fila);
        grid.add(caja, 1, fila);
        campos.add(control);
        return this;
    }

    /** Atajo de teclado mientras el formulario está abierto. */
    Formulario atajo(javafx.scene.input.KeyCombination tecla, Runnable accion) {
        dialogo.getDialogPane().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (tecla.match(e)) {
                e.consume();
                accion.run();
            }
        });
        return this;
    }

    Formulario nota(String texto) {
        Label nota = new Label(texto);
        nota.getStyleClass().add("texto-pequeno");
        nota.setWrapText(true);
        grid.add(nota, 0, campos.size() + 1, 2, 1);
        return this;
    }

    /**
     * Muestra el diálogo hasta que {@code accion} tenga éxito (regrese null) o el usuario cancele.
     *
     * @return true si se guardó
     */
    boolean mostrar(Supplier<String> accion) {
        Button btnGuardar = (Button) dialogo.getDialogPane().lookupButton(guardar);
        btnGuardar.addEventFilter(ActionEvent.ACTION, e -> {
            String error = accion.get();
            if (error != null) {
                lblError.setText(error);
                e.consume();
            }
        });
        // Solo los controles editables entran en la cadena de Enter (las etiquetas informativas no).
        List<Node> editables = campos.stream().filter(n -> !(n instanceof Label)).toList();
        if (!editables.isEmpty()) {
            Teclado.cadenaEnter(btnGuardar::fire, editables.toArray(new Node[0]));
            dialogo.setOnShown(e -> editables.get(0).requestFocus());
        }
        Optional<ButtonType> r = dialogo.showAndWait();
        return r.isPresent() && r.get() == guardar;
    }
}
