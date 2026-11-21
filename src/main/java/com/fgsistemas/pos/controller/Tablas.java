package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.util.Alertas;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Function;

/** Ayudas para tablas y diálogos de solo lectura. */
final class Tablas {

    private Tablas() {
    }

    static <T> TableColumn<T, String> col(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setCellValueFactory(f -> new ReadOnlyStringWrapper(f.getValue() == null ? "" : valor.apply(f.getValue())));
        return c;
    }

    /** Columna alineada a la derecha (dinero, cantidades). */
    static <T> TableColumn<T, String> colDerecha(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> c = col(titulo, ancho, valor);
        c.getStyleClass().add("dinero");
        return c;
    }

    static <T> TableView<T> tabla(String vacio) {
        TableView<T> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(new Label(vacio));
        return t;
    }

    /**
     * Diálogo con una tabla de solo lectura, botones extra (con su atajo en el texto) y "Cerrar (Esc)".
     */
    static void dialogo(String titulo, Node encabezado, TableView<?> tabla, Node pie, double ancho, double alto,
                        List<Button> extras) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(titulo);
        d.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar (Esc)", ButtonBar.ButtonData.CANCEL_CLOSE));
        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("subtitulo");
        VBox contenido = new VBox(12, lblTitulo);
        if (encabezado != null) {
            contenido.getChildren().add(encabezado);
        }
        contenido.getChildren().add(tabla);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        if (pie != null) {
            contenido.getChildren().add(pie);
        }
        if (extras != null && !extras.isEmpty()) {
            HBox barra = new HBox(8);
            barra.getChildren().addAll(extras);
            contenido.getChildren().add(barra);
        }
        contenido.setPrefSize(ancho, alto);
        d.getDialogPane().setContent(contenido);
        d.setResizable(true);
        Alertas.estilizar(d);
        d.setOnShown(e -> {
            tabla.requestFocus();
            if (!tabla.getItems().isEmpty() && tabla.getSelectionModel().isEmpty()) {
                tabla.getSelectionModel().selectFirst();
            }
        });
        d.showAndWait();
    }
}
