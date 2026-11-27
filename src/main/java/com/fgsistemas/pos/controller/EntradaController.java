package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.service.Avisos;
import com.fgsistemas.pos.service.EntradaService;
import com.fgsistemas.pos.service.EntradaService.CambioCosto;
import com.fgsistemas.pos.service.EntradaService.Linea;
import com.fgsistemas.pos.service.EntradaService.Resultado;
import com.fgsistemas.pos.service.InventarioService;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.util.Navegador;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.CodigoBarras;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Teclado;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import com.fgsistemas.pos.service.Validacion;
import com.fgsistemas.pos.util.Texto;

import java.util.List;
import java.util.Optional;

/**
 * Entrada de mercancía (16-entrada-mercancia.md).
 * Flujo: código + Enter → cantidad → Enter → costo → Enter → regresa al código.
 */
public class EntradaController {

    /** Renglón en pantalla (aún no guardado). */
    private static final class Renglon {
        final Producto producto;
        long cantidad;
        long costo;

        Renglon(Producto producto, long cantidad, long costo) {
            this.producto = producto;
            this.cantidad = cantidad;
            this.costo = costo;
        }

        long subtotal() {
            return Cantidad.importe(costo, cantidad);
        }
    }

    @FXML private BorderPane raiz;
    @FXML private ComboBox<String> cmbProveedor;
    @FXML private TextField txtFactura;
    @FXML private TextField txtCodigo;
    @FXML private Label lblCapturaProducto;
    @FXML private Label lblCapturaInfo;
    @FXML private Label lblCantidad;
    @FXML private TextField txtCantidad;
    @FXML private TextField txtCosto;
    @FXML private Label lblCapturaSubtotal;
    @FXML private TableView<Renglon> tabla;
    @FXML private Label lblTotal;
    @FXML private Label lblRenglones;
    @FXML private Label lblPiezas;
    @FXML private CheckBox chkPagadoCaja;
    @FXML private TextField txtNotas;
    @FXML private FlowPane barraAcciones;

    private final EntradaService entradas = new EntradaService();
    private final InventarioService inventario = new InventarioService();
    private final ObservableList<Renglon> renglones = FXCollections.observableArrayList();

    private Producto enCaptura;
    private Renglon editando;
    private List<Producto> catalogo;

    @FXML
    private void initialize() {
        configurarTabla();
        configurarCaptura();
        configurarAcciones();
        cargarProveedores();
        limpiarCaptura();
        actualizarTotales();
        Navegador.alCerrarAvisar(() -> renglones.isEmpty() && enCaptura == null ? null
                : "Hay una entrada de mercancía sin guardar con " + renglones.size() + " productos.");
        Teclado.enfocar(cmbProveedor);
    }

    // ------------------------------------------------------------------ configuración

