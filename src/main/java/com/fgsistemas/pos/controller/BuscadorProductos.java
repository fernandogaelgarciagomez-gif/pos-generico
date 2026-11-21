package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Texto;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/**
 * Buscar producto por nombre o código (F2). Filtra al escribir; ↓ baja a la tabla; Enter elige; Esc cierra.
 * Se reutiliza en Entrada de mercancía y (Fase 5) en Vender.
 */
final class BuscadorProductos {

    private BuscadorProductos() {
    }

    static Optional<Producto> buscar(List<Producto> productos, String textoInicial, boolean mostrarStock) {
        Dialog<Producto> d = new Dialog<>();
        d.setTitle("Buscar producto");
        ButtonType elegir = new ButtonType("Elegir (Enter)", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(elegir,
                new ButtonType("Cancelar (Esc)", ButtonBar.ButtonData.CANCEL_CLOSE));

        TextField txt = new TextField(textoInicial == null ? "" : textoInicial);
        txt.setPromptText("Escribe parte del nombre o del código…");
        FilteredList<Producto> filtrados = new FilteredList<>(FXCollections.observableArrayList(productos));
        TableView<Producto> tabla = Tablas.tabla("Sin coincidencias");
        tabla.getColumns().add(Tablas.col("Código", 150, Producto::getCodigo));
        tabla.getColumns().add(Tablas.col("Producto", 380, Producto::getNombre));
        tabla.getColumns().add(Tablas.colDerecha("Precio", 140,
                p -> Dinero.formatear(p.getPrecioVenta()) + " /" + p.getUnidad()));
        if (mostrarStock) {
            tabla.getColumns().add(Tablas.colDerecha("Stock", 140, Producto::stockTexto));
        }
        tabla.setItems(filtrados);

        Runnable filtrar = () -> {
            String q = txt.getText();
            filtrados.setPredicate(p -> q.isBlank() || Texto.contieneTodas(p.getCodigo() + " " + p.getNombre(), q));
            if (!filtrados.isEmpty()) {
                tabla.getSelectionModel().selectFirst();
            }
        };
        txt.textProperty().addListener((o, a, n) -> filtrar.run());
        filtrar.run();

        Button btnElegir = (Button) d.getDialogPane().lookupButton(elegir);
        btnElegir.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        txt.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DOWN && !filtrados.isEmpty()) {
                e.consume();
                tabla.requestFocus();
                if (tabla.getSelectionModel().isEmpty()) {
                    tabla.getSelectionModel().selectFirst();
                }
            } else if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                if (!btnElegir.isDisabled()) {
                    btnElegir.fire();
                }
            }
        });
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                if (!btnElegir.isDisabled()) {
                    btnElegir.fire();
                }
            } else if (e.getCode() == KeyCode.UP && tabla.getSelectionModel().getSelectedIndex() <= 0) {
                e.consume();
                txt.requestFocus();
            }
        });
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && !btnElegir.isDisabled()) {
                btnElegir.fire();
            }
        });

        Label titulo = new Label("Buscar producto");
        titulo.getStyleClass().add("subtitulo");
        Label ayuda = new Label("↓ para bajar a la lista · Enter elegir · Esc cancelar");
        ayuda.getStyleClass().add("texto-pequeno");
        VBox contenido = new VBox(10, titulo, txt, tabla, ayuda);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        contenido.setPrefSize(860, 520);
        d.getDialogPane().setContent(contenido);
        d.setResizable(true);
        d.setResultConverter(b -> b == elegir ? tabla.getSelectionModel().getSelectedItem() : null);
        Alertas.estilizar(d);
        d.setOnShown(e -> {
            txt.requestFocus();
            txt.end();
        });
        return d.showAndWait();
    }
}
