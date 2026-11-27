package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.App;
import com.fgsistemas.pos.model.MetodoPago;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.ResumenCaja;
import com.fgsistemas.pos.model.Turno;
import com.fgsistemas.pos.service.CorteService;
import com.fgsistemas.pos.service.ExportService;
import com.fgsistemas.pos.service.ExportService.Cant;
import com.fgsistemas.pos.service.ExportService.Hoja;
import com.fgsistemas.pos.service.ExportService.Monto;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Sesion;
import com.fgsistemas.pos.util.Teclado;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Resumen del día sin cerrar el turno (17-resumen-del-dia.md). Cajero: su turno, sin ganancia. */
public class ResumenController {

    private static final Logger LOG = Logger.getLogger(ResumenController.class.getName());
    private static final List<MetodoPago> METODOS = List.of(MetodoPago.EFECTIVO, MetodoPago.TARJETA,
            MetodoPago.TRANSFERENCIA, MetodoPago.FIADO);

    @FXML private BorderPane raiz;
    @FXML private ScrollPane scroll;
    @FXML private VBox contenido;
    @FXML private HBox cajaModo;
    @FXML private Label lblPeriodo;
    @FXML private FlowPane barraAcciones;

    private final CorteService servicio = new CorteService();
    private boolean puedeVerDia;
    private boolean todoElDia;
    private ToggleButton btnTurno;
    private ToggleButton btnDia;
    private ResumenCaja actual;

    /** Lo que se mostró la última vez, para no reconstruir el encabezado. */
    private int fijos;

    @FXML
    private void initialize() {
        puedeVerDia = Sesion.tiene(Permiso.VER_CORTES_PASADOS);
        fijos = contenido.getChildren().size();
        if (puedeVerDia) {
            ToggleGroup grupo = new ToggleGroup();
            btnTurno = botonModo("Turno actual", "T", grupo);
            btnDia = botonModo("Todo el día", "D", grupo);
            btnTurno.setSelected(true);
            grupo.selectedToggleProperty().addListener((o, antes, ahora) -> {
                if (ahora == null) {
                    antes.setSelected(true);
                    return;
                }
                todoElDia = ahora == btnDia;
                cargar();
            });
            cajaModo.getChildren().addAll(btnTurno, btnDia);
            Acciones.atajo(raiz, new KeyCodeCombination(KeyCode.T), true, () -> btnTurno.setSelected(true));
            Acciones.atajo(raiz, new KeyCodeCombination(KeyCode.D), true, () -> btnDia.setSelected(true));
        }
        Acciones.agregar(barraAcciones, raiz, "Actualizar", "F5", new KeyCodeCombination(KeyCode.F5), false,
                this::cargar);
        Acciones.agregar(barraAcciones, raiz, "Exportar a Excel", "F7", new KeyCodeCombination(KeyCode.F7), false,
                this::exportar);
        Acciones.espaciador(barraAcciones);
        Acciones.regresar(barraAcciones, raiz, "Regresar al menú",
                Flujo::irAlMenu);
        cargar();
        Teclado.enfocar(scroll);
    }

    private static ToggleButton botonModo(String texto, String tecla, ToggleGroup grupo) {
        ToggleButton t = new ToggleButton(texto);
        t.getStyleClass().setAll("chip");
        Label chip = new Label(tecla);
        chip.getStyleClass().add("tecla-chip");
        t.setGraphic(chip);
        t.setToggleGroup(grupo);
        t.setFocusTraversable(false);
        return t;
    }

    private void cargar() {
        try {
            LocalDate hoy = Fechas.ahora().toLocalDate();
            if (todoElDia) {
                actual = servicio.resumenDia(hoy);
                lblPeriodo.setText("Todo el día · " + Fechas.FECHA_CORTA.format(hoy) + " (todos los turnos de hoy)");
            } else {
                actual = servicio.resumenTurnoActual();
                Turno t = Sesion.turno();
                lblPeriodo.setText("Turno de " + t.getUsuarioNombre() + " · abierto "
                        + Fechas.FECHA_HORA.format(t.getAbiertoEn()) + " · actualizado "
                        + Fechas.HORA.format(Fechas.ahora()));
            }
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        } catch (IllegalStateException | PermisoDenegadoException e) {
            Alertas.advertencia(e.getMessage());
            return;
        }
        contenido.getChildren().remove(fijos, contenido.getChildren().size());
        contenido.getChildren().addAll(tarjetas(actual), detalle(actual));
    }

    // ------------------------------------------------------------------ armado

