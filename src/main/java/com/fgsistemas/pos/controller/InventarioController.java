package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.service.ExportService.Cant;
import com.fgsistemas.pos.service.ExportService.Monto;
import com.fgsistemas.pos.service.InventarioService;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Sesion;
import com.fgsistemas.pos.util.Teclado;
import com.fgsistemas.pos.util.Texto;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Inventario (15-inventario.md). Admin edita; cajero solo consulta (sin costos). */
public class InventarioController {

    private static final String TODAS = "Todas las categorías";

    @FXML private BorderPane raiz;
    @FXML private TextField txtBuscar;
    @FXML private ComboBox<String> cmbCategoria;
    @FXML private CheckBox chkStockBajo;
    @FXML private CheckBox chkInactivos;
    @FXML private TableView<Producto> tabla;
    @FXML private Label lblPie;
    @FXML private FlowPane barraAcciones;

    private final InventarioService servicio = new InventarioService();
    private final ObservableList<Producto> productos = FXCollections.observableArrayList();
    private final FilteredList<Producto> filtrados = new FilteredList<>(productos);
    private boolean editor;

    @FXML
    private void initialize() {
        editor = Sesion.tiene(Permiso.EDITAR_INVENTARIO);
        configurarTabla();
        configurarFiltros();
        configurarAcciones();
        cargar(null);
        Teclado.enfocar(txtBuscar);
    }

    // ------------------------------------------------------------------ tabla

