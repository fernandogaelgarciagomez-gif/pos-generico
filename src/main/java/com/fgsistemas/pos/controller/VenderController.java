package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.model.Carrito;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.service.InventarioService;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.service.TicketService;
import com.fgsistemas.pos.service.VentaService;
import com.fgsistemas.pos.service.VentaService.Pago;
import com.fgsistemas.pos.service.VentaService.VentaRegistrada;
import com.fgsistemas.pos.util.Navegador;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Captura;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Iniciales;
import com.fgsistemas.pos.util.Teclado;
import com.fgsistemas.pos.util.Texto;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Vender (14-vender.md). Rápido y 100% con teclado, pero también con mosaicos para tocar/clic.
 * El foco regresa siempre al campo de código.
 */
public class VenderController {

    private static final int MAX_MOSAICOS = 60;

    @FXML private BorderPane raiz;
    @FXML private TextField txtCodigo;
    @FXML private Label lblCoincidencias;
    @FXML private Label lblAviso;
    @FXML private ScrollPane scrollChips;
    @FXML private FlowPane chips;
    @FXML private ScrollPane scrollMosaicos;
    @FXML private FlowPane mosaicos;
    @FXML private Label lblCliente;
    @FXML private TableView<Carrito.Renglon> tabla;
    @FXML private Label lblSubtotal;
    @FXML private Label lblDescuentoTitulo;
    @FXML private Label lblDescuento;
    @FXML private Label lblArticulos;
    @FXML private Label lblTotal;
    @FXML private Button btnCobrar;
    @FXML private FlowPane barraAcciones;

    private final InventarioService inventario = new InventarioService();
    private final VentaService ventas = new VentaService();
    private final Carrito carrito = new Carrito();
    private final ObservableList<Carrito.Renglon> items = FXCollections.observableArrayList();
    private final ToggleGroup grupoChips = new ToggleGroup();

    private List<Producto> catalogo = List.of();
    private List<Producto> visibles = List.of();
    private Negocio config = new Negocio();
    private String categoriaElegida;

    @FXML
    private void initialize() {
        configurarTabla();
        configurarCampo();
        configurarAcciones();
        cargarCatalogo();
        refrescar();
        Navegador.alCerrarAvisar(() -> carrito.vacio() ? null
                : "Hay una venta sin cobrar con " + carrito.getRenglones().size() + " productos.");
        Teclado.enfocar(txtCodigo);
    }

    // ------------------------------------------------------------------ configuración

