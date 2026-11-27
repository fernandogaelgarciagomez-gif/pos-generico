package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.App;
import com.fgsistemas.pos.config.AppConfig;
import com.fgsistemas.pos.config.Hardware;
import com.fgsistemas.pos.config.Migraciones;
import com.fgsistemas.pos.dao.CategoriaDao.ConConteo;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.service.ActualizacionService;
import com.fgsistemas.pos.service.AuthService;
import com.fgsistemas.pos.service.InventarioService;
import com.fgsistemas.pos.service.NegocioService;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.service.RespaldoService;
import com.fgsistemas.pos.service.RespaldoService.Respaldo;
import com.fgsistemas.pos.service.TicketService;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.CodigoBarras;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Navegador;
import com.fgsistemas.pos.util.Teclado;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuración (22-configuracion.md) con la pestaña de respaldos (23-respaldos.md). Solo admin.
 * <p>
 * DECISION: un solo "Guardar" (Ctrl+S) en la barra inferior guarda todas las pestañas a la vez; las
 * categorías, el logo y los respaldos se aplican al momento (cada uno con su botón).
 */
public class ConfiguracionController {

    private static final Logger LOG = Logger.getLogger(ConfiguracionController.class.getName());

    @FXML private BorderPane raiz;
    @FXML private FlowPane barraAcciones;
    @FXML private TabPane pestanas;
    @FXML private BarraSuperiorController barraSuperiorController;

    private final NegocioService negocioService = new NegocioService();
    private final InventarioService inventario = new InventarioService();
    private final RespaldoService respaldos = new RespaldoService();

    // Negocio
    private final TextField txtNombre = new TextField();
    private final TextField txtDireccion = new TextField();
    private final TextField txtTelefono = new TextField();
    private final TextField txtRfc = new TextField();
    private final TextField txtMensaje = new TextField();
    private final ImageView imgLogo = new ImageView();
    private final Label lblSinLogo = new Label("Sin logo");
    // Inventario
    private final TextField txtStockMinimo = new TextField();
    private final CheckBox chkStockNegativo = new CheckBox("Permitir vender sin stock (el stock puede quedar negativo)");
    private final TableView<ConConteo> tablaCategorias = new TableView<>();
    // Ventas
    private final CheckBox chkEfectivo = new CheckBox("Efectivo (siempre habilitado)");
    private final CheckBox chkTarjeta = new CheckBox("Tarjeta");
    private final CheckBox chkTransferencia = new CheckBox("Transferencia");
    private final CheckBox chkFiado = new CheckBox("Fiado");
    private final TextField txtDescuento = new TextField();
    // Respaldos
    private final CheckBox chkRespaldoAuto = new CheckBox(
            "Respaldo automático (al abrir el sistema una vez al día y después de cada corte)");
    private final TextField txtDias = new TextField();
    private final TableView<Respaldo> tablaRespaldos = new TableView<>();
    // Ticket y lector (de esta computadora: se guardan en hardware.properties, no en la BD)
    private static final String SIN_IMPRESORA = "(Sin impresora: no imprimir tickets)";
    private static final String PAPEL_58 = "58 mm (32 caracteres por renglón)";
    private static final String PAPEL_80 = "80 mm (48 caracteres por renglón)";
    private final ComboBox<String> cmbImpresora = new ComboBox<>();
    private final ComboBox<String> cmbPapel = new ComboBox<>(FXCollections.observableArrayList(PAPEL_58, PAPEL_80));
    private final ComboBox<Hardware.ModoTicket> cmbModo =
            new ComboBox<>(FXCollections.observableArrayList(Hardware.ModoTicket.values()));
    private final CheckBox chkCortar = new CheckBox("Cortar el papel al terminar (si la impresora tiene cortador)");
    private final CheckBox chkCajon = new CheckBox(
            "Abrir el cajón de dinero al cobrar en efectivo (cajón conectado a la impresora)");
    private final CheckBox chkSinAcentos = new CheckBox(
            "Imprimir sin acentos (si en el ticket salen símbolos raros en lugar de á, é, ñ…)");
    private final CheckBox chkLector = new CheckBox(
            "En Vender, mandar al campo de código lo que lea el lector aunque el cursor esté en otro lado");

    private Tab tabNegocio;
    private Tab tabInventario;
    private Tab tabVentas;
    private Tab tabRespaldos;
    private Tab tabHardware;
    private Tab tabActualizar;
    private Tab tabAcerca;
    // Actualizaciones (decisión 85)
    private final ActualizacionService actualizaciones = new ActualizacionService();
    private final Label lblActualizacion = new Label("Presiona \"Buscar actualizaciones\" (F5).");
    private final Label lblNotasVersion = pequeno("");
    private Button btnInstalarVersion;
    private ActualizacionService.Disponible disponible;
    private boolean actualizando;

    private boolean cargando;
    private boolean sucio;

