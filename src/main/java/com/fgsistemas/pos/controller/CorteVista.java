package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.App;
import com.fgsistemas.pos.model.Corte;
import com.fgsistemas.pos.service.CorteExport;
import com.fgsistemas.pos.service.CorteExport.Linea;
import com.fgsistemas.pos.service.CorteService;
import com.fgsistemas.pos.service.ExportService;
import com.fgsistemas.pos.model.Venta;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Dinero;
import javafx.geometry.HPos;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * La vista del Paso 2 del corte (18-corte-de-caja.md). Se usa al hacer el corte y en Cortes pasados,
 * a partir de los mismos renglones que el PDF y el Excel ({@link CorteExport#lineas}).
 */
final class CorteVista {

    private static final Logger LOG = Logger.getLogger(CorteVista.class.getName());

    private CorteVista() {
    }

    /** Los totales en grande arriba y el desglose abajo. */
    static VBox vista(Corte k) {
        return new VBox(16, totales(k), desglose(k));
    }

    /** "Debe haber en caja" y "Total con todo y fondo", en grande (pedido del cliente). */
    static FlowPane totales(Corte k) {
        FlowPane f = new FlowPane(16, 12);
        f.getChildren().add(tarjetaGrande("Debe haber en caja (con fondo)", k.efectivoEsperado(),
                "Contado: " + Dinero.formatear(k.efectivoContado()) + " · " + CorteExport.textoDiferencia(k.diferencia())));
        f.getChildren().add(tarjetaGrande("Total con todo y fondo", k.totalConFondo(),
                "Efectivo + tarjeta + transferencia + fondo (sin fiado)"));
        return f;
    }

    private static VBox tarjetaGrande(String titulo, long monto, String detalle) {
        Label t = new Label(titulo);
        t.getStyleClass().add("contador-titulo");
        Label m = new Label(Dinero.formatear(monto));
        m.getStyleClass().addAll("contador-monto", "monto-gigante");
        Label d = new Label(detalle);
        d.getStyleClass().add("contador-detalle");
        d.setWrapText(true);
        VBox v = new VBox(2, t, m, d);
        v.getStyleClass().add("tarjeta-dato");
        v.setPrefWidth(320);
        return v;
    }

    static GridPane desglose(Corte k) {
        GridPane g = new GridPane();
        g.setHgap(40);
        g.setVgap(4);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(300);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setMinWidth(170);
        c2.setHalignment(HPos.RIGHT);
        g.getColumnConstraints().addAll(c1, c2);
        int fila = 0;
        for (Linea l : CorteExport.lineas(k)) {
            switch (l.estilo()) {
                case SECCION, NOTA -> {
                    Label t = new Label(l.concepto());
                    t.getStyleClass().add(l.estilo() == CorteExport.Estilo.SECCION ? "corte-seccion" : "texto-secundario");
                    g.add(t, 0, fila, 2, 1);
                }
                case DIFERENCIA -> {
                    Label concepto = new Label(l.concepto());
                    concepto.getStyleClass().add("corte-diferencia");
                    Label monto = new Label(Dinero.formatear(l.monto()));
                    monto.getStyleClass().add("corte-diferencia");
                    String clase = l.monto() < 0 ? "falta" : "sobra"; // verde si sobra o cuadra
                    concepto.getStyleClass().add(clase);
                    monto.getStyleClass().add(clase);
                    Label estado = new Label(CorteExport.textoDiferencia(l.monto()));
                    estado.getStyleClass().addAll("corte-diferencia", clase);
                    estado.setStyle("-fx-font-size: 15px;");
                    g.add(concepto, 0, fila);
                    g.add(monto, 1, fila);
                    fila++;
                    g.add(estado, 1, fila);
                }
                default -> {
                    Label concepto = new Label(l.concepto());
                    Label monto = new Label(Dinero.formatear(l.monto()));
                    String clase = l.estilo() == CorteExport.Estilo.TOTAL ? "corte-total" : "corte-linea";
                    HBox a = new HBox(concepto);
                    HBox b = new HBox(monto);
                    a.getStyleClass().add(clase);
                    b.getStyleClass().add(clase);
                    b.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
                    HBox.setHgrow(concepto, Priority.ALWAYS);
                    concepto.setMaxWidth(Double.MAX_VALUE);
                    monto.setMaxWidth(Double.MAX_VALUE);
                    g.add(a, 0, fila);
                    g.add(b, 1, fila);
                }
            }
            fila++;
        }
        if (k.notas() != null) {
            Label notas = new Label("Notas: " + k.notas());
            notas.setWrapText(true);
            notas.setMaxWidth(500);
            notas.setStyle("-fx-padding: 10 0 0 0;");
            g.add(notas, 0, fila, 2, 1);
        }
        return g;
    }

    static void exportarPdf(Corte k) {
        try {
            Path archivo = new CorteService().exportarPdf(k);
            if (Alertas.confirmar("Archivo guardado en:\n" + archivo + "\n\n¿Abrirlo ahora?")) {
                App.abrirArchivo(archivo);
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "No se pudo exportar el corte a PDF", e);
            Alertas.error("No se pudo guardar el PDF.\n\n" + e.getMessage()
                    + "\n\nSi el archivo está abierto, ciérralo e intenta de nuevo.");
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        }
    }

    static void exportarExcel(Corte k, List<Venta.Resumen> ventas) {
        try {
            Path archivo = new ExportService().excel(CorteExport.nombreBase(k), CorteExport.hojas(k, ventas));
            if (Alertas.confirmar("Archivo guardado en:\n" + archivo + "\n\n¿Abrirlo ahora?")) {
                App.abrirArchivo(archivo);
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "No se pudo exportar el corte a Excel", e);
            Alertas.error("No se pudo guardar el archivo de Excel.\n\n" + e.getMessage()
                    + "\n\nSi el archivo está abierto en Excel, ciérralo e intenta de nuevo.");
        }
    }
}