    private void configurarTabla() {
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.getColumns().add(Tablas.col("Producto", 200, r -> r.getProducto().getNombre()));
        tabla.getColumns().add(Tablas.colDerecha("Cant.", 90, Carrito.Renglon::cantidadTexto));
        tabla.getColumns().add(Tablas.colDerecha("Precio", 90, r -> Dinero.formatear(r.getPrecioUnitario())));
        tabla.getColumns().add(Tablas.colDerecha("Importe", 100, r -> Dinero.formatear(r.importe())));
        tabla.setItems(items);
        // Un clic en el carrito selecciona el renglón, pero el foco vuelve al código.
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                cambiarCantidad();
            }
            txtCodigo.requestFocus();
        });
    }

    private void configurarCampo() {
        txtCodigo.textProperty().addListener((o, a, n) -> {
            lblAviso.setText("");
            filtrarMosaicos();
        });
        txtCodigo.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case ENTER -> {
                    e.consume();
                    procesar();
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
                        quitar();
                    }
                }
                default -> {
                }
            }
        });
        // + / − cambian la cantidad del renglón seleccionado SOLO si el campo está vacío.
        txtCodigo.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if (!txtCodigo.getText().isEmpty()) {
                return;
            }
            if ("+".equals(e.getCharacter())) {
                e.consume();
                sumarCantidad(1);
            } else if ("-".equals(e.getCharacter())) {
                e.consume();
                sumarCantidad(-1);
            }
        });
    }

    private void configurarAcciones() {
        Acciones.agregar(barraAcciones, raiz, "Buscar producto", "F2", new KeyCodeCombination(KeyCode.F2), false,
                () -> buscar(txtCodigo.getText()));
        Acciones.agregar(barraAcciones, raiz, "Cantidad", "Ctrl+Q",
                new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN), false, this::cambiarCantidad);
        barraAcciones.getChildren().add(Acciones.boton("+1", "+", () -> sumarCantidad(1)));
        barraAcciones.getChildren().add(Acciones.boton("−1", "−", () -> sumarCantidad(-1)));
        barraAcciones.getChildren().add(Acciones.boton("Quitar", "Supr", this::quitar));
        Acciones.agregar(barraAcciones, raiz, "Cliente", "F4", new KeyCodeCombination(KeyCode.F4), false,
                this::cliente);
        Acciones.agregar(barraAcciones, raiz, "Descuento", "F6", new KeyCodeCombination(KeyCode.F6), false,
                this::descuento);
        Acciones.agregar(barraAcciones, raiz, "Entrada / retiro de caja", "F8", new KeyCodeCombination(KeyCode.F8),
                false, this::caja);
        Button cobrar = Acciones.agregar(barraAcciones, raiz, "Cobrar", "F10", new KeyCodeCombination(KeyCode.F10),
                false, this::cobrar);
        cobrar.getStyleClass().add("boton-principal");
        Acciones.agregar(barraAcciones, raiz, "Ventas del turno / cancelar", "F11",
                new KeyCodeCombination(KeyCode.F11), false, this::ventasDelTurno);
        Acciones.agregar(barraAcciones, raiz, "Vaciar / Menú", "Esc", new KeyCodeCombination(KeyCode.ESCAPE),
                false, this::escape);
    }

    // ------------------------------------------------------------------ catálogo y mosaicos

    private void cargarCatalogo() {
        try {
            catalogo = inventario.listar(false);
            config = ventas.configuracion();
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        } catch (PermisoDenegadoException e) {
            Alertas.error(e.getMessage());
        }
        construirChips();
        filtrarMosaicos();
    }

    private void construirChips() {
        TreeSet<String> categorias = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Producto p : catalogo) {
            if (p.getCategoriaNombre() != null) {
                categorias.add(p.getCategoriaNombre());
            }
        }
        if (categoriaElegida != null && !categorias.contains(categoriaElegida)) {
            categoriaElegida = null;
        }
        chips.getChildren().clear();
        grupoChips.getToggles().clear();
        chips.getChildren().add(chip("Todas", null));
        for (String c : categorias) {
            chips.getChildren().add(chip(c, c));
        }
        scrollChips.setVisible(!categorias.isEmpty());
        scrollChips.setManaged(!categorias.isEmpty());
        scrollChips.setVvalue(0);
    }

    private ToggleButton chip(String texto, String categoria) {
        ToggleButton t = new ToggleButton(texto);
        t.getStyleClass().setAll("chip");
        t.setFocusTraversable(false);
        t.setMaxWidth(220); // nombres muy largos se cortan con "…"; el nombre completo va en el tooltip
        t.setTooltip(new javafx.scene.control.Tooltip(texto));
        t.setToggleGroup(grupoChips);
        t.setSelected(java.util.Objects.equals(categoria, categoriaElegida));
        t.setOnAction(e -> {
            t.setSelected(true); // un chip siempre queda elegido
            categoriaElegida = categoria;
            filtrarMosaicos();
            txtCodigo.requestFocus();
        });
        return t;
    }

    /** Texto con el que se filtran los mosaicos: lo que va después del último "*" (para "3*coca"). */
    private String textoBusqueda() {
        String t = txtCodigo.getText();
        int i = t.lastIndexOf('*');
        return (i >= 0 ? t.substring(i + 1) : t).trim();
    }

    private void filtrarMosaicos() {
        String q = textoBusqueda();
        visibles = catalogo.stream()
                .filter(p -> categoriaElegida == null || categoriaElegida.equals(p.getCategoriaNombre()))
                .filter(p -> q.isEmpty() || Texto.contieneTodas(p.getCodigo() + " " + p.getNombre(), q))
                .toList();
        List<Node> nodos = visibles.stream().limit(MAX_MOSAICOS).map(this::mosaico).map(n -> (Node) n).toList();
        mosaicos.getChildren().setAll(nodos);
        String texto = catalogo.isEmpty()
                ? "No hay productos activos. Agrégalos en Inventario."
                : "Productos — " + visibles.size() + " de " + catalogo.size()
                + (q.isEmpty() && categoriaElegida == null ? "" : " coinciden")
                + (visibles.size() > MAX_MOSAICOS ? " (se muestran " + MAX_MOSAICOS + "; escribe para filtrar)" : "");
        lblCoincidencias.setText(texto);
        scrollMosaicos.setVvalue(0);
    }

    private Button mosaico(Producto p) {
        Label iniciales = new Label(Iniciales.de(p.getNombre()));
        iniciales.getStyleClass().add("mosaico-iniciales");
        StackPane imagen = new StackPane(iniciales);
        imagen.getStyleClass().addAll("mosaico-imagen", "mosaico-color-" + Iniciales.color(p.getNombre()));

        Label nombre = new Label(p.getNombre());
        nombre.getStyleClass().add("mosaico-nombre");
        nombre.setWrapText(true);
        nombre.setMaxHeight(42);
        nombre.setMinHeight(42);
        nombre.setAlignment(Pos.TOP_CENTER);
        nombre.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Label precio = new Label(Dinero.formatear(p.getPrecioVenta())
                + (p.getTipoVenta().aceptaDecimales() ? " /" + p.getUnidad() : ""));
        precio.getStyleClass().add("mosaico-precio");

        Label stock = new Label(p.getStock() <= 0 ? "Agotado" : "Quedan " + p.stockTexto());
        stock.getStyleClass().add("mosaico-stock");
        if (p.getStock() <= 0) {
            stock.getStyleClass().add("agotado");
        } else if (p.stockBajo()) {
            stock.getStyleClass().add("bajo");
        }

        VBox contenido = new VBox(6, imagen, nombre, precio, stock);
        contenido.setAlignment(Pos.TOP_CENTER);
        contenido.setFillWidth(true);

        Button b = new Button();
        b.setGraphic(contenido);
        b.getStyleClass().setAll("mosaico");
        b.setFocusTraversable(false);
        b.setMaxWidth(176);
        b.setOnAction(e -> {
            agregar(p, null);
            txtCodigo.requestFocus();
        });
        return b;
    }

    // ------------------------------------------------------------------ captura

    private void procesar() {
        String texto = txtCodigo.getText().trim();
        if (texto.isEmpty()) {
            return;
        }
        Captura cap;
        try {
            cap = Captura.parsear(texto);
        } catch (IllegalArgumentException e) {
            aviso(e.getMessage());
            return;
        }
        Optional<Producto> exacto = catalogo.stream()
                .filter(p -> p.getCodigo().equalsIgnoreCase(cap.codigo())).findFirst();
        if (exacto.isPresent()) {
            agregar(exacto.get(), cap);
            return;
        }
        if (visibles.size() == 1) {
            agregar(visibles.get(0), cap);
            return;
        }
        aviso("Producto no encontrado: \"" + cap.codigo() + "\"");
        Optional<Producto> elegido = BuscadorProductos.buscar(catalogo, cap.codigo(), true);
        if (elegido.isPresent()) {
            agregar(elegido.get(), cap);
        } else {
            txtCodigo.requestFocus();
            txtCodigo.selectAll();
        }
    }

    private void buscar(String texto) {
        BuscadorProductos.buscar(catalogo, textoBusqueda().isEmpty() ? texto : textoBusqueda(), true)
                .ifPresent(p -> agregar(p, null));
        txtCodigo.requestFocus();
    }

    /** Errores humanos: "50" en vez de "5" o un código en la cantidad. Pide confirmar cantidades raras. */
    private static boolean cantidadConfirmada(Producto p, long cantidad) {
        String aviso = Cantidad.avisoCantidadInusual(cantidad, p.getTipoVenta(), p.getUnidad());
        return aviso == null || Alertas.confirmarPeligro(p.getNombre() + "\n\n" + aviso);
    }

    /** Agrega un producto con la cantidad indicada en la captura (o la pide si es a granel). */
    private void agregar(Producto p, Captura cap) {
        long cantidad;
        try {
            if (cap != null && cap.importe() != null) {
                if (!p.getTipoVenta().aceptaDecimales()) {
                    aviso("Vender por importe ($…*) es solo para productos por peso o fracción.");
                    return;
                }
                cantidad = Cantidad.porImporte(cap.importe(), p.getPrecioVenta());
            } else if (cap != null && cap.cantidad() != null) {
                cantidad = Cantidad.parsear(cap.cantidad(), p.getTipoVenta());
            } else if (p.getTipoVenta().aceptaDecimales()) {
                Optional<Long> q = VentaDialogos.pedirCantidad(p, null);
                if (q.isEmpty()) {
                    txtCodigo.requestFocus();
                    return;
                }
                cantidad = q.get();
            } else {
                cantidad = Cantidad.UNO;
            }
        } catch (IllegalArgumentException e) {
            aviso(e.getMessage());
            return;
        }
        if (cantidad <= 0) {
            aviso("La cantidad debe ser mayor a cero.");
            return;
        }
        if (!cantidadConfirmada(p, cantidad)) {
            txtCodigo.requestFocus();
            return;
        }
        if (!hayStock(p, carrito.cantidadDe(p.getId()) + cantidad)) {
            return;
        }
        Carrito.Renglon r;
        try {
            r = carrito.agregar(p, cantidad);
        } catch (IllegalArgumentException e) {
            aviso(e.getMessage());
            return;
        }
        txtCodigo.clear();
        refrescar();
        tabla.getSelectionModel().select(r);
        tabla.scrollTo(r);
        txtCodigo.requestFocus();
    }

    private boolean hayStock(Producto p, long cantidadTotal) {
        if (config.isPermitirStockNegativo() || cantidadTotal <= p.getStock()) {
            return true;
        }
        Alertas.advertencia("No hay suficiente \"" + p.getNombre() + "\".\n"
                + "Quedan " + p.formatear(Math.max(0, p.getStock())) + " y la venta llevaría "
                + p.formatear(cantidadTotal) + ".");
        txtCodigo.requestFocus();
        return false;
    }

    private void aviso(String mensaje) {
        lblAviso.setText("⚠ " + mensaje);
        txtCodigo.requestFocus();
        txtCodigo.selectAll();
    }

    // ------------------------------------------------------------------ carrito

    private void refrescar() {
        Carrito.Renglon sel = tabla.getSelectionModel().getSelectedItem();
        items.setAll(carrito.getRenglones());
        if (sel != null && items.contains(sel)) {
            tabla.getSelectionModel().select(sel);
        }
        tabla.refresh();
        lblSubtotal.setText(Dinero.formatear(carrito.subtotal()));
        long d = carrito.getDescuento();
        lblDescuento.setText(d == 0 ? Dinero.formatear(0) : "−" + Dinero.formatear(d));
        lblDescuentoTitulo.setText(d == 0 ? "Descuento"
                : "Descuento (" + VentaService.porcentajeTexto(d, carrito.subtotal()) + ")");
        lblTotal.setText(Dinero.formatear(carrito.total()));
        long art = carrito.articulos();
        lblArticulos.setText(art == 1 ? "1 artículo" : art + " artículos");
        lblCliente.setText(carrito.getCliente() == null ? "Sin cliente (F4)"
                : "Cliente: " + carrito.getCliente().getNombre());
        btnCobrar.setDisable(carrito.vacio());
    }

    private Carrito.Renglon seleccionadoOUltimo() {
        Carrito.Renglon r = tabla.getSelectionModel().getSelectedItem();
        if (r == null && !items.isEmpty()) {
            r = items.get(items.size() - 1);
        }
        return r;
    }

    private void moverSeleccion(int delta) {
        if (items.isEmpty()) {
            return;
        }
        int i = tabla.getSelectionModel().getSelectedIndex();
        int destino = i < 0 ? (delta > 0 ? 0 : items.size() - 1) : Math.max(0, Math.min(items.size() - 1, i + delta));
        tabla.getSelectionModel().select(destino);
        tabla.scrollTo(destino);
    }

    private void sumarCantidad(int delta) {
        Carrito.Renglon r = seleccionadoOUltimo();
        if (r == null) {
            return;
        }
        if (r.getProducto().getTipoVenta().aceptaDecimales()) {
            cambiarCantidad();
            return;
        }
        long nueva = r.getCantidad() + delta * Cantidad.UNO;
        if (nueva <= 0) {
            aviso("Para quitar el producto usa Supr.");
            return;
        }
        if (delta > 0 && !hayStock(r.getProducto(), carrito.cantidadDe(r.getProducto().getId()) + Cantidad.UNO)) {
            return;
        }
        carrito.cambiarCantidad(r, nueva);
        tabla.getSelectionModel().select(r);
        refrescar();
        txtCodigo.requestFocus();
    }

    private void cambiarCantidad() {
        Carrito.Renglon r = seleccionadoOUltimo();
        if (r == null) {
            return;
        }
        Optional<Long> q = VentaDialogos.pedirCantidad(r.getProducto(), r.getCantidad());
        if (q.isPresent() && cantidadConfirmada(r.getProducto(), q.get())) {
            long otros = carrito.cantidadDe(r.getProducto().getId()) - r.getCantidad();
            if (hayStock(r.getProducto(), otros + q.get())) {
                carrito.cambiarCantidad(r, q.get());
                tabla.getSelectionModel().select(r);
                refrescar();
            }
        }
        txtCodigo.requestFocus();
    }

    private void quitar() {
        Carrito.Renglon r = tabla.getSelectionModel().getSelectedItem();
        if (r == null) {
            r = seleccionadoOUltimo();
        }
        if (r == null) {
            return;
        }
        int i = items.indexOf(r);
        carrito.quitar(r);
        refrescar();
        if (!items.isEmpty()) {
            tabla.getSelectionModel().select(Math.min(i, items.size() - 1));
        }
        txtCodigo.requestFocus();
    }

    // ------------------------------------------------------------------ acciones

    private void cliente() {
        VentaDialogos.elegirCliente(carrito.getCliente()).ifPresent(c ->
                carrito.setCliente(c == VentaDialogos.SIN_CLIENTE ? null : c));
        refrescar();
        txtCodigo.requestFocus();
    }

    private void descuento() {
        if (carrito.vacio()) {
            aviso("Agrega productos antes de aplicar un descuento.");
            return;
        }
        if (VentaDialogos.descuento(carrito, config)) {
            refrescar();
        }
        txtCodigo.requestFocus();
    }

    private void caja() {
        VentaDialogos.movimientoCaja();
        txtCodigo.requestFocus();
    }

    @FXML
    private void cobrar() {
        if (carrito.vacio()) {
            aviso("La venta no tiene productos.");
            return;
        }
        Optional<List<Pago>> pagos;
        try {
            pagos = VentaDialogos.cobrar(carrito, ventas.metodosHabilitados());
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        refrescar(); // el cliente pudo cambiar dentro del cobro
        if (pagos.isEmpty()) {
            txtCodigo.requestFocus();
            return;
        }
        try {
            VentaRegistrada v = ventas.cobrar(carrito, pagos.get());
            new TicketService().imprimir(v.id());
            Alertas.toast("Venta #" + v.folio() + " registrada"
                    + (v.cambio() > 0 ? " · Cambio " + Dinero.formatear(v.cambio()) : ""));
            carrito.limpiar();
            cargarCatalogo(); // stock actualizado en los mosaicos
            txtCodigo.clear();
            refrescar();
        } catch (IllegalArgumentException | IllegalStateException | PermisoDenegadoException e) {
            Alertas.advertencia(e.getMessage());
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        }
        txtCodigo.requestFocus();
    }

    private void ventasDelTurno() {
        if (VentaDialogos.ventasDelTurno()) {
            cargarCatalogo(); // una cancelación regresa stock
        }
        txtCodigo.requestFocus();
    }

    private void escape() {
        if (!carrito.vacio()) {
            if (Alertas.confirmar("¿Vaciar la venta? Se quitarán los " + items.size() + " renglones.")) {
                carrito.limpiar();
                txtCodigo.clear();
                refrescar();
            }
            txtCodigo.requestFocus();
        } else if (!txtCodigo.getText().isEmpty()) {
            txtCodigo.clear();
        } else {
            Flujo.irAlMenu();
        }
    }
}
