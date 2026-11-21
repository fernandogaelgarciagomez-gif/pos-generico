package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.model.EntradaDetalle;
import com.fgsistemas.pos.model.EntradaMercancia;
import com.fgsistemas.pos.service.EntradaService;
import com.fgsistemas.pos.service.ExportService.Cant;
import com.fgsistemas.pos.service.ExportService.Monto;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Historial de entradas (F11) con filtro por fechas y proveedor/factura; Enter ver detalle. */
final class HistorialEntradas {

    private HistorialEntradas() {
    }

    static void mostrar() {
        EntradaService servicio = new EntradaService();
        TextField txtTexto = new TextField();
        txtTexto.setPromptText("Proveedor o factura");
        HBox.setHgrow(txtTexto, Priority.ALWAYS);
        DatePicker desde = new DatePicker(Fechas.ahora().toLocalDate().minusDays(30));
        DatePicker hasta = new DatePicker(Fechas.ahora().toLocalDate());
        Label lblPie = new Label();
        lblPie.getStyleClass().add("pie-tabla");

        TableView<EntradaMercancia> tabla = Tablas.tabla("Sin entradas en ese periodo");
        tabla.getColumns().add(Tablas.colDerecha("#", 60, e -> String.valueOf(e.id())));
        tabla.getColumns().add(Tablas.col("Fecha", 150, e -> Fechas.FECHA_HORA.format(e.fecha())));
        tabla.getColumns().add(Tablas.col("Proveedor", 220, e -> e.proveedor() == null ? "" : e.proveedor()));
        tabla.getColumns().add(Tablas.col("Factura", 120, e -> e.factura() == null ? "" : e.factura()));
        tabla.getColumns().add(Tablas.colDerecha("Productos", 100, e -> String.valueOf(e.renglones())));
        tabla.getColumns().add(Tablas.colDerecha("Total", 130, e -> Dinero.formatear(e.totalCosto())));
        tabla.getColumns().add(Tablas.col("Usuario", 150, EntradaMercancia::usuarioNombre));

        Runnable cargar = () -> {
            LocalDate d = desde.getValue() == null ? LocalDate.of(2000, 1, 1) : desde.getValue();
            LocalDate h = hasta.getValue() == null ? Fechas.ahora().toLocalDate() : hasta.getValue();
            try {
                List<EntradaMercancia> lista = servicio.historial(d, h, txtTexto.getText());
                tabla.setItems(FXCollections.observableArrayList(lista));
                long total = lista.stream().mapToLong(EntradaMercancia::totalCosto).sum();
                lblPie.setText(lista.size() + " entradas · Total " + Dinero.formatear(total));
                if (!lista.isEmpty()) {
                    tabla.getSelectionModel().selectFirst();
                }
            } catch (SQLException e) {
                Flujo.errorDatos(e);
            }
        };
        txtTexto.textProperty().addListener((o, a, n) -> cargar.run());
        desde.valueProperty().addListener((o, a, n) -> cargar.run());
        hasta.valueProperty().addListener((o, a, n) -> cargar.run());
        cargar.run();

        Runnable verDetalle = () -> {
            EntradaMercancia sel = tabla.getSelectionModel().getSelectedItem();
            if (sel != null) {
                detalle(servicio, sel);
                tabla.requestFocus();
            }
        };
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                verDetalle.run();
            }
        });
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                verDetalle.run();
            }
        });

        HBox filtros = new HBox(10, new Label("Desde"), desde, new Label("Hasta"), hasta, txtTexto);
        filtros.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Button btnDetalle = Acciones.boton("Ver detalle", "Enter", verDetalle);
        Tablas.dialogo("Historial de entradas", filtros, tabla, lblPie, 1060, 560, List.of(btnDetalle));
    }

    private static void detalle(EntradaService servicio, EntradaMercancia e) {
        List<EntradaDetalle> renglones;
        try {
            renglones = servicio.detalle(e.id());
        } catch (SQLException ex) {
            Flujo.errorDatos(ex);
            return;
        }
        TableView<EntradaDetalle> tabla = Tablas.tabla("Sin productos");
        tabla.getColumns().add(Tablas.col("Código", 130, EntradaDetalle::codigo));
        tabla.getColumns().add(Tablas.col("Producto", 300, EntradaDetalle::nombre));
        tabla.getColumns().add(Tablas.colDerecha("Cantidad", 130,
                d -> Cantidad.formatear(d.cantidad(), d.tipoVenta(), d.unidad())));
        tabla.getColumns().add(Tablas.colDerecha("Costo unit.", 130, d -> Dinero.formatear(d.costoUnitario())));
        tabla.getColumns().add(Tablas.colDerecha("Subtotal", 130, d -> Dinero.formatear(d.subtotal())));
        tabla.setItems(FXCollections.observableArrayList(renglones));

        Label encabezado = new Label(Fechas.FECHA_HORA.format(e.fecha()) + " · "
                + (e.proveedor() == null ? "Sin proveedor" : e.proveedor())
                + (e.factura() == null ? "" : " · Factura " + e.factura()) + " · " + e.usuarioNombre()
                + (e.notas() == null ? "" : "\nNotas: " + e.notas()));
        encabezado.getStyleClass().add("texto-secundario");
        encabezado.setWrapText(true);
        Label pie = new Label("Total: " + Dinero.formatear(e.totalCosto()));
        pie.getStyleClass().add("subtitulo");

        List<List<Object>> filas = new ArrayList<>();
        for (EntradaDetalle d : renglones) {
            filas.add(List.of(d.codigo(), d.nombre(), d.unidad(), new Cant(d.cantidad()),
                    new Monto(d.costoUnitario()), new Monto(d.subtotal())));
        }
        Button exportar = Acciones.boton("Exportar a Excel", null, () -> Exportar.excel("entrada_" + e.id(),
                "Entrada " + e.id(), List.of("Código", "Producto", "Unidad", "Cantidad", "Costo unitario", "Subtotal"),
                filas));
        Tablas.dialogo("Entrada #" + e.id(), encabezado, tabla, pie, 920, 500, List.of(exportar));
    }
}
