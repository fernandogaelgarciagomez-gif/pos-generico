package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.model.Corte;
import com.fgsistemas.pos.model.Usuario;
import com.fgsistemas.pos.model.Venta;
import com.fgsistemas.pos.service.CorteExport;
import com.fgsistemas.pos.service.CorteService;
import com.fgsistemas.pos.service.ExportService.Monto;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.service.UsuarioService;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Teclado;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Cortes pasados (19-cortes-pasados.md). Solo admin. */
public class CortesController {

    /** Opción "Todos" del filtro de usuario. */
    private static final Usuario TODOS = new Usuario(0, "Todos los usuarios", "", null);

    @FXML private BorderPane raiz;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private ComboBox<Usuario> cmbUsuario;
    @FXML private HBox cajaRapidos;
    @FXML private TableView<Corte> tabla;
    @FXML private Label lblPie;
    @FXML private FlowPane barraAcciones;

    private final CorteService servicio = new CorteService();
    private boolean cargando;

    @FXML
    private void initialize() {
        configurarTabla();
        configurarFiltros();
        configurarAcciones();
        rango(hoy().minusDays(6), hoy()); // última semana
        Teclado.enfocar(tabla);
    }

    private static LocalDate hoy() {
        return Fechas.ahora().toLocalDate();
    }

    // ------------------------------------------------------------------ tabla