    private Node tarjetas(ResumenCaja r) {
        FlowPane fila = new FlowPane(16, 16);
        fila.getChildren().add(tarjeta("Total vendido", Dinero.formatear(r.totalVendido()),
                r.numCanceladas() == 0 ? "Sin cancelaciones"
                        : r.numCanceladas() + (r.numCanceladas() == 1 ? " cancelada (" : " canceladas (")
                        + Dinero.formatear(r.montoCanceladas()) + ") no suman"));
        fila.getChildren().add(tarjeta("Número de ventas", String.valueOf(r.numVentas()), "completadas"));
        fila.getChildren().add(tarjeta("Ticket promedio", Dinero.formatear(r.ticketPromedio()), "por venta"));
        if (r.ganancia() != null) {
            fila.getChildren().add(tarjeta("Ganancia estimada", Dinero.formatear(r.ganancia()),
                    "precio − costo − descuentos"));
        }
        return fila;
    }

    private static VBox tarjeta(String titulo, String valor, String detalle) {
        Label t = new Label(titulo);
        t.getStyleClass().add("contador-titulo");
        Label v = new Label(valor);
        v.getStyleClass().add("contador-monto");
        Label d = new Label(detalle);
        d.getStyleClass().add("contador-detalle");
        VBox caja = new VBox(t, v, d);
        caja.getStyleClass().add("tarjeta-dato");
        return caja;
    }

    private Node detalle(ResumenCaja r) {
        // Columna izquierda: métodos, abonos y caja
        TableView<MetodoPago> tMetodos = Tablas.tabla("");
        tMetodos.getColumns().add(Tablas.col("Método", 170, MetodoPago::etiqueta));
        tMetodos.getColumns().add(Tablas.colDerecha("Monto", 140, m -> Dinero.formatear(r.ventas(m))));
        tMetodos.getColumns().add(Tablas.colDerecha("# ventas", 100, m -> String.valueOf(r.numPorMetodo(m))));
        tMetodos.setItems(FXCollections.observableArrayList(METODOS));
        tablaFija(tMetodos, METODOS.size());

        GridPane abonos = grid();
        int f = 0;
        for (MetodoPago m : List.of(MetodoPago.EFECTIVO, MetodoPago.TARJETA, MetodoPago.TRANSFERENCIA)) {
            renglon(abonos, f++, m.etiqueta(), r.abonos(m), false);
        }
        renglon(abonos, f, "Total abonos", r.totalAbonos(), true);

        GridPane cajaGrid = grid();
        f = 0;
        renglon(cajaGrid, f++, todoElDia ? "Fondos de los turnos" : "Fondo inicial", r.fondo(), false);
        renglon(cajaGrid, f++, "+ Ventas en efectivo", r.ventas(MetodoPago.EFECTIVO), false);
        renglon(cajaGrid, f++, "+ Abonos en efectivo", r.abonos(MetodoPago.EFECTIVO), false);
        renglon(cajaGrid, f++, "+ Entradas de caja", r.entradas(), false);
        renglon(cajaGrid, f++, "− Retiros de caja", r.retiros(), false);
        VBox caja = seccion(todoElDia ? "Caja (todos los turnos de hoy)" : "Caja", cajaGrid);
        if (todoElDia) {
            // DECISION: con varios turnos el efectivo ya se retiró en cada corte; "debería haber ahora" solo
            // tiene sentido para el turno actual.
            Label nota = new Label("El efectivo que debería haber en caja se ve en \"Turno actual\".");
            nota.getStyleClass().add("texto-pequeno");
            nota.setWrapText(true);
            caja.getChildren().add(nota);
        } else {
            renglon(cajaGrid, f, "= Efectivo que debería haber ahora", r.efectivoEsperado(), true);
        }

        VBox izquierda = new VBox(16, seccion("Ventas por método de pago", tMetodos),
                seccion("Abonos de fiados recibidos", abonos), caja);
        izquierda.setMinWidth(440);
        izquierda.setPrefWidth(460);

        // Columna derecha: top 10 y ventas por hora
        TableView<ResumenCaja.ProductoVendido> tTop = Tablas.tabla("Sin ventas todavía");
        tTop.getColumns().add(Tablas.colDerecha("#", 40, p -> String.valueOf(r.top().indexOf(p) + 1)));
        tTop.getColumns().add(Tablas.col("Producto", 280, ResumenCaja.ProductoVendido::nombre));
        tTop.getColumns().add(Tablas.colDerecha("Cantidad", 130,
                p -> Cantidad.formatear(p.cantidad(), p.tipoVenta(), p.unidad())));
        tTop.getColumns().add(Tablas.colDerecha("Importe", 130, p -> Dinero.formatear(p.importe())));
        tTop.setItems(FXCollections.observableArrayList(r.top()));
        tablaFija(tTop, Math.max(3, r.top().size()));

        VBox derecha = new VBox(16, seccion("Top 10 productos (por importe)", tTop),
                seccion("Ventas por hora", grafica(r.porHora())));
        HBox.setHgrow(derecha, Priority.ALWAYS);

        FlowPane columnas = new FlowPane(16, 16, izquierda, derecha);
        derecha.setPrefWidth(620);
        derecha.setMinWidth(520);
        return columnas;
    }