    private void configurarTabla() {
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.getColumns().add(Tablas.col("Código", 140, Producto::getCodigo));
        tabla.getColumns().add(Tablas.col("Nombre", 320, Producto::getNombre));
        tabla.getColumns().add(Tablas.col("Categoría", 160,
                p -> p.getCategoriaNombre() == null ? "" : p.getCategoriaNombre()));
        tabla.getColumns().add(Tablas.colDerecha("Precio", 140,
                p -> Dinero.formatear(p.getPrecioVenta()) + " /" + p.getUnidad()));
        if (editor) {
            tabla.getColumns().add(Tablas.colDerecha("Costo", 120, p -> Dinero.formatear(p.getCosto())));
        }
        tabla.getColumns().add(Tablas.colDerecha("Stock", 140, p -> (p.stockBajo() ? "⚠ " : "") + p.stockTexto()));
        tabla.getColumns().add(Tablas.colDerecha("Mínimo", 120, p -> p.formatear(p.getStockMinimo())));
        tabla.getColumns().add(Tablas.col("Estado", 100, p -> p.isActivo() ? "Activo" : "Inactivo"));

        SortedList<Producto> ordenados = new SortedList<>(filtrados);
        ordenados.comparatorProperty().bind(tabla.comparatorProperty());
        tabla.setItems(ordenados);

        tabla.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Producto p, boolean vacio) {
                super.updateItem(p, vacio);
                getStyleClass().removeAll("stock-bajo", "inactivo");
                if (!vacio && p != null) {
                    if (!p.isActivo()) {
                        getStyleClass().add("inactivo");
                    } else if (p.stockBajo()) {
                        getStyleClass().add("stock-bajo");
                    }
                }
            }
        });
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                editar();
            } else if (e.getCode() == KeyCode.UP && tabla.getSelectionModel().getSelectedIndex() <= 0) {
                e.consume();
                txtBuscar.requestFocus();
            }
        });
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                editar();
            }
        });
    }

    // ------------------------------------------------------------------ filtros

    private void configurarFiltros() {
        cmbCategoria.setMaxWidth(Double.MAX_VALUE);
        Teclado.comboConFlechas(cmbCategoria);
        chkInactivos.setVisible(editor);
        chkInactivos.setManaged(editor);

        txtBuscar.textProperty().addListener((o, a, n) -> aplicarFiltro());
        cmbCategoria.valueProperty().addListener((o, a, n) -> aplicarFiltro());
        chkStockBajo.selectedProperty().addListener((o, a, n) -> aplicarFiltro());
        chkInactivos.selectedProperty().addListener((o, a, n) -> aplicarFiltro());

        txtBuscar.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if ((e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.ENTER) && !filtrados.isEmpty()) {
                e.consume();
                tabla.requestFocus();
                if (tabla.getSelectionModel().isEmpty()) {
                    tabla.getSelectionModel().selectFirst();
                }
            }
        });
    }

    private void aplicarFiltro() {
        String q = txtBuscar.getText();
        String categoria = cmbCategoria.getValue();
        boolean soloBajo = chkStockBajo.isSelected();
        boolean inactivos = chkInactivos.isSelected();
        filtrados.setPredicate(p -> {
            if (!p.isActivo() && !inactivos) {
                return false;
            }
            if (soloBajo && !p.stockBajo()) {
                return false;
            }
            if (categoria != null && !TODAS.equals(categoria) && !categoria.equals(p.getCategoriaNombre())) {
                return false;
            }
            return q == null || q.isBlank() || Texto.contieneTodas(
                    p.getCodigo() + " " + p.getNombre() + " " + (p.getCategoriaNombre() == null ? "" : p.getCategoriaNombre()), q);
        });
        actualizarPie();
    }

    private void actualizarPie() {
        long activos = productos.stream().filter(Producto::isActivo).count();
        long bajos = productos.stream().filter(Producto::stockBajo).count();
        StringBuilder sb = new StringBuilder("Mostrando " + filtrados.size() + " de " + activos + " productos activos");
        if (bajos > 0) {
            sb.append("  ·  ⚠ ").append(bajos).append(" con stock bajo");
        }
        if (editor) {
            long aCosto = 0;
            long aPrecio = 0;
            for (Producto p : productos) {
                if (p.isActivo() && p.getStock() > 0) {
                    aCosto += Cantidad.importe(p.getCosto(), p.getStock());
                    aPrecio += Cantidad.importe(p.getPrecioVenta(), p.getStock());
                }
            }
            sb.append("  ·  Valor del inventario: a costo ").append(Dinero.formatear(aCosto))
                    .append(", a precio ").append(Dinero.formatear(aPrecio));
        }
        lblPie.setText(sb.toString());
    }

    // ------------------------------------------------------------------ acciones

    private void configurarAcciones() {
        Acciones.agregar(barraAcciones, raiz, "Buscar", "F2", new KeyCodeCombination(KeyCode.F2), false,
                txtBuscar::requestFocus);
        if (editor) {
            Acciones.agregar(barraAcciones, raiz, "Nuevo", "Ins/Ctrl+N", null, false, this::nuevo);
            Teclado.atajoNuevo(raiz, this::nuevo);
            barraAcciones.getChildren().add(Acciones.boton("Editar", "Enter", this::editar));
            Acciones.agregar(barraAcciones, raiz, "Ajustar stock", "Ctrl+A",
                    new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN), true, this::ajustar);
            Acciones.agregar(barraAcciones, raiz, "Movimientos (kardex)", "K", new KeyCodeCombination(KeyCode.K),
                    true, this::kardex);
            Acciones.agregar(barraAcciones, raiz, "Desactivar / Reactivar", "Supr",
                    new KeyCodeCombination(KeyCode.DELETE), true, this::cambiarActivo);
            Acciones.agregar(barraAcciones, raiz, "Lista de compras", "F8", new KeyCodeCombination(KeyCode.F8),
                    false, ProductoDialogos::listaDeCompras);
        }
        Acciones.agregar(barraAcciones, raiz, "Solo stock bajo", "F5", new KeyCodeCombination(KeyCode.F5), false,
                () -> chkStockBajo.setSelected(!chkStockBajo.isSelected()));
        Acciones.agregar(barraAcciones, raiz, "Exportar a Excel", "F7", new KeyCodeCombination(KeyCode.F7), false,
                this::exportar);
        Acciones.espaciador(barraAcciones);
        Acciones.agregar(barraAcciones, raiz, "Menú", "Esc", new KeyCodeCombination(KeyCode.ESCAPE), false,
                this::escape);
    }

    private void escape() {
        if (txtBuscar.isFocused() && !txtBuscar.getText().isEmpty()) {
            txtBuscar.clear();
        } else {
            Flujo.irAlMenu();
        }
    }

    private void cargar(Long seleccionarId) {
        try {
            String categoriaElegida = cmbCategoria.getValue();
            productos.setAll(servicio.listar(true));
            List<String> cats = new ArrayList<>();
            cats.add(TODAS);
            cats.addAll(servicio.categorias());
            cmbCategoria.setItems(FXCollections.observableArrayList(cats));
            cmbCategoria.setValue(categoriaElegida != null && cats.contains(categoriaElegida) ? categoriaElegida : TODAS);
            aplicarFiltro();
            if (seleccionarId != null) {
                for (int i = 0; i < tabla.getItems().size(); i++) {
                    if (tabla.getItems().get(i).getId() == seleccionarId) {
                        tabla.getSelectionModel().select(i);
                        tabla.scrollTo(i);
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        } catch (PermisoDenegadoException e) {
            Alertas.error(e.getMessage());
        }
    }

    private Producto seleccionado() {
        Producto p = tabla.getSelectionModel().getSelectedItem();
        if (p == null) {
            Alertas.advertencia("Primero selecciona un producto de la lista (flechas ↑ ↓).");
        }
        return p;
    }

    private void nuevo() {
        if (!editor) {
            return;
        }
        ProductoDialogos.formulario(null, null).ifPresent(p -> {
            Alertas.toast("Producto \"" + p.getNombre() + "\" agregado");
            txtBuscar.clear();
            cargar(p.getId());
        });
        tabla.requestFocus();
    }

    private void editar() {
        if (!editor) {
            return;
        }
        Producto p = seleccionado();
        if (p == null) {
            return;
        }
        ProductoDialogos.formulario(p, null).ifPresent(g -> {
            Alertas.toast("Cambios guardados");
            cargar(g.getId());
        });
        tabla.requestFocus();
    }

    private void ajustar() {
        Producto p = seleccionado();
        if (p == null) {
            return;
        }
        if (ProductoDialogos.ajustar(p)) {
            cargar(p.getId());
        }
        tabla.requestFocus();
    }

    private void kardex() {
        Producto p = seleccionado();
        if (p != null) {
            ProductoDialogos.kardex(p);
            tabla.requestFocus();
        }
    }

    private void cambiarActivo() {
        Producto p = seleccionado();
        if (p == null) {
            return;
        }
        boolean activar = !p.isActivo();
        boolean ok = activar
                ? Alertas.confirmar("¿Reactivar \"" + p.getNombre() + "\"? Volverá a aparecer en ventas.")
                : Alertas.confirmarPeligro("¿Desactivar \"" + p.getNombre() + "\"?\n"
                + "No se borra: deja de aparecer en ventas y puedes reactivarlo después.");
        if (!ok) {
            tabla.requestFocus();
            return;
        }
        try {
            servicio.cambiarActivo(p.getId(), activar);
            Alertas.toast(activar ? "Producto reactivado" : "Producto desactivado");
            if (!activar && !chkInactivos.isSelected()) {
                cargar(null);
            } else {
                cargar(p.getId());
            }
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        } catch (IllegalArgumentException | PermisoDenegadoException e) {
            Alertas.advertencia(e.getMessage());
        }
        tabla.requestFocus();
    }

    private void exportar() {
        List<String> encabezados = new ArrayList<>(List.of("Código", "Nombre", "Categoría", "Se vende por",
                "Unidad", "Precio"));
        if (editor) {
            encabezados.add("Costo");
        }
        encabezados.addAll(List.of("Stock", "Mínimo", "Estado"));
        List<List<Object>> filas = new ArrayList<>();
        for (Producto p : tabla.getItems()) {
            List<Object> fila = new ArrayList<>(List.of(p.getCodigo(), p.getNombre(),
                    p.getCategoriaNombre() == null ? "" : p.getCategoriaNombre(), p.getTipoVenta().etiqueta(),
                    p.getUnidad(), new Monto(p.getPrecioVenta())));
            if (editor) {
                fila.add(new Monto(p.getCosto()));
            }
            fila.addAll(List.of(new Cant(p.getStock()), new Cant(p.getStockMinimo()),
                    p.isActivo() ? (p.stockBajo() ? "Stock bajo" : "Activo") : "Inactivo"));
            filas.add(fila);
        }
        Exportar.excel("inventario", "Inventario", encabezados, filas);
    }
}