    private void configurarTabla() {
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.getColumns().add(Tablas.col("Código", 130, r -> r.producto.getCodigo()));
        tabla.getColumns().add(Tablas.col("Producto", 260, r -> r.producto.getNombre()));
        tabla.getColumns().add(Tablas.colDerecha("Stock actual", 120, r -> r.producto.stockTexto()));
        tabla.getColumns().add(Tablas.colDerecha("Entra", 120, r -> r.producto.formatear(r.cantidad)));
        tabla.getColumns().add(Tablas.colDerecha("Costo unit.", 120, r -> Dinero.formatear(r.costo)));
        tabla.getColumns().add(Tablas.colDerecha("Subtotal", 120, r -> Dinero.formatear(r.subtotal())));
        tabla.getColumns().add(Tablas.colDerecha("Stock nuevo", 120,
                r -> r.producto.formatear(r.producto.getStock() + r.cantidad)));
        tabla.setItems(renglones);
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                editarRenglon();
            }
        });
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                editarRenglon();
            }
        });
    }

    private void configurarCaptura() {
        Teclado.cadenaEnter(txtCodigo::requestFocus, cmbProveedor, txtFactura);
        Teclado.campoDinero(txtCosto);

        txtCodigo.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case ENTER -> {
                    e.consume();
                    procesarCodigo();
                }
                case UP -> {
                    e.consume();
                    moverSeleccion(-1);
                }
                case DOWN -> {
                    e.consume();
                    moverSeleccion(1);
                }
                case DELETE -> {
                    if (txtCodigo.getText().isEmpty()) {
                        e.consume();
                        quitarRenglon();
                    }
                }
                default -> {
                }
            }
        });
        txtCantidad.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                if (leerCantidad().isPresent()) {
                    txtCosto.requestFocus();
                }
            }
        });
        txtCosto.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                agregarRenglon();
            }
        });
        txtCantidad.textProperty().addListener((o, a, n) -> actualizarSubtotalCaptura());
        txtCosto.textProperty().addListener((o, a, n) -> actualizarSubtotalCaptura());
        chkPagadoCaja.selectedProperty().addListener((o, a, n) -> actualizarTotales());
    }

    private void configurarAcciones() {
        Acciones.agregar(barraAcciones, raiz, "Buscar producto", "F2", new KeyCodeCombination(KeyCode.F2), false,
                this::buscar);
        barraAcciones.getChildren().add(Acciones.boton("Editar renglón", "Enter", this::editarRenglon));
        Acciones.agregar(barraAcciones, raiz, "Quitar renglón", "Supr", new KeyCodeCombination(KeyCode.DELETE),
                true, this::quitarRenglon);
        Button guardar = Acciones.agregar(barraAcciones, raiz, "Guardar entrada", "F10",
                new KeyCodeCombination(KeyCode.F10), false, this::guardar);
        guardar.getStyleClass().add("boton-principal");
        Acciones.agregar(barraAcciones, raiz, "Historial", "F11", new KeyCodeCombination(KeyCode.F11), false,
                HistorialEntradas::mostrar);
        Acciones.espaciador(barraAcciones);
        Acciones.regresar(barraAcciones, raiz, "Regresar al menú",
                this::escape);
    }

    private void cargarProveedores() {
        try {
            String actual = cmbProveedor.getEditor().getText();
            cmbProveedor.setItems(FXCollections.observableArrayList(entradas.proveedores()));
            cmbProveedor.getEditor().setText(actual);
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        } catch (PermisoDenegadoException e) {
            Alertas.error(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ captura

    private void procesarCodigo() {
        String codigo = CodigoBarras.limpiar(txtCodigo.getText());
        if (codigo.isEmpty()) {
            return;
        }
        try {
            Optional<Producto> p = inventario.buscarActivoPorCodigo(codigo);
            if (p.isPresent()) {
                cargarCaptura(p.get(), null);
                return;
            }
            // DECISION: igual que en Vender, el campo también acepta parte del nombre (05-decisiones.md #40).
            if (catalogo == null) {
                catalogo = inventario.listar(false);
            }
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        List<Producto> coincidencias = catalogo.stream()
                .filter(p -> Texto.contieneTodas(p.getCodigo() + " " + p.getNombre(), codigo))
                .toList();
        if (coincidencias.size() == 1) {
            cargarCaptura(coincidencias.get(0), null);
            return;
        }
        if (coincidencias.size() > 1) {
            buscar();
            return;
        }
        if (Validacion.codigoProducto(codigo) != null) { // no parece código (p. ej. lleva espacios)
            Alertas.advertencia("No hay productos que coincidan con \"" + codigo + "\".\n"
                    + "Revisa lo escrito o da de alta el producto en Inventario.");
            txtCodigo.requestFocus();
            txtCodigo.selectAll();
            return;
        }
        if (Alertas.confirmar("No existe un producto activo con el código o nombre \"" + codigo + "\".\n"
                + "¿Crear producto nuevo con ese código?")) {
            Optional<Producto> creado = ProductoDialogos.formulario(null, codigo);
            if (creado.isPresent()) {
                catalogo = null; // recargar el catálogo de búsqueda
                cargarCaptura(creado.get(), null);
                return;
            }
        }
        txtCodigo.requestFocus();
        txtCodigo.selectAll();
    }

    private void cargarCaptura(Producto p, Renglon aEditar) {
        enCaptura = p;
        editando = aEditar;
        Renglon existente = aEditar != null ? aEditar : buscarRenglon(p.getId());
        lblCapturaProducto.setText(p.getNombre());
        lblCapturaInfo.getStyleClass().remove("texto-error");
        lblCapturaInfo.setText(p.getCodigo() + " · Stock actual: " + p.stockTexto() + " · Costo actual: "
                + Dinero.formatear(p.getCosto()) + " /" + p.getUnidad()
                + (aEditar == null && existente != null ? "\nYa está en la lista: la cantidad se sumará." : ""));
        lblCantidad.setText("Cantidad (" + p.getUnidad() + ")");
        Teclado.soloNumeros(txtCantidad, p.getTipoVenta().aceptaDecimales() ? 3 : 0);
        txtCantidad.setText(aEditar != null ? Cantidad.paraCampo(aEditar.cantidad, p.getTipoVenta()) : "1");
        txtCosto.setText(Dinero.paraCampo(existente != null ? existente.costo : p.getCosto()));
        txtCantidad.setDisable(false);
        txtCosto.setDisable(false);
        txtCodigo.clear();
        actualizarSubtotalCaptura();
        txtCantidad.requestFocus();
    }

    private Optional<Long> leerCantidad() {
        if (enCaptura == null) {
            return Optional.empty();
        }
        try {
            long q = Cantidad.parsear(txtCantidad.getText(), enCaptura.getTipoVenta());
            if (q <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser mayor a cero.");
            }
            return Optional.of(q);
        } catch (IllegalArgumentException e) {
            mostrarErrorCaptura(e.getMessage());
            txtCantidad.requestFocus();
            return Optional.empty();
        }
    }

    private void agregarRenglon() {
        if (enCaptura == null) {
            return;
        }
        Optional<Long> q = leerCantidad();
        if (q.isEmpty()) {
            return;
        }
        long costo;
        try {
            costo = txtCosto.getText().isBlank() ? 0 : Dinero.parsear(txtCosto.getText());
        } catch (IllegalArgumentException e) {
            mostrarErrorCaptura(e.getMessage());
            txtCosto.requestFocus();
            return;
        }
        // Errores humanos: un código escaneado en la cantidad, "100" en vez de "10", o el costo de la caja
        // completa en vez del de una unidad. Se pide confirmar; el foco regresa al campo sospechoso.
        Renglon existente = editando != null ? null : buscarRenglon(enCaptura.getId());
        long cantidadFinal = q.get() + (existente == null ? 0 : existente.cantidad);
        String avisoCantidad = Cantidad.avisoCantidadInusual(cantidadFinal, enCaptura.getTipoVenta(),
                enCaptura.getUnidad());
        if (avisoCantidad != null && !Alertas.confirmarPeligro(enCaptura.getNombre() + "\n\n" + avisoCantidad)) {
            txtCantidad.requestFocus();
            return;
        }
        String avisoCosto = Avisos.costo(costo, enCaptura.getCosto(), enCaptura.getPrecioVenta());
        if (avisoCosto != null && !Alertas.confirmarPeligro(enCaptura.getNombre() + "\n\n" + avisoCosto)) {
            txtCosto.requestFocus();
            return;
        }
        Renglon r;
        if (editando != null) {
            r = editando;
            r.cantidad = q.get();
            r.costo = costo;
        } else {
            r = buscarRenglon(enCaptura.getId());
            if (r != null) {
                r.cantidad += q.get();
                r.costo = costo;
            } else {
                r = new Renglon(enCaptura, q.get(), costo);
                renglones.add(r);
            }
        }
        tabla.refresh();
        tabla.getSelectionModel().select(r);
        tabla.scrollTo(r);
        actualizarTotales();
        limpiarCaptura();
        txtCodigo.requestFocus();
    }

    private void limpiarCaptura() {
        enCaptura = null;
        editando = null;
        lblCapturaProducto.setText("Escribe un código para empezar");
        lblCapturaInfo.getStyleClass().remove("texto-error");
        lblCapturaInfo.setText("Enter en el código → cantidad → costo → Enter agrega a la lista.");
        lblCantidad.setText("Cantidad");
        txtCantidad.clear();
        txtCosto.clear();
        txtCantidad.setDisable(true);
        txtCosto.setDisable(true);
        lblCapturaSubtotal.setText("—");
    }

    private void mostrarErrorCaptura(String mensaje) {
        lblCapturaInfo.setText("⚠ " + mensaje);
        if (!lblCapturaInfo.getStyleClass().contains("texto-error")) {
            lblCapturaInfo.getStyleClass().add("texto-error");
        }
    }

    private void actualizarSubtotalCaptura() {
        if (enCaptura == null) {
            lblCapturaSubtotal.setText("—");
            return;
        }
        try {
            long q = Cantidad.parsear(txtCantidad.getText(), enCaptura.getTipoVenta());
            long c = txtCosto.getText().isBlank() ? 0 : Dinero.parsear(txtCosto.getText());
            lblCapturaSubtotal.setText(Dinero.formatear(Cantidad.importe(c, q)));
        } catch (IllegalArgumentException e) {
            lblCapturaSubtotal.setText("—");
        }
    }

    private Renglon buscarRenglon(long productoId) {
        return renglones.stream().filter(r -> r.producto.getId() == productoId).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ acciones

    private void moverSeleccion(int delta) {
        if (renglones.isEmpty()) {
            return;
        }
        int i = tabla.getSelectionModel().getSelectedIndex();
        int destino = i < 0 ? (delta > 0 ? 0 : renglones.size() - 1)
                : Math.max(0, Math.min(renglones.size() - 1, i + delta));
        tabla.getSelectionModel().select(destino);
        tabla.scrollTo(destino);
    }

    private void editarRenglon() {
        Renglon r = tabla.getSelectionModel().getSelectedItem();
        if (r == null) {
            Alertas.advertencia("Selecciona un renglón de la lista (↑ ↓ desde el campo de código).");
            return;
        }
        cargarCaptura(r.producto, r);
    }

    private void quitarRenglon() {
        Renglon r = tabla.getSelectionModel().getSelectedItem();
        if (r == null) {
            return;
        }
        if (editando == r) {
            limpiarCaptura();
        }
        renglones.remove(r);
        actualizarTotales();
        txtCodigo.requestFocus();
    }

    private void buscar() {
        try {
            if (catalogo == null) {
                catalogo = inventario.listar(false);
            }
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        BuscadorProductos.buscar(catalogo, txtCodigo.getText(), true)
                .ifPresentOrElse(p -> cargarCaptura(p, null), txtCodigo::requestFocus);
    }

    private void actualizarTotales() {
        long total = renglones.stream().mapToLong(Renglon::subtotal).sum();
        lblTotal.setText(Dinero.formatear(total));
        lblRenglones.setText(renglones.size() == 1 ? "1 producto" : renglones.size() + " productos");
        long piezas = renglones.stream().filter(r -> !r.producto.getTipoVenta().aceptaDecimales())
                .mapToLong(r -> r.cantidad / Cantidad.UNO).sum();
        long granel = renglones.stream().filter(r -> r.producto.getTipoVenta().aceptaDecimales()).count();
        lblPiezas.setText(piezas + " piezas" + (granel > 0 ? " + " + granel + " a granel" : ""));
    }

    private void guardar() {
        if (enCaptura != null) {
            Alertas.advertencia("Termina de capturar \"" + enCaptura.getNombre()
                    + "\" (Enter en el costo) o cancélalo con Esc.");
            txtCantidad.requestFocus();
            return;
        }
        if (renglones.isEmpty()) {
            Alertas.advertencia("Agrega al menos un producto a la entrada.");
            txtCodigo.requestFocus();
            return;
        }
        String proveedor = cmbProveedor.getEditor().getText();
        // Errores humanos: capturar dos veces la misma factura.
        try {
            Optional<String> anterior = entradas.facturaYaRegistrada(proveedor, txtFactura.getText());
            if (anterior.isPresent() && !Alertas.confirmarPeligro("La factura \"" + txtFactura.getText().trim()
                    + "\" ya se registró:\n" + anterior.get()
                    + "\n\nSi la guardas otra vez, el stock se sumará dos veces.\n\n¿Guardarla de todos modos?")) {
                txtFactura.requestFocus();
                return;
            }
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        long total = renglones.stream().mapToLong(Renglon::subtotal).sum();
        String resumen = "Guardar entrada de mercancía:\n\n"
                + "  Proveedor: " + (proveedor.isBlank() ? "(sin proveedor)" : proveedor.trim()) + "\n"
                + "  Productos: " + renglones.size() + "\n"
                + "  " + lblPiezas.getText() + "\n"
                + "  Total a costo: " + Dinero.formatear(total) + "\n"
                + (chkPagadoCaja.isSelected() ? "  Se registrará un RETIRO de caja por " + Dinero.formatear(total) + "\n" : "")
                + "\n¿Continuar?";
        if (!Alertas.confirmar(resumen)) {
            txtCodigo.requestFocus();
            return;
        }
        List<Linea> lineas = renglones.stream().map(r -> new Linea(r.producto.getId(), r.cantidad, r.costo)).toList();
        Resultado res;
        try {
            res = entradas.guardar(proveedor, txtFactura.getText(), txtNotas.getText(), lineas,
                    chkPagadoCaja.isSelected());
        } catch (IllegalArgumentException | IllegalStateException | PermisoDenegadoException e) {
            Alertas.advertencia(e.getMessage());
            return;
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        for (CambioCosto cambio : res.cambiosDeCosto()) {
            ofrecerNuevoPrecio(cambio);
        }
        Alertas.toast("Entrada #" + res.entradaId() + " guardada · " + Dinero.formatear(res.totalCosto()));
        reiniciar();
    }

    /** Si el costo cambió, pregunta si actualizar también el precio (mostrando margen actual vs nuevo). */
    private void ofrecerNuevoPrecio(CambioCosto c) {
        Producto p = c.producto();
        long precio = p.getPrecioVenta();
        long sugerido = c.costoAnterior() > 0
                ? BigDecimal.valueOf(precio).multiply(BigDecimal.valueOf(c.costoNuevo()))
                .divide(BigDecimal.valueOf(c.costoAnterior()), 0, RoundingMode.HALF_UP).longValueExact()
                : precio;
        TextField txtPrecio = new TextField(Dinero.paraCampo(sugerido));
        Teclado.campoDinero(txtPrecio);
        Label lblMargen = new Label();
        lblMargen.getStyleClass().add("texto-secundario");
        Runnable margen = () -> {
            try {
                long nuevo = Dinero.parsear(txtPrecio.getText());
                lblMargen.setText("Margen con este precio: " + margen(nuevo, c.costoNuevo()));
            } catch (IllegalArgumentException e) {
                lblMargen.setText("");
            }
        };
        txtPrecio.textProperty().addListener((o, a, n) -> margen.run());
        margen.run();

        new Formulario("¿Actualizar el precio de venta?", "Actualizar precio (Enter)")
                .campo("Producto", new Label(p.getNombre()))
                .campo("Costo", new Label(Dinero.formatear(c.costoAnterior()) + " → " + Dinero.formatear(c.costoNuevo())
                        + " /" + p.getUnidad()))
                .campo("Precio actual", new Label(Dinero.formatear(precio) + "   (margen antes: "
                        + margen(precio, c.costoAnterior()) + ", ahora: " + margen(precio, c.costoNuevo()) + ")"))
                .campo("Precio nuevo", txtPrecio)
                .campo("", lblMargen)
                .nota("Sugerido: el precio que mantiene el mismo margen. Esc deja el precio como está.")
                .mostrar(() -> {
                    try {
                        long nuevoPrecio = Dinero.parsear(txtPrecio.getText());
                        String aviso = Avisos.precio(nuevoPrecio, precio, c.costoNuevo());
                        if (aviso != null && !Alertas.confirmarPeligro(p.getNombre() + "\n\n" + aviso)) {
                            return "Revisa el precio.";
                        }
                        inventario.cambiarPrecio(p.getId(), nuevoPrecio);
                        return null;
                    } catch (IllegalArgumentException | PermisoDenegadoException e) {
                        return e.getMessage();
                    } catch (SQLException e) {
                        Flujo.errorDatos(e);
                        return "No se pudo guardar.";
                    }
                });
    }

    private static String margen(long precio, long costo) {
        if (precio <= 0) {
            return "—";
        }
        BigDecimal pct = BigDecimal.valueOf(precio - costo).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(precio), 1, RoundingMode.HALF_UP);
        return pct.toPlainString() + "%";
    }

    private void reiniciar() {
        renglones.clear();
        limpiarCaptura();
        cmbProveedor.getEditor().clear();
        txtFactura.clear();
        txtNotas.clear();
        chkPagadoCaja.setSelected(false);
        catalogo = null;
        cargarProveedores();
        actualizarTotales();
        cmbProveedor.requestFocus();
    }

    private void escape() {
        if (enCaptura != null) {
            limpiarCaptura();
            txtCodigo.requestFocus();
        } else if (renglones.isEmpty() || Alertas.confirmar("Hay " + renglones.size()
                + " productos sin guardar. ¿Descartar esta entrada y regresar al menú?")) {
            Flujo.irAlMenu();
        } else {
            txtCodigo.requestFocus();
        }
    }
}