    private static Node grafica(long[] porHora) {
        int desde = 23;
        int hasta = 0;
        for (int h = 0; h < 24; h++) {
            if (porHora[h] > 0) {
                desde = Math.min(desde, h);
                hasta = Math.max(hasta, h);
            }
        }
        if (desde > hasta) { // sin ventas: horario típico
            desde = 8;
            hasta = 20;
        }
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel("$");
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(260);
        chart.setMinHeight(220);
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (int h = desde; h <= hasta; h++) {
            serie.getData().add(new XYChart.Data<>(String.format("%02d", h), porHora[h] / 100.0));
        }
        chart.getData().add(serie);
        return chart;
    }

    private static GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(24);
        g.setVgap(4);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHalignment(HPos.RIGHT);
        g.getColumnConstraints().addAll(c1, c2);
        return g;
    }

    private static void renglon(GridPane g, int fila, String concepto, long monto, boolean total) {
        Label a = new Label(concepto);
        Label b = new Label(Dinero.formatear(monto));
        if (total) {
            a.setStyle("-fx-font-weight: bold;");
            b.setStyle("-fx-font-weight: bold;");
        }
        g.add(a, 0, fila);
        g.add(b, 1, fila);
    }

    private static VBox seccion(String titulo, Node cuerpo) {
        Label t = new Label(titulo);
        t.getStyleClass().add("subtitulo");
        VBox v = new VBox(8, t, cuerpo);
        v.getStyleClass().add("panel-seccion");
        return v;
    }

    /** Tabla de alto fijo (sin scroll propio) para que la pantalla se desplace como un todo. */
    private static void tablaFija(TableView<?> t, int filas) {
        double alto = 38 + filas * 36;
        t.setPrefHeight(alto);
        t.setMinHeight(alto);
        t.setMaxHeight(alto);
        t.setFocusTraversable(false);
    }

    // ------------------------------------------------------------------ exportar

    private void exportar() {
        if (actual == null) {
            return;
        }
        ResumenCaja r = actual;
        List<List<Object>> resumen = new ArrayList<>();
        resumen.add(List.of(lblPeriodo.getText(), ""));
        resumen.add(fila("Total vendido", r.totalVendido()));
        resumen.add(List.of("Número de ventas", r.numVentas()));
        resumen.add(fila("Ticket promedio", r.ticketPromedio()));
        if (r.ganancia() != null) {
            resumen.add(fila("Ganancia estimada", r.ganancia()));
        }
        for (MetodoPago m : METODOS) {
            resumen.add(fila("Ventas " + m.etiqueta().toLowerCase(), r.ventas(m)));
        }
        for (MetodoPago m : List.of(MetodoPago.EFECTIVO, MetodoPago.TARJETA, MetodoPago.TRANSFERENCIA)) {
            resumen.add(fila("Abonos " + m.etiqueta().toLowerCase(), r.abonos(m)));
        }
        resumen.add(fila(todoElDia ? "Fondos de los turnos" : "Fondo inicial", r.fondo()));
        resumen.add(fila("Entradas de caja", r.entradas()));
        resumen.add(fila("Retiros de caja", r.retiros()));
        if (!todoElDia) {
            resumen.add(fila("Efectivo que debería haber", r.efectivoEsperado()));
        }
        resumen.add(List.of("Ventas canceladas", r.numCanceladas()));
        resumen.add(fila("Monto cancelado", r.montoCanceladas()));

        List<List<Object>> top = new ArrayList<>();
        for (ResumenCaja.ProductoVendido p : r.top()) {
            top.add(List.of(p.codigo(), p.nombre(), p.unidad(), new Cant(p.cantidad()), new Monto(p.importe())));
        }
        List<List<Object>> horas = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            if (r.porHora()[h] > 0) {
                horas.add(List.of(String.format("%02d:00", h), new Monto(r.porHora()[h])));
            }
        }
        try {
            Path archivo = new ExportService().excel(todoElDia ? "resumen_dia" : "resumen_turno", List.of(
                    new Hoja("Resumen", List.of("Concepto", "Valor"), resumen),
                    new Hoja("Top productos", List.of("Código", "Producto", "Unidad", "Cantidad", "Importe"), top),
                    new Hoja("Por hora", List.of("Hora", "Vendido"), horas)));
            if (Alertas.confirmar("Archivo guardado en:\n" + archivo + "\n\n¿Abrirlo ahora?")) {
                App.abrirArchivo(archivo);
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "No se pudo exportar el resumen", e);
            Alertas.error("No se pudo guardar el archivo de Excel.\n\n" + e.getMessage()
                    + "\n\nSi el archivo está abierto en Excel, ciérralo e intenta de nuevo.");
        }
    }

    private static List<Object> fila(String concepto, long centavos) {
        return List.of(concepto, new Monto(centavos));
    }
}