    private void configurarTabla() {
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.setPlaceholder(new Label("No hay cortes en ese periodo."));
        tabla.getColumns().add(Tablas.col("Fecha", 150, k -> Fechas.FECHA_HORA.format(k.fecha())));
        tabla.getColumns().add(Tablas.col("Usuario", 180, Corte::turnoUsuarioNombre));
        tabla.getColumns().add(Tablas.colDerecha("Fondo", 110, k -> Dinero.formatear(k.fondoInicial())));
        tabla.getColumns().add(Tablas.colDerecha("Total vendido", 140, k -> Dinero.formatear(k.totalVendido())));
        tabla.getColumns().add(Tablas.colDerecha("Efectivo esperado", 150,
                k -> Dinero.formatear(k.efectivoEsperado())));
        tabla.getColumns().add(Tablas.colDerecha("Contado", 130, k -> Dinero.formatear(k.efectivoContado())));
        tabla.getColumns().add(columnaDiferencia());
        tabla.getColumns().add(Tablas.colDerecha("# ventas", 90, k -> String.valueOf(k.numVentas())));
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                verDetalle();
            }
        });
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                verDetalle();
            }
        });
    }

    /** Diferencia: roja si faltó, verde si sobró o cuadró. */
    private static TableColumn<Corte, Corte> columnaDiferencia() {
        TableColumn<Corte, Corte> col = new TableColumn<>("Diferencia");
        col.setPrefWidth(130);
        col.getStyleClass().add("dinero");
        col.setCellValueFactory(f -> new ReadOnlyObjectWrapper<>(f.getValue()));
        col.setComparator(Comparator.comparingLong(Corte::diferencia));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Corte k, boolean vacio) {
                super.updateItem(k, vacio);
                getStyleClass().removeAll("saldo-deudor", "saldo-favor");
                if (vacio || k == null) {
                    setText(null);
                    return;
                }
                setText(Dinero.formatear(k.diferencia()));
                getStyleClass().add(k.diferencia() < 0 ? "saldo-deudor" : "saldo-favor");
            }
        });
        return col;
    }

    // ------------------------------------------------------------------ filtros

    private void configurarFiltros() {
        List<Usuario> usuarios = new ArrayList<>();
        usuarios.add(TODOS);
        try {
            usuarios.addAll(new UsuarioService().listar());
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        } catch (PermisoDenegadoException ignorada) {
            // Sin permiso de usuarios solo se filtra por fecha.
        }
        cmbUsuario.setItems(FXCollections.observableArrayList(usuarios));
        cmbUsuario.setConverter(new StringConverter<>() {
            @Override
            public String toString(Usuario u) {
                return u == null ? "" : u.getNombre();
            }

            @Override
            public Usuario fromString(String s) {
                return null;
            }
        });
        cmbUsuario.setValue(TODOS);
        Teclado.comboConFlechas(cmbUsuario);
        dpDesde.valueProperty().addListener((o, a, n) -> cargar());
        dpHasta.valueProperty().addListener((o, a, n) -> cargar());
        cmbUsuario.valueProperty().addListener((o, a, n) -> cargar());
    }

    private void rango(LocalDate desde, LocalDate hasta) {
        cargando = true;
        dpDesde.setValue(desde);
        dpHasta.setValue(hasta);
        cargando = false;
        cargar();
        tabla.requestFocus();
    }

    private void cargar() {
        if (cargando) {
            return;
        }
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();
        if (desde == null || hasta == null) {
            return;
        }
        Usuario u = cmbUsuario.getValue();
        try {
            List<Corte> lista = servicio.listar(desde, hasta, u == null || u == TODOS ? null : u.getId());
            tabla.setItems(FXCollections.observableArrayList(lista));
            if (!lista.isEmpty()) {
                tabla.getSelectionModel().selectFirst();
            }
            long vendido = lista.stream().mapToLong(Corte::totalVendido).sum();
            long esperado = lista.stream().mapToLong(Corte::efectivoEsperado).sum();
            long contado = lista.stream().mapToLong(Corte::efectivoContado).sum();
            long diferencias = lista.stream().mapToLong(Corte::diferencia).sum();
            int ventas = lista.stream().mapToInt(Corte::numVentas).sum();
            lblPie.setText(lista.size() + (lista.size() == 1 ? " corte" : " cortes") + "  ·  Vendido "
                    + Dinero.formatear(vendido) + "  ·  Esperado " + Dinero.formatear(esperado) + "  ·  Contado "
                    + Dinero.formatear(contado) + "  ·  Suma de diferencias " + Dinero.formatear(diferencias)
                    + "  ·  " + ventas + " ventas");
        } catch (IllegalArgumentException e) {
            lblPie.setText(e.getMessage());
            tabla.getItems().clear();
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        } catch (PermisoDenegadoException e) {
            Alertas.error(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ acciones

    private void configurarAcciones() {
        // Atajos rápidos de periodo (botón visible con su tecla). Solo con el foco fuera de los campos de fecha.
        cajaRapidos.getChildren().add(Acciones.boton("Hoy", "H", () -> rango(hoy(), hoy())));
        cajaRapidos.getChildren().add(Acciones.boton("Esta semana", "S",
                () -> rango(hoy().with(DayOfWeek.MONDAY), hoy())));
        cajaRapidos.getChildren().add(Acciones.boton("Este mes", "M", () -> rango(hoy().withDayOfMonth(1), hoy())));
        Acciones.atajo(raiz, new KeyCodeCombination(KeyCode.H), true, () -> rango(hoy(), hoy()));
        Acciones.atajo(raiz, new KeyCodeCombination(KeyCode.S), true, () -> rango(hoy().with(DayOfWeek.MONDAY), hoy()));
        Acciones.atajo(raiz, new KeyCodeCombination(KeyCode.M), true, () -> rango(hoy().withDayOfMonth(1), hoy()));

        barraAcciones.getChildren().add(Acciones.boton("Ver detalle", "Enter", this::verDetalle));
        Acciones.agregar(barraAcciones, raiz, "Exportar a Excel", "F7", new KeyCodeCombination(KeyCode.F7), false,
                this::exportarLista);
        Acciones.espaciador(barraAcciones);
        Acciones.regresar(barraAcciones, raiz, "Regresar al menú",
                Flujo::irAlMenu);
    }

    private void exportarLista() {
        List<List<Object>> filas = new ArrayList<>();
        for (Corte k : tabla.getItems()) {
            List<Object> f = new ArrayList<>();
            f.add(k.id());
            f.add(Fechas.FECHA_HORA.format(k.fecha()));
            f.add(k.turnoUsuarioNombre());
            f.add(new Monto(k.fondoInicial()));
            f.add(new Monto(k.totalVendido()));
            f.add(new Monto(k.efectivoEsperado()));
            f.add(new Monto(k.efectivoContado()));
            f.add(new Monto(k.diferencia()));
            f.add(k.numVentas());
            f.add(k.numCanceladas());
            f.add(k.ganancia() == null ? null : new Monto(k.ganancia()));
            f.add(k.notas() == null ? "" : k.notas());
            filas.add(f);
        }
        Exportar.excel("cortes", "Cortes", List.of("Corte", "Fecha", "Usuario", "Fondo", "Total vendido",
                "Efectivo esperado", "Contado", "Diferencia", "Ventas", "Canceladas", "Ganancia", "Notas"), filas);
    }

    // ------------------------------------------------------------------ detalle

    /** Movimiento de caja o abono, para una sola tabla en el detalle. */
    private record Movimiento(LocalDateTime fecha, String tipo, String detalle, long monto) {
    }

    private void verDetalle() {
        Corte k = tabla.getSelectionModel().getSelectedItem();
        if (k == null) {
            return;
        }
        CorteService.DetalleTurno d;
        try {
            d = servicio.detalle(k.turnoId());
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }

        // Ventas del turno
        TableView<Venta.Resumen> tVentas = Tablas.tabla("Sin ventas en el turno");
        tVentas.getColumns().add(Tablas.colDerecha("Folio", 70, v -> String.valueOf(v.folio())));
        tVentas.getColumns().add(Tablas.col("Hora", 70, v -> Fechas.HORA.format(v.fecha())));
        tVentas.getColumns().add(Tablas.col("Cliente", 140, v -> v.clienteNombre() == null ? "" : v.clienteNombre()));
        tVentas.getColumns().add(Tablas.col("Pago", 150, Venta.Resumen::metodos));
        tVentas.getColumns().add(Tablas.colDerecha("Total", 110, v -> Dinero.formatear(v.total())));
        tVentas.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Venta.Resumen v, boolean vacio) {
                super.updateItem(v, vacio);
                getStyleClass().remove("cancelada");
                if (!vacio && v != null && v.cancelada()) {
                    getStyleClass().add("cancelada");
                }
            }
        });
        tVentas.setItems(FXCollections.observableArrayList(d.ventas()));
        Runnable verVenta = () -> {
            Venta.Resumen v = tVentas.getSelectionModel().getSelectedItem();
            if (v != null) {
                VentaDialogos.verVenta(v.id());
                tVentas.requestFocus();
            }
        };
        tVentas.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                verVenta.run();
            }
        });
        tVentas.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                verVenta.run();
            }
        });
        VBox.setVgrow(tVentas, Priority.ALWAYS);

        // Movimientos de caja y abonos
        List<Movimiento> movs = new ArrayList<>();
        d.movimientos().forEach(m -> movs.add(new Movimiento(m.fecha(),
                m.tipo() == com.fgsistemas.pos.dao.MovimientoCajaDao.Tipo.ENTRADA ? "Entrada de caja" : "Retiro de caja",
                m.concepto() + " · " + m.usuarioNombre(),
                m.tipo() == com.fgsistemas.pos.dao.MovimientoCajaDao.Tipo.ENTRADA ? m.monto() : -m.monto())));
        d.abonos().forEach(a -> movs.add(new Movimiento(a.fecha(), "Abono (" + a.metodo().etiqueta().toLowerCase() + ")",
                a.clienteNombre(), a.monto())));
        movs.sort(Comparator.comparing(Movimiento::fecha));
        TableView<Movimiento> tMovs = Tablas.tabla("Sin movimientos de caja ni abonos");
        tMovs.getColumns().add(Tablas.col("Hora", 70, m -> Fechas.HORA.format(m.fecha())));
        tMovs.getColumns().add(Tablas.col("Tipo", 160, Movimiento::tipo));
        tMovs.getColumns().add(Tablas.col("Detalle", 200, Movimiento::detalle));
        tMovs.getColumns().add(Tablas.colDerecha("Monto", 110, m -> Dinero.formatear(m.monto())));
        tMovs.setItems(FXCollections.observableArrayList(movs));
        tMovs.setPrefHeight(180);
        tMovs.setFocusTraversable(false);

        Label lVentas = new Label("Ventas del turno (Enter ver productos y pagos)");
        lVentas.getStyleClass().add("subtitulo");
        Label lMovs = new Label("Movimientos de caja y abonos");
        lMovs.getStyleClass().add("subtitulo");
        VBox derecha = new VBox(8, lVentas, tVentas, lMovs, tMovs);
        HBox.setHgrow(derecha, Priority.ALWAYS);
        derecha.setMinWidth(560);

        Label enc = new Label(CorteExport.encabezado(k));
        enc.getStyleClass().add("texto-secundario");
        enc.setWrapText(true);
        enc.setMaxWidth(500);
        VBox izquierda = new VBox(12, enc, CorteVista.vista(k));
        ScrollPane sIzq = new ScrollPane(izquierda);
        sIzq.setFitToWidth(true);
        sIzq.setMinWidth(540);
        sIzq.setFocusTraversable(false);

        Runnable excel = () -> CorteVista.exportarExcel(k, d.ventas());
        Runnable pdf = () -> CorteVista.exportarPdf(k);
        HBox botones = new HBox(8, Acciones.boton("Ver productos de la venta", "Enter", verVenta),
                Acciones.boton("Exportar a Excel", "F7", excel), Acciones.boton("Exportar a PDF", "Shift+F7", pdf));

        Label titulo = new Label("Corte #" + k.id());
        titulo.getStyleClass().add("subtitulo");
        HBox cuerpo = new HBox(24, sIzq, derecha);
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        VBox contenido = new VBox(12, titulo, cuerpo, botones);
        contenido.setPrefSize(1180, 640);

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Corte #" + k.id());
        dlg.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar (Esc)", ButtonBar.ButtonData.CANCEL_CLOSE));
        dlg.getDialogPane().setContent(contenido);
        dlg.setResizable(true);
        dlg.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F7 && e.isShiftDown()) {
                e.consume();
                pdf.run();
            } else if (e.getCode() == KeyCode.F7) {
                e.consume();
                excel.run();
            }
        });
        Alertas.estilizar(dlg);
        dlg.setOnShown(e -> {
            tVentas.requestFocus();
            if (!tVentas.getItems().isEmpty()) {
                tVentas.getSelectionModel().selectFirst();
            }
        });
        dlg.showAndWait();
        tabla.requestFocus();
    }
}