    @FXML
    private void initialize() {
        tabNegocio = new Tab("Negocio", envolver(armarNegocio()));
        tabInventario = new Tab("Inventario", envolver(armarInventario()));
        tabVentas = new Tab("Ventas", envolver(armarVentas()));
        tabRespaldos = new Tab("Respaldos", envolver(armarRespaldos()));
        tabHardware = new Tab("Ticket y lector", envolver(armarHardware()));
        tabActualizar = new Tab("Actualizaciones", envolver(armarActualizaciones()));
        tabAcerca = new Tab("Acerca de", envolver(armarAcerca()));
        pestanas.getTabs().addAll(tabNegocio, tabInventario, tabVentas, tabRespaldos, tabHardware, tabActualizar,
                tabAcerca);
        pestanas.getSelectionModel().selectedItemProperty().addListener((o, a, tab) -> enfocarPestana(tab));

        Acciones.agregar(barraAcciones, raiz, "Guardar", "Ctrl+S",
                new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), false, this::guardar);
        barraAcciones.getChildren().add(Acciones.boton("Siguiente pestaña", "Ctrl+Tab", () -> cambiarPestana(1)));
        Acciones.espaciador(barraAcciones);
        Acciones.agregar(barraAcciones, raiz, "Menú", "Esc", new KeyCodeCombination(KeyCode.ESCAPE), false,
                this::salir);
        // Ctrl+Tab / Ctrl+Shift+Tab desde cualquier campo (los campos de texto se quedarían con la tecla).
        raiz.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.TAB && e.isShortcutDown()) {
                e.consume();
                cambiarPestana(e.isShiftDown() ? -1 : 1);
            }
        });

        cargarNegocio();
        cargarCategorias();
        cargarRespaldos();
        cargarHardware();
        vigilarCambios(txtNombre, txtDireccion, txtTelefono, txtRfc, txtMensaje, txtStockMinimo, txtDescuento, txtDias);
        vigilarCambios(chkStockNegativo, chkTarjeta, chkTransferencia, chkFiado, chkRespaldoAuto);
        vigilarCambios(chkCortar, chkCajon, chkSinAcentos, chkLector);
        for (ComboBox<?> c : List.of(cmbImpresora, cmbPapel, cmbModo)) {
            c.valueProperty().addListener((o, a, b) -> marcarSucio());
        }
        Navegador.alCerrarAvisar(() -> sucio ? "Hay cambios sin guardar en Configuración." : null);
        Teclado.enfocar(txtNombre);
    }

    // ------------------------------------------------------------------ armado de pestañas

    private Node armarNegocio() {
        txtNombre.setPromptText("Obligatorio");
        txtRfc.setPromptText("Opcional");
        txtMensaje.setPromptText("Ej. ¡Gracias por su compra!");
        GridPane g = rejilla();
        fila(g, 0, "Nombre del negocio", txtNombre);
        fila(g, 1, "Dirección", txtDireccion);
        fila(g, 2, "Teléfono", txtTelefono);
        fila(g, 3, "RFC", txtRfc);
        fila(g, 4, "Mensaje para ticket", txtMensaje);
        Teclado.cadenaEnter(this::guardar, txtNombre, txtDireccion, txtTelefono, txtRfc, txtMensaje);

        imgLogo.setFitWidth(140);
        imgLogo.setFitHeight(140);
        imgLogo.setPreserveRatio(true);
        lblSinLogo.getStyleClass().add("texto-secundario");
        StackPane marco = new StackPane(lblSinLogo, imgLogo);
        marco.getStyleClass().add("panel-seccion");
        marco.setMinSize(170, 170);
        marco.setMaxSize(170, 170);
        Button elegir = Acciones.boton("Elegir logo…", "Ctrl+L", this::elegirLogo);
        Button quitar = Acciones.boton("Quitar logo", "Ctrl+Shift+L", this::quitarLogo);
        atajo(tabNegocio(), new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN), false, this::elegirLogo);
        atajo(tabNegocio(), new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN,
                KeyCombination.SHIFT_DOWN), false, this::quitarLogo);
        Label ayudaLogo = pequeno("PNG o JPG de hasta 5 MB. Se muestra en el inicio de sesión. "
                + "El logo se aplica al momento; lo demás, con Guardar (Ctrl+S).");
        VBox logo = new VBox(10, subtitulo("Logo"), new HBox(16, marco, new VBox(10, elegir, quitar)), ayudaLogo);

        return new VBox(24, subtitulo("Datos del negocio"), g,
                pequeno("El nombre y los datos salen en la barra superior, el inicio de sesión y las exportaciones."),
                logo);
    }

    private Node armarInventario() {
        Teclado.soloNumeros(txtStockMinimo, 3);
        txtStockMinimo.setMaxWidth(180);
        GridPane g = rejilla();
        fila(g, 0, "Stock mínimo para productos nuevos", txtStockMinimo);
        g.add(chkStockNegativo, 1, 1);
        Teclado.cadenaEnter(this::guardar, txtStockMinimo, chkStockNegativo);

        tablaCategorias.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tablaCategorias.setPlaceholder(new Label("Sin categorías. Crea una con Ins o Ctrl+N."));
        tablaCategorias.getColumns().add(Tablas.col("Categoría", 360, ConConteo::nombre));
        tablaCategorias.getColumns().add(Tablas.col("Productos", 140, k -> String.valueOf(k.productos())));
        tablaCategorias.setPrefHeight(320);
        tablaCategorias.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                renombrarCategoria();
            }
        });
        tablaCategorias.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                renombrarCategoria();
            }
        });
        Button nueva = Acciones.boton("Nueva categoría", "Ins/Ctrl+N", this::nuevaCategoria);
        Button renombrar = Acciones.boton("Renombrar", "Enter", this::renombrarCategoria);
        Button quitar = Acciones.boton("Quitar", "Supr", this::quitarCategoria);
        atajo(tabInventario(), new KeyCodeCombination(KeyCode.INSERT), false, this::nuevaCategoria);
        atajo(tabInventario(), new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), false,
                this::nuevaCategoria);
        atajo(tabInventario(), new KeyCodeCombination(KeyCode.DELETE), true, this::quitarCategoria);

        VBox categorias = new VBox(10, subtitulo("Categorías"),
                pequeno("Se aplican al momento. Solo se puede quitar una categoría sin productos."),
                new HBox(8, nueva, renombrar, quitar), tablaCategorias);
        return new VBox(24, subtitulo("Inventario"), g,
                pequeno("El stock mínimo se escribe en unidades del producto (piezas, kg, m…); acepta hasta 3 decimales."),
                categorias);
    }

    private Node armarVentas() {
        chkEfectivo.setSelected(true);
        chkEfectivo.setDisable(true);
        Teclado.soloNumeros(txtDescuento, 0);
        txtDescuento.setMaxWidth(120);
        VBox metodos = new VBox(12, chkEfectivo, chkTarjeta, chkTransferencia, chkFiado);
        GridPane g = rejilla();
        fila(g, 0, "Descuento máximo que se puede autorizar (%)", txtDescuento);
        Teclado.cadenaEnter(this::guardar, chkTarjeta, chkTransferencia, chkFiado, txtDescuento);
        return new VBox(24, subtitulo("Métodos de pago habilitados"), metodos,
                pequeno("Un método deshabilitado no aparece en el cobro. Tarjeta y transferencia también aplican "
                        + "a los abonos de fiados. Espacio marca o desmarca."),
                g);
    }

    private Node armarRespaldos() {
        Teclado.soloNumeros(txtDias, 0);
        txtDias.setMaxWidth(120);
        GridPane g = rejilla();
        g.add(chkRespaldoAuto, 0, 0, 2, 1);
        fila(g, 1, "Conservar respaldos de los últimos (días)", txtDias);
        Teclado.cadenaEnter(this::guardar, chkRespaldoAuto, txtDias);

        tablaRespaldos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tablaRespaldos.setPlaceholder(new Label("Aún no hay respaldos. Haz uno con F5."));
        tablaRespaldos.getColumns().add(Tablas.col("Fecha", 200, r -> Fechas.FECHA_HORA.format(r.fecha())));
        tablaRespaldos.getColumns().add(Tablas.col("Archivo", 420, r -> r.nombre()
                + (r.esPrevioARestaurar() ? "   (antes de restaurar)" : "")));
        tablaRespaldos.getColumns().add(Tablas.col("Tamaño", 120, Respaldo::tamanoTexto));
        tablaRespaldos.setPrefHeight(300);

        Button ahora = Acciones.boton("Respaldar ahora", "F5", this::respaldarAhora);
        Button otra = Acciones.boton("Respaldar en otra ubicación…", "F6", this::respaldarEnOtra);
        Button restaurar = Acciones.boton("Restaurar", "R", this::restaurarSeleccionado);
        restaurar.getStyleClass().add("boton-peligro");
        Button desdeArchivo = Acciones.boton("Restaurar desde archivo…", "Ctrl+O", this::restaurarDesdeArchivo);
        desdeArchivo.getStyleClass().add("boton-peligro");
        Button exportar = Acciones.boton("Exportar todo a Excel", "F7", this::exportarTodo);
        atajo(tabRespaldos(), new KeyCodeCombination(KeyCode.F5), false, this::respaldarAhora);
        atajo(tabRespaldos(), new KeyCodeCombination(KeyCode.F6), false, this::respaldarEnOtra);
        atajo(tabRespaldos(), new KeyCodeCombination(KeyCode.R), true, this::restaurarSeleccionado);
        atajo(tabRespaldos(), new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), false,
                this::restaurarDesdeArchivo);
        atajo(tabRespaldos(), new KeyCodeCombination(KeyCode.F7), false, this::exportarTodo);

        Label carpeta = pequeno("Carpeta: " + carpetaRespaldos());
        FlowPane botones = new FlowPane(8, 8, ahora, otra, restaurar, desdeArchivo, exportar);
        return new VBox(20, subtitulo("Respaldo automático"), g, subtitulo("Respaldos"), carpeta, botones,
                tablaRespaldos,
                pequeno("Restaurar reemplaza TODOS los datos por los del respaldo (antes se respalda el estado "
                        + "actual) y regresa al inicio de sesión. Exportar todo: productos, clientes, ventas y "
                        + "cortes del mes, en Documentos\\Punto de Venta\\Exportaciones."));
    }

    private Node armarHardware() {
        for (ComboBox<?> c : List.of(cmbImpresora, cmbPapel, cmbModo)) {
            c.setMaxWidth(Double.MAX_VALUE);
            Teclado.comboConFlechas(c);
        }
        GridPane g = rejilla();
        fila(g, 0, "Impresora de tickets", cmbImpresora);
        fila(g, 1, "Ancho del papel", cmbPapel);
        fila(g, 2, "Al cobrar", cmbModo);
        g.add(chkCortar, 1, 3);
        g.add(chkCajon, 1, 4);
        g.add(chkSinAcentos, 1, 5);
        Teclado.cadenaEnter(this::guardar, cmbImpresora, cmbPapel, cmbModo, chkCortar, chkCajon, chkSinAcentos);

        Button actualizar = Acciones.boton("Buscar impresoras", "F5", this::cargarImpresoras);
        Button prueba = Acciones.boton("Imprimir prueba", "Ctrl+P", this::imprimirPrueba);
        Button vista = Acciones.boton("Vista previa", "F3", this::vistaPreviaTicket);
        Button cajon = Acciones.boton("Abrir cajón", "F4", this::probarCajon);
        atajo(tabHardware(), new KeyCodeCombination(KeyCode.F5), false, this::cargarImpresoras);
        atajo(tabHardware(), new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN), false,
                this::imprimirPrueba);
        atajo(tabHardware(), new KeyCodeCombination(KeyCode.F3), false, this::vistaPreviaTicket);
        atajo(tabHardware(), new KeyCodeCombination(KeyCode.F4), false, this::probarCajon);

        Button probarLector = Acciones.boton("Probar lector", "F9", this::probarLector);
        atajo(tabHardware(), new KeyCodeCombination(KeyCode.F9), false, this::probarLector);

        return new VBox(20,
                subtitulo("Impresora de tickets (térmica)"),
                pequeno("Sirve cualquier impresora térmica de 58 u 80 mm compatible con ESC/POS (Epson, Xprinter, "
                        + "3nStar, Ec Line, genéricas…). Primero instálala en Windows con su controlador (o como "
                        + "\"Genérico / Solo texto\") y luego elígela aquí. No elijas una impresora normal de hojas "
                        + "ni \"Microsoft Print to PDF\"."),
                g,
                new FlowPane(8, 8, actualizar, prueba, vista, cajon),
                pequeno("Prueba y vista previa usan lo que está en pantalla; Guardar (Ctrl+S) lo deja fijo. "
                        + "En Vender, Ctrl+P reimprime el último ticket; en Ventas del turno, P reimprime la venta "
                        + "elegida. Esta configuración es solo de esta computadora."),
                subtitulo("Lector de código de barras"),
                pequeno("Sirve cualquier lector USB o inalámbrico en modo teclado (el que viene así de fábrica): se "
                        + "conecta y listo, no necesita controlador. Al leer un código \"escribe\" los números y da "
                        + "Enter, igual que si se tecleara en el campo de código de Vender o Entrada de mercancía. "
                        + "Si en el inventario el código está con o sin el 0 inicial, se encuentra igual."),
                chkLector,
                new HBox(8, probarLector),
                pequeno("Si al probar salen letras o símbolos cambiados (por ejemplo ' en lugar de -), el lector y "
                        + "Windows tienen distinto idioma de teclado: configura el lector en \"Latinoamérica\" o "
                        + "\"Español\" con las hojas de códigos de su manual."));
    }

    private Node armarActualizaciones() {
        Button buscar = Acciones.boton("Buscar actualizaciones", "F5", this::buscarActualizacion);
        btnInstalarVersion = Acciones.boton("Descargar e instalar", "F6", this::instalarActualizacion);
        btnInstalarVersion.setDisable(true);
        atajo(tabActualizar(), new KeyCodeCombination(KeyCode.F5), false, this::buscarActualizacion);
        atajo(tabActualizar(), new KeyCodeCombination(KeyCode.F6), false, this::instalarActualizacion);
        lblActualizacion.setWrapText(true);
        lblActualizacion.setMaxWidth(900);
        VBox v = new VBox(10, subtitulo("Versión instalada: " + AppConfig.version()),
                pequeno("El programa se actualiza solo desde internet, sin reinstalar ni llevar USB. Al instalar una "
                        + "versión nueva se hace un respaldo, el programa se cierra y se vuelve a abrir solo en unos "
                        + "segundos. Las ventas, el inventario y la configuración no se tocan. Una vez al día, al "
                        + "entrar al menú, el sistema revisa si hay versión nueva y te avisa."),
                new HBox(8, buscar, btnInstalarVersion), lblActualizacion, lblNotasVersion);
        if (ActualizacionService.instalacionActual().isEmpty()) {
            v.getChildren().add(pequeno("Este programa no se está ejecutando desde la instalación (por ejemplo, desde "
                    + "el proyecto): se puede buscar, pero no instalar."));
        }
        return v;
    }

    private void buscarActualizacion() {
        if (actualizando) {
            return;
        }
        actualizando = true;
        disponible = null;
        btnInstalarVersion.setDisable(true);
        lblNotasVersion.setText("");
        lblActualizacion.setText("Buscando…");
        Thread t = new Thread(() -> {
            String mensaje;
            Optional<ActualizacionService.Disponible> d = Optional.empty();
            try {
                d = actualizaciones.buscar();
                mensaje = d.map(x -> "Hay una versión nueva: " + x.version()
                                + (x.bytes() > 0 ? String.format(" (%.1f MB)", x.bytes() / 1048576.0) : "")
                                + ". Presiona \"Descargar e instalar\" (F6).")
                        .orElse("Ya tienes la versión más reciente (" + AppConfig.version() + ").");
            } catch (IOException e) {
                LOG.log(Level.INFO, "No se pudo buscar actualización", e);
                mensaje = "No se pudo revisar: " + e.getMessage();
            }
            Optional<ActualizacionService.Disponible> fin = d;
            String texto = mensaje;
            javafx.application.Platform.runLater(() -> {
                actualizando = false;
                disponible = fin.orElse(null);
                lblActualizacion.setText(texto);
                if (disponible != null) {
                    lblNotasVersion.setText(disponible.notas().isEmpty() ? "" : "Novedades: " + disponible.notas());
                    btnInstalarVersion.setDisable(false);
                }
            });
        }, "buscar-actualizacion");
        t.setDaemon(true);
        t.start();
    }

    private void instalarActualizacion() {
        ActualizacionService.Disponible d = disponible;
        if (d == null || actualizando) {
            return;
        }
        if (d.requiereInstalador()) {
            Alertas.info("Actualización", "La versión " + d.version() + " cambia partes internas del programa y "
                    + "necesita el instalador completo.\n\nDescárgalo de:\n"
                    + (d.paginaDescarga().isEmpty() ? AppConfig.URL_VERSIONES : d.paginaDescarga())
                    + "\n\nCierra el programa y ejecuta Instalar.bat; tus datos se conservan.");
            return;
        }
        Optional<ActualizacionService.Instalacion> inst = ActualizacionService.instalacionActual();
        if (inst.isEmpty()) {
            Alertas.advertencia("Este programa no se está ejecutando desde la instalación: no se puede actualizar "
                    + "solo. Usa el instalador.");
            return;
        }
        if (sucio) {
            Alertas.advertencia("Primero guarda los cambios de Configuración (Ctrl+S).");
            return;
        }
        if (!Alertas.confirmar("Se va a instalar la versión " + d.version() + ":\n\n"
                + "1. Se hace un respaldo de tus datos.\n"
                + "2. Se baja la versión nueva de internet.\n"
                + "3. El programa se cierra y se abre solo con la versión nueva (unos segundos).\n\n"
                + "Las ventas, el inventario y la configuración no se tocan. ¿Continuar?")) {
            return;
        }
        actualizando = true;
        btnInstalarVersion.setDisable(true);
        lblActualizacion.setText("Haciendo respaldo…");
        Thread t = new Thread(() -> {
            try {
                respaldos.respaldarAhora();
                javafx.application.Platform.runLater(() -> lblActualizacion.setText("Bajando la versión nueva…"));
                Path jar = actualizaciones.descargar(d, bytes -> javafx.application.Platform.runLater(() ->
                        lblActualizacion.setText(String.format("Bajando la versión nueva… %.1f MB", bytes / 1048576.0))));
                actualizaciones.lanzarAyudante(inst.get(), jar, d.version());
                javafx.application.Platform.runLater(() -> {
                    lblActualizacion.setText("Cerrando para terminar la actualización…");
                    LOG.info("Cerrando para instalar la versión " + d.version());
                    javafx.application.Platform.exit();
                    System.exit(0);
                });
            } catch (Exception e) {
                LOG.log(Level.WARNING, "No se pudo instalar la actualización", e);
                javafx.application.Platform.runLater(() -> {
                    actualizando = false;
                    btnInstalarVersion.setDisable(false);
                    lblActualizacion.setText("No se pudo actualizar; sigues con la versión " + AppConfig.version()
                            + ".");
                    Alertas.error("No se pudo actualizar.\n\n" + (e instanceof IOException ? e.getMessage()
                            : com.fgsistemas.pos.util.Registro.mensajeInesperado(e)));
                });
            }
        }, "instalar-actualizacion");
        t.setDaemon(true);
        t.start();
    }

    private Node armarAcerca() {
        VBox v = new VBox(10);
        Label app = new Label(AppConfig.NOMBRE_APP);
        app.getStyleClass().add("titulo");
        v.getChildren().addAll(app, new Label("Versión " + AppConfig.version()),
                new Label("Desarrollado por " + AppConfig.DESARROLLADOR));
        if (!AppConfig.CONTACTO_TELEFONO.isBlank()) {
            v.getChildren().add(new Label("Teléfono: " + AppConfig.CONTACTO_TELEFONO));
        }
        if (!AppConfig.CONTACTO_CORREO.isBlank()) {
            v.getChildren().add(new Label("Correo: " + AppConfig.CONTACTO_CORREO));
        }
        v.getChildren().addAll(new Label(""),
                pequeno("Carpeta de datos: " + AppConfig.carpetaDatos()),
                pequeno("Versión de la base de datos: " + Migraciones.versionMasReciente()),
                pequeno("Java " + System.getProperty("java.version")));
        return v;
    }

    // ------------------------------------------------------------------ carga y guardado

    private void cargarNegocio() {
        Negocio n;
        try {
            n = negocioService.obtener().orElseGet(Negocio::new);
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        cargando = true;
        txtNombre.setText(texto(n.getNombre()));
        txtDireccion.setText(texto(n.getDireccion()));
        txtTelefono.setText(texto(n.getTelefono()));
        txtRfc.setText(texto(n.getRfc()));
        txtMensaje.setText(texto(n.getMensajeTicket()));
        txtStockMinimo.setText(BigDecimal.valueOf(n.getStockMinimoDefault(), 3).stripTrailingZeros().toPlainString());
        chkStockNegativo.setSelected(n.isPermitirStockNegativo());
        chkTarjeta.setSelected(n.isMetodoTarjeta());
        chkTransferencia.setSelected(n.isMetodoTransferencia());
        chkFiado.setSelected(n.isMetodoFiado());
        txtDescuento.setText(String.valueOf(n.getDescuentoMaximoPct()));
        chkRespaldoAuto.setSelected(n.isRespaldoAutomatico());
        txtDias.setText(String.valueOf(n.getDiasConservarRespaldos()));
        cargando = false;
        sucio = false;
        mostrarLogo(n);
    }

    private void guardar() {
        Negocio n;
        try {
            n = negocioService.obtener().orElseGet(Negocio::new);
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        n.setNombre(txtNombre.getText());
        n.setDireccion(txtDireccion.getText());
        n.setTelefono(txtTelefono.getText());
        n.setRfc(txtRfc.getText());
        n.setMensajeTicket(txtMensaje.getText());
        try {
            n.setStockMinimoDefault(txtStockMinimo.getText().isBlank() ? 0
                    : Cantidad.parsear(txtStockMinimo.getText(), TipoVenta.PESO));
        } catch (IllegalArgumentException e) {
            error(tabInventario, txtStockMinimo, "Stock mínimo: " + e.getMessage());
            return;
        }
        n.setPermitirStockNegativo(chkStockNegativo.isSelected());
        n.setMetodoTarjeta(chkTarjeta.isSelected());
        n.setMetodoTransferencia(chkTransferencia.isSelected());
        n.setMetodoFiado(chkFiado.isSelected());
        Integer descuento = entero(txtDescuento);
        if (descuento == null) {
            error(tabVentas, txtDescuento, "Escribe el descuento máximo (0 a 100).");
            return;
        }
        n.setDescuentoMaximoPct(descuento);
        n.setRespaldoAutomatico(chkRespaldoAuto.isSelected());
        Integer dias = entero(txtDias);
        if (dias == null) {
            error(tabRespaldos, txtDias, "Escribe cuántos días conservar los respaldos.");
            return;
        }
        n.setDiasConservarRespaldos(dias);
        try {
            negocioService.guardar(n);
        } catch (IllegalArgumentException e) {
            Tab tab = tabNegocio;
            Node campo = txtNombre;
            String m = e.getMessage().toLowerCase();
            if (m.contains("stock")) {
                tab = tabInventario;
                campo = txtStockMinimo;
            } else if (m.contains("descuento")) {
                tab = tabVentas;
                campo = txtDescuento;
            } else if (m.contains("respaldo")) {
                tab = tabRespaldos;
                campo = txtDias;
            }
            error(tab, campo, e.getMessage());
            return;
        } catch (PermisoDenegadoException | IllegalStateException e) {
            Alertas.error(e.getMessage());
            return;
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        Hardware previo = Hardware.cargar();
        Hardware hw = hardwareDeLaPantalla();
        try {
            hw.guardar();
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "No se pudo guardar la configuración de la impresora", e);
            pestanas.getSelectionModel().select(tabHardware);
            Alertas.error("No se pudo guardar la configuración de la impresora y el lector.\n\n" + e.getMessage());
            return;
        }
        cargarNegocio();
        cargarHardware();
        barraSuperiorController.refrescar();
        Alertas.toast("Configuración guardada");
        // Error humano: elegir "Imprimir siempre / Preguntar" sin elegir la impresora (no saldría nada).
        boolean tocoTicket = previo.getModo() != hw.getModo() || !previo.getImpresora().equals(hw.getImpresora());
        if (tocoTicket && !hw.hayImpresora() && hw.getModo() != Hardware.ModoTicket.NUNCA) {
            pestanas.getSelectionModel().select(tabHardware);
            Alertas.advertencia("Elegiste \"" + hw.getModo().etiqueta() + "\", pero no hay impresora de tickets "
                    + "elegida.\n\nElige tu impresora en la lista \"Impresora de tickets\" y vuelve a guardar.\n\n"
                    + (hw.getModo() == Hardware.ModoTicket.PREGUNTAR
                    ? "Mientras tanto, al cobrar se preguntará y el ticket se mostrará en pantalla."
                    : "Mientras tanto no se imprimirán tickets."));
            Teclado.enfocar(cmbImpresora);
        }
    }

    private void salir() {
        if (sucio && !Alertas.confirmarPeligro("Hay cambios sin guardar en Configuración.\n\n¿Salir sin guardarlos?")) {
            return;
        }
        Flujo.irAlMenu();
    }

    // ------------------------------------------------------------------ logo

    private void elegirLogo() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Elegir logo del negocio");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes PNG o JPG", "*.png", "*.jpg", "*.jpeg"));
        File archivo = fc.showOpenDialog(Navegador.stage());
        if (archivo == null) {
            return;
        }
        try (InputStream in = Files.newInputStream(archivo.toPath())) {
            if (new Image(in).isError()) {
                Alertas.error("No se pudo leer la imagen. Prueba con otro archivo PNG o JPG.");
                return;
            }
        } catch (IOException e) {
            Alertas.error("No se pudo leer la imagen.\n\n" + e.getMessage());
            return;
        }
        try {
            negocioService.cambiarLogo(archivo.toPath());
            mostrarLogo(negocioService.obtener().orElse(null));
            Alertas.toast("Logo actualizado");
        } catch (IllegalArgumentException | PermisoDenegadoException e) {
            Alertas.error(e.getMessage());
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "No se pudo copiar el logo", e);
            Alertas.error("No se pudo copiar la imagen.\n\n" + e.getMessage());
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        }
    }

    private void quitarLogo() {
        if (imgLogo.getImage() == null) {
            Alertas.advertencia("El negocio no tiene logo.");
            return;
        }
        if (!Alertas.confirmar("¿Quitar el logo del negocio?")) {
            return;
        }
        try {
            negocioService.quitarLogo();
            mostrarLogo(null);
            Alertas.toast("Logo quitado");
        } catch (PermisoDenegadoException e) {
            Alertas.error(e.getMessage());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo borrar el archivo del logo", e);
            mostrarLogo(null);
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        }
    }

    private void mostrarLogo(Negocio n) {
        Image imagen = null;
        Optional<Path> archivo = NegocioService.archivoLogo(n);
        if (archivo.isPresent()) {
            try (InputStream in = Files.newInputStream(archivo.get())) {
                Image leida = new Image(in);
                imagen = leida.isError() ? null : leida;
            } catch (IOException e) {
                LOG.log(Level.WARNING, "No se pudo leer el logo", e);
            }
        }
        imgLogo.setImage(imagen);
        lblSinLogo.setVisible(imagen == null);
    }

    // ------------------------------------------------------------------ ticket y lector

    private void cargarHardware() {
        Hardware h = Hardware.cargar();
        boolean antes = cargando;
        cargando = true;
        cargarImpresoras(h.getImpresora());
        cmbPapel.setValue(h.getAnchoPapelMm() == 80 ? PAPEL_80 : PAPEL_58);
        cmbModo.setValue(h.getModo());
        chkCortar.setSelected(h.isCortarPapel());
        chkCajon.setSelected(h.isAbrirCajon());
        chkSinAcentos.setSelected(h.isSinAcentos());
        chkLector.setSelected(h.isCapturarLector());
        cargando = antes;
    }

    private void cargarImpresoras() {
        String actual = cmbImpresora.getValue();
        cargarImpresoras(SIN_IMPRESORA.equals(actual) ? "" : actual);
        int total = cmbImpresora.getItems().size() - 1;
        Alertas.toast(total == 1 ? "1 impresora encontrada en Windows" : total + " impresoras encontradas en Windows");
    }

    /** Llena la lista con las impresoras de Windows y deja elegida {@code elegir} (aunque ya no esté conectada). */
    private void cargarImpresoras(String elegir) {
        List<String> lista = new java.util.ArrayList<>();
        lista.add(SIN_IMPRESORA);
        try {
            lista.addAll(TicketService.impresorasInstaladas());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo leer la lista de impresoras de Windows", e);
        }
        if (elegir != null && !elegir.isBlank() && lista.stream().noneMatch(elegir::equalsIgnoreCase)) {
            lista.add(elegir); // configurada pero desconectada: se conserva para no perderla
        }
        boolean antes = cargando;
        cargando = true;
        cmbImpresora.setItems(FXCollections.observableArrayList(lista));
        cmbImpresora.setValue(elegir == null || elegir.isBlank() ? SIN_IMPRESORA
                : lista.stream().filter(elegir::equalsIgnoreCase).findFirst().orElse(SIN_IMPRESORA));
        cargando = antes;
    }

    private Hardware hardwareDeLaPantalla() {
        Hardware h = new Hardware();
        String imp = cmbImpresora.getValue();
        h.setImpresora(imp == null || SIN_IMPRESORA.equals(imp) ? "" : imp);
        h.setAnchoPapelMm(PAPEL_80.equals(cmbPapel.getValue()) ? 80 : 58);
        h.setModo(cmbModo.getValue());
        h.setCortarPapel(chkCortar.isSelected());
        h.setAbrirCajon(chkCajon.isSelected());
        h.setSinAcentos(chkSinAcentos.isSelected());
        h.setCapturarLector(chkLector.isSelected());
        return h;
    }

    private Hardware hardwareConImpresora() {
        Hardware h = hardwareDeLaPantalla();
        if (!h.hayImpresora()) {
            Alertas.advertencia("Primero elige la impresora de tickets en la lista.");
            Teclado.enfocar(cmbImpresora);
            return null;
        }
        return h;
    }

    private void imprimirPrueba() {
        Hardware h = hardwareConImpresora();
        if (h == null) {
            return;
        }
        try {
            byte[] datos = new TicketService().ticketPrueba(h).bytes();
            Impresion.avisarSiFalla(TicketService.enviar(h.getImpresora(), datos, "Ticket de prueba"),
                    "No se pudo imprimir el ticket de prueba");
            Alertas.toast("Ticket de prueba enviado a " + h.getImpresora());
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        }
    }

    private void vistaPreviaTicket() {
        try {
            Impresion.vistaPrevia("Vista previa del ticket", new TicketService().ticketPrueba(hardwareDeLaPantalla()));
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        }
    }

    private void probarCajon() {
        Hardware h = hardwareConImpresora();
        if (h == null) {
            return;
        }
        Impresion.avisarSiFalla(TicketService.abrirCajon(h), "No se pudo abrir el cajón");
        Alertas.toast("Orden de abrir cajón enviada");
    }

    /** Ventana para escanear códigos y ver qué llega: tipo, si es válido y a qué producto corresponde. */
    private void probarLector() {
        TextField txt = new TextField();
        txt.setPromptText("Escanea un código aquí");
        Label resultado = new Label("Apunta el lector a un código de barras y dispara. Puedes probar varios.");
        resultado.setWrapText(true);
        resultado.setMaxWidth(560);
        resultado.setMinHeight(130);
        txt.setOnAction(e -> {
            String crudo = txt.getText();
            String codigo = CodigoBarras.limpiar(crudo);
            if (codigo.isEmpty()) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Leído: ").append(codigo).append("   (").append(codigo.length()).append(" caracteres)\n");
            sb.append("Tipo: ").append(CodigoBarras.tipo(codigo)).append('\n');
            if (!crudo.equals(codigo)) {
                sb.append("El lector mandó espacios o caracteres de control extra; se quitan solos.\n");
            }
            try {
                Optional<Producto> p = inventario.buscarActivoPorCodigo(codigo);
                sb.append(p.map(x -> "Producto: " + x.getNombre() + " · " + Dinero.formatear(x.getPrecioVenta())
                                + (x.getCodigo().equals(codigo) ? "" : "  (registrado como " + x.getCodigo() + ")"))
                        .orElse("Ningún producto activo tiene este código (dalo de alta en Inventario)."));
            } catch (SQLException ex) {
                sb.append("No se pudo buscar el producto: ").append(ex.getMessage());
            } catch (PermisoDenegadoException ex) {
                sb.append(ex.getMessage());
            }
            resultado.setText(sb.toString());
            txt.clear();
        });
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Probar lector de código de barras");
        d.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar (Esc)", ButtonBar.ButtonData.CANCEL_CLOSE));
        d.getDialogPane().setContent(new VBox(12, subtitulo("Probar lector de código de barras"), txt, resultado));
        Alertas.estilizar(d);
        d.setOnShown(e -> Teclado.enfocar(txt));
        d.showAndWait();
    }

    // ------------------------------------------------------------------ categorías

    private void cargarCategorias() {
        cargarCategorias(null);
    }

    private void cargarCategorias(String seleccionar) {
        try {
            List<ConConteo> lista = inventario.categoriasConConteo();
            tablaCategorias.setItems(FXCollections.observableArrayList(lista));
            int indice = 0;
            for (int i = 0; seleccionar != null && i < lista.size(); i++) {
                if (lista.get(i).nombre().equalsIgnoreCase(seleccionar)) {
                    indice = i;
                }
            }
            if (!lista.isEmpty()) {
                tablaCategorias.getSelectionModel().select(indice);
                tablaCategorias.scrollTo(indice);
            }
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        } catch (PermisoDenegadoException e) {
            Alertas.error(e.getMessage());
        }
    }

    private void nuevaCategoria() {
        TextField nombre = new TextField();
        String[] creada = new String[1];
        boolean ok = new Formulario("Nueva categoría", "Crear (Enter)")
                .campo("Nombre", nombre)
                .mostrar(() -> intentar(() -> creada[0] = inventario.crearCategoria(nombre.getText())));
        if (ok) {
            cargarCategorias(creada[0]);
            Alertas.toast("Categoría \"" + creada[0] + "\" lista");
        }
        tablaCategorias.requestFocus();
    }

    private void renombrarCategoria() {
        ConConteo k = tablaCategorias.getSelectionModel().getSelectedItem();
        if (k == null) {
            Alertas.advertencia("Elige una categoría de la lista.");
            return;
        }
        TextField nombre = new TextField(k.nombre());
        Teclado.seleccionarTodoAlFoco(nombre);
        boolean ok = new Formulario("Renombrar categoría", "Guardar (Enter)")
                .campo("Nombre", nombre)
                .nota(k.productos() == 0 ? "No tiene productos."
                        : "Los " + k.productos() + " productos de esta categoría quedan con el nombre nuevo.")
                .mostrar(() -> intentar(() -> inventario.renombrarCategoria(k.id(), nombre.getText())));
        if (ok) {
            cargarCategorias(nombre.getText().trim());
            Alertas.toast("Categoría renombrada");
        }
        tablaCategorias.requestFocus();
    }

    private void quitarCategoria() {
        ConConteo k = tablaCategorias.getSelectionModel().getSelectedItem();
        if (k == null) {
            return;
        }
        if (k.productos() > 0) {
            Alertas.advertencia("\"" + k.nombre() + "\" tiene " + k.productos()
                    + (k.productos() == 1 ? " producto" : " productos")
                    + ".\n\nCámbialos de categoría en Inventario antes de quitarla.");
            return;
        }
        if (!Alertas.confirmarPeligro("¿Quitar la categoría \"" + k.nombre() + "\"?")) {
            return;
        }
        try {
            inventario.quitarCategoria(k.id());
            cargarCategorias();
            Alertas.toast("Categoría quitada");
        } catch (IllegalArgumentException | IllegalStateException | PermisoDenegadoException e) {
            Alertas.error(e.getMessage());
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        }
        tablaCategorias.requestFocus();
    }

    // ------------------------------------------------------------------ respaldos

    private void cargarRespaldos() {
        try {
            List<Respaldo> lista = respaldos.listar();
            tablaRespaldos.setItems(FXCollections.observableArrayList(lista));
            if (!lista.isEmpty()) {
                tablaRespaldos.getSelectionModel().selectFirst();
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo leer la carpeta de respaldos", e);
            Alertas.error("No se pudo leer la carpeta de respaldos.\n\n" + e.getMessage());
        } catch (PermisoDenegadoException e) {
            Alertas.error(e.getMessage());
        }
    }

    private void respaldarAhora() {
        try {
            Path archivo = respaldos.respaldarAhora();
            cargarRespaldos();
            Alertas.toast("Respaldo creado: " + archivo.getFileName());
        } catch (PermisoDenegadoException e) {
            Alertas.error(e.getMessage());
        } catch (SQLException | IOException e) {
            LOG.log(Level.SEVERE, "No se pudo respaldar", e);
            Alertas.error("No se pudo hacer el respaldo.\n\n" + e.getMessage());
        }
    }

    private void respaldarEnOtra() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("¿Dónde guardar el respaldo? (USB, carpeta de Google Drive…)");
        File carpeta = dc.showDialog(Navegador.stage());
        if (carpeta == null) {
            return;
        }
        try {
            Path archivo = respaldos.respaldarEn(carpeta.toPath());
            Alertas.info("Respaldo creado", "El respaldo se guardó en:\n" + archivo);
        } catch (IllegalArgumentException | PermisoDenegadoException e) {
            Alertas.error(e.getMessage());
        } catch (SQLException | IOException e) {
            LOG.log(Level.SEVERE, "No se pudo respaldar en otra ubicación", e);
            Alertas.error("No se pudo guardar el respaldo en esa carpeta.\n\n" + e.getMessage());
        }
    }

    private void restaurarSeleccionado() {
        Respaldo r = tablaRespaldos.getSelectionModel().getSelectedItem();
        if (r == null) {
            Alertas.advertencia("Elige un respaldo de la lista (o usa Ctrl+O para buscar un archivo).");
            return;
        }
        restaurar(r.archivo(), Fechas.FECHA_HORA.format(r.fecha()));
    }

    private void restaurarDesdeArchivo() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Elegir respaldo para restaurar");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Respaldos del punto de venta (*.db)", "*.db"));
        File archivo = fc.showOpenDialog(Navegador.stage());
        if (archivo != null) {
            restaurar(archivo.toPath(), archivo.getName());
        }
    }

    private void restaurar(Path archivo, String descripcion) {
        TextField confirmacion = new TextField();
        confirmacion.setPromptText(RespaldoService.PALABRA_RESTAURAR);
        Path[] seguridad = new Path[1];
        boolean ok = new Formulario("Restaurar respaldo", "Restaurar")
                .campo("Escribe " + RespaldoService.PALABRA_RESTAURAR, confirmacion)
                .nota("Respaldo: " + descripcion + "\n\nSe reemplazarán TODOS los datos actuales (ventas, inventario, "
                        + "clientes, cortes…) por los de ese respaldo. Antes se hará un respaldo del estado actual. "
                        + "Al terminar se cerrará la sesión.")
                .mostrar(() -> {
                    try {
                        seguridad[0] = respaldos.restaurar(archivo, confirmacion.getText());
                        return null;
                    } catch (IllegalArgumentException | IllegalStateException | PermisoDenegadoException e) {
                        return e.getMessage();
                    } catch (SQLException | IOException e) {
                        LOG.log(Level.SEVERE, "No se pudo restaurar el respaldo", e);
                        return "No se pudo restaurar: " + e.getMessage();
                    }
                });
        if (!ok) {
            return;
        }
        Alertas.info("Respaldo restaurado", "Los datos quedaron como en el respaldo " + descripcion + ".\n\n"
                + "El estado anterior se guardó en:\n" + seguridad[0] + "\n\nInicia sesión de nuevo.");
        AuthService.instancia().logout();
        try {
            Flujo.irAInicio();
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        }
    }

    private void exportarTodo() {
        try {
            Path archivo = respaldos.exportarTodo();
            if (Alertas.confirmar("Archivo guardado en:\n" + archivo + "\n\n¿Abrirlo ahora?")) {
                App.abrirArchivo(archivo);
            }
        } catch (PermisoDenegadoException e) {
            Alertas.error(e.getMessage());
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "No se pudo exportar todo", e);
            Alertas.error("No se pudo guardar el archivo de Excel.\n\n" + e.getMessage()
                    + "\n\nSi el archivo está abierto en Excel, ciérralo e intenta de nuevo.");
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        }
    }

    // ------------------------------------------------------------------ utilidades

    /** Atajo que solo actúa con {@code tab} visible (y, si se pide, con el foco fuera de campos de texto). */
    private void atajo(java.util.function.Supplier<Tab> tab, KeyCombination tecla, boolean soloFueraDeTexto,
                       Runnable accion) {
        raiz.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!tecla.match(e) || pestanas.getSelectionModel().getSelectedItem() != tab.get()) {
                return;
            }
            if (soloFueraDeTexto) {
                Scene escena = raiz.getScene();
                Node foco = escena == null ? null : escena.getFocusOwner();
                if (foco != null && Teclado.esCampoTexto(foco)) {
                    return;
                }
            }
            e.consume();
            accion.run();
        });
    }

    // Las pestañas se crean después de armar su contenido; los atajos las consultan al dispararse.
    private java.util.function.Supplier<Tab> tabNegocio() {
        return () -> tabNegocio;
    }

    private java.util.function.Supplier<Tab> tabInventario() {
        return () -> tabInventario;
    }

    private java.util.function.Supplier<Tab> tabRespaldos() {
        return () -> tabRespaldos;
    }

    private java.util.function.Supplier<Tab> tabHardware() {
        return () -> tabHardware;
    }

    private java.util.function.Supplier<Tab> tabActualizar() {
        return () -> tabActualizar;
    }

    private void cambiarPestana(int delta) {
        int total = pestanas.getTabs().size();
        int i = pestanas.getSelectionModel().getSelectedIndex();
        pestanas.getSelectionModel().select((i + delta + total) % total);
    }

    private void enfocarPestana(Tab tab) {
        Node destino;
        if (tab == tabNegocio) {
            destino = txtNombre;
        } else if (tab == tabInventario) {
            destino = txtStockMinimo;
        } else if (tab == tabVentas) {
            destino = chkTarjeta;
        } else if (tab == tabRespaldos) {
            destino = tablaRespaldos.getItems().isEmpty() ? chkRespaldoAuto : tablaRespaldos;
        } else if (tab == tabHardware) {
            destino = cmbImpresora;
        } else {
            destino = raiz;
        }
        Teclado.enfocar(destino);
    }

    private void error(Tab tab, Node campo, String mensaje) {
        pestanas.getSelectionModel().select(tab);
        Alertas.error(mensaje);
        Teclado.enfocar(campo);
    }

    private void vigilarCambios(TextField... campos) {
        for (TextField t : campos) {
            t.textProperty().addListener((o, a, b) -> marcarSucio());
        }
    }

    private void vigilarCambios(CheckBox... casillas) {
        for (CheckBox c : casillas) {
            c.selectedProperty().addListener((o, a, b) -> marcarSucio());
        }
    }

    private void marcarSucio() {
        if (!cargando) {
            sucio = true;
        }
    }

    /** Envuelve la pestaña en un ScrollPane con margen (por si la pantalla es baja). */
    private static Node envolver(Node contenido) {
        VBox caja = new VBox(contenido);
        caja.setStyle("-fx-padding: 20 8 20 8;");
        ScrollPane sp = new ScrollPane(caja);
        sp.setFitToWidth(true);
        sp.setFocusTraversable(false);
        return sp;
    }

    private static GridPane rejilla() {
        GridPane g = new GridPane();
        g.setHgap(16);
        g.setVgap(12);
        g.setAlignment(Pos.TOP_LEFT);
        ColumnConstraints etiquetas = new ColumnConstraints();
        etiquetas.setMinWidth(260);
        ColumnConstraints controles = new ColumnConstraints(200, 520, 640);
        controles.setHgrow(Priority.SOMETIMES);
        g.getColumnConstraints().addAll(etiquetas, controles);
        return g;
    }

    private static void fila(GridPane g, int fila, String etiqueta, Node control) {
        g.add(new Label(etiqueta), 0, fila);
        g.add(control, 1, fila);
    }

    private static Label subtitulo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("subtitulo");
        return l;
    }

    private static Label pequeno(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("texto-secundario");
        l.setWrapText(true);
        l.setMaxWidth(900);
        return l;
    }

    private static String texto(String s) {
        return s == null ? "" : s;
    }

    private static Integer entero(TextField t) {
        try {
            return t.getText().isBlank() ? null : Integer.parseInt(t.getText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Path carpetaRespaldos() {
        try {
            return RespaldoService.carpeta();
        } catch (RuntimeException e) {
            return AppConfig.carpetaRespaldos();
        }
    }

    /** Ejecuta una acción del servicio y regresa el mensaje de error (o null si salió bien). */
    private static String intentar(AccionServicio accion) {
        try {
            accion.ejecutar();
            return null;
        } catch (IllegalArgumentException | IllegalStateException | PermisoDenegadoException e) {
            return e.getMessage();
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return "No se pudo guardar.";
        }
    }

    @FunctionalInterface
    private interface AccionServicio {
        void ejecutar() throws SQLException;
    }
}
