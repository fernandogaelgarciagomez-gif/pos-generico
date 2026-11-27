package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.model.Cliente;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.service.ClienteService;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Sesion;
import com.fgsistemas.pos.util.Teclado;
import com.fgsistemas.pos.util.Texto;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Comparator;

/** Fiados y clientes (20-fiados-clientes.md). Cajero: alta, abonos e historial. Admin: además editar y desactivar. */
public class FiadosController {

    @FXML private BorderPane raiz;
    @FXML private TextField txtBuscar;
    @FXML private CheckBox chkConAdeudo;
    @FXML private CheckBox chkInactivos;
    @FXML private TableView<Cliente> tabla;
    @FXML private Label lblPie;
    @FXML private Label lblTotal;
    @FXML private FlowPane barraAcciones;

    private final ClienteService servicio = new ClienteService();
    private final ObservableList<Cliente> clientes = FXCollections.observableArrayList();
    private final FilteredList<Cliente> filtrados = new FilteredList<>(clientes);
    private boolean admin;

    @FXML
    private void initialize() {
        admin = Sesion.tiene(Permiso.FIADOS_ADMINISTRAR);
        configurarTabla();
        configurarFiltros();
        configurarAcciones();
        cargar(null);
        // DECISION: el foco inicia en la tabla para que A / H funcionen de inmediato (05-decisiones.md #9);
        // si no hay clientes que mostrar, en el buscador.
        Teclado.enfocar(filtrados.isEmpty() ? txtBuscar : tabla);
    }

    // ------------------------------------------------------------------ tabla

    private void configurarTabla() {
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.setPlaceholder(new Label("No hay clientes que mostrar. Agrega uno con Nuevo (Ins/Ctrl+N)."));
        tabla.getColumns().add(Tablas.col("Nombre", 320, Cliente::getNombre));
        tabla.getColumns().add(Tablas.col("Teléfono", 160, c -> c.getTelefono() == null ? "" : c.getTelefono()));
        tabla.getColumns().add(columnaSaldo());
        tabla.getColumns().add(Tablas.col("Última compra", 170, c -> fecha(c.getUltimaCompra())));
        tabla.getColumns().add(Tablas.col("Último abono", 170, c -> fecha(c.getUltimoAbono())));

        SortedList<Cliente> ordenados = new SortedList<>(filtrados);
        ordenados.comparatorProperty().bind(tabla.comparatorProperty());
        tabla.setItems(ordenados);

        tabla.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Cliente c, boolean vacio) {
                super.updateItem(c, vacio);
                getStyleClass().remove("inactivo");
                if (!vacio && c != null && !c.isActivo()) {
                    getStyleClass().add("inactivo");
                }
            }
        });
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                enter();
            } else if (e.getCode() == KeyCode.UP && tabla.getSelectionModel().getSelectedIndex() <= 0) {
                e.consume();
                txtBuscar.requestFocus();
            }
        });
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                enter();
            }
        });
    }

    /** Saldo en rojo si debe; en verde si tiene saldo a favor. Ordena por el número, no por el texto. */
    private static TableColumn<Cliente, Cliente> columnaSaldo() {
        TableColumn<Cliente, Cliente> col = new TableColumn<>("Saldo");
        col.setPrefWidth(160);
        col.getStyleClass().add("dinero");
        col.setCellValueFactory(f -> new javafx.beans.property.ReadOnlyObjectWrapper<>(f.getValue()));
        col.setComparator(Comparator.comparingLong(Cliente::getSaldo));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Cliente cl, boolean vacio) {
                super.updateItem(cl, vacio);
                getStyleClass().removeAll("saldo-deudor", "saldo-favor");
                if (vacio || cl == null) {
                    setText(null);
                    return;
                }
                setText(cl.saldoTexto());
                if (cl.getSaldo() > 0) {
                    getStyleClass().add("saldo-deudor");
                } else if (cl.getSaldo() < 0) {
                    getStyleClass().add("saldo-favor");
                }
            }
        });
        return col;
    }

    private static String fecha(LocalDateTime f) {
        return f == null ? "—" : Fechas.FECHA_HORA.format(f);
    }

    // ------------------------------------------------------------------ filtros

    private void configurarFiltros() {
        chkInactivos.setVisible(admin);
        chkInactivos.setManaged(admin);
        txtBuscar.textProperty().addListener((o, a, n) -> aplicarFiltro());
        chkConAdeudo.selectedProperty().addListener((o, a, n) -> aplicarFiltro());
        chkInactivos.selectedProperty().addListener((o, a, n) -> cargar(idSeleccionado()));
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
        boolean conAdeudo = chkConAdeudo.isSelected();
        filtrados.setPredicate(c -> {
            if (conAdeudo && c.getSaldo() <= 0) {
                return false;
            }
            return q == null || q.isBlank()
                    || Texto.contieneTodas(c.getNombre() + " " + (c.getTelefono() == null ? "" : c.getTelefono()), q);
        });
        if (tabla.getSelectionModel().isEmpty() && !filtrados.isEmpty()) {
            tabla.getSelectionModel().selectFirst();
        }
        actualizarPie();
    }

    private void actualizarPie() {
        long activos = clientes.stream().filter(Cliente::isActivo).count();
        long conAdeudo = clientes.stream().filter(c -> c.getSaldo() > 0).count();
        long totalDeben = clientes.stream().filter(c -> c.getSaldo() > 0).mapToLong(Cliente::getSaldo).sum();
        lblPie.setText("Mostrando " + filtrados.size() + " de " + activos + " clientes activos  ·  "
                + conAdeudo + (conAdeudo == 1 ? " cliente debe" : " clientes deben"));
        lblTotal.setText("Total que deben: " + Dinero.formatear(totalDeben));
    }

    // ------------------------------------------------------------------ acciones

    private void configurarAcciones() {
        Acciones.agregar(barraAcciones, raiz, "Buscar", "F2", new KeyCodeCombination(KeyCode.F2), false,
                txtBuscar::requestFocus);
        Acciones.agregar(barraAcciones, raiz, "Nuevo cliente", "Ins/Ctrl+N", null, false, this::nuevo);
        Teclado.atajoNuevo(raiz, this::nuevo);
        Acciones.agregar(barraAcciones, raiz, "Abonar", "A", new KeyCodeCombination(KeyCode.A), true, this::abonar);
        Acciones.agregar(barraAcciones, raiz, "Historial", "H", new KeyCodeCombination(KeyCode.H), true,
                this::historial);
        if (admin) {
            barraAcciones.getChildren().add(Acciones.boton("Editar", "Enter", this::editar));
            Acciones.agregar(barraAcciones, raiz, "Desactivar / Reactivar", "Supr",
                    new KeyCodeCombination(KeyCode.DELETE), true, this::cambiarActivo);
        }
        Acciones.agregar(barraAcciones, raiz, "Solo con adeudo", "F5", new KeyCodeCombination(KeyCode.F5), false,
                () -> chkConAdeudo.setSelected(!chkConAdeudo.isSelected()));
        Acciones.espaciador(barraAcciones);
        Acciones.regresar(barraAcciones, raiz, "Regresar al menú",
                this::escape);
    }

    private void escape() {
        if (txtBuscar.isFocused() && !txtBuscar.getText().isEmpty()) {
            txtBuscar.clear();
        } else {
            Flujo.irAlMenu();
        }
    }

    /** Enter en la tabla: admin edita; el cajero (que no edita) ve el historial. */
    private void enter() {
        // DECISION: la especificación solo define Enter para el admin; para el cajero abre el historial.
        if (admin) {
            editar();
        } else {
            historial();
        }
    }

    private void cargar(Long seleccionarId) {
        try {
            clientes.setAll(servicio.listar(admin && chkInactivos.isSelected()));
            tabla.getSelectionModel().clearSelection();
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

    private Long idSeleccionado() {
        Cliente c = tabla.getSelectionModel().getSelectedItem();
        return c == null ? null : c.getId();
    }

    private Cliente seleccionado() {
        Cliente c = tabla.getSelectionModel().getSelectedItem();
        if (c == null) {
            Alertas.advertencia("Primero selecciona un cliente de la lista (flechas ↑ ↓).");
        }
        return c;
    }

    private void nuevo() {
        TextField nombre = new TextField(txtBuscar.getText().trim());
        TextField telefono = new TextField();
        TextField notas = notas(null);
        Cliente[] creado = new Cliente[1];
        boolean ok = new Formulario("Nuevo cliente", "Crear (Enter)")
                .campo("Nombre", nombre)
                .campo("Teléfono", telefono)
                .campo("Notas", notas)
                .nota("Solo el nombre es obligatorio.")
                .mostrar(() -> intentar(() -> creado[0] = servicio.crear(nombre.getText(), telefono.getText(),
                        notas.getText())));
        if (ok) {
            Alertas.toast("Cliente \"" + creado[0].getNombre() + "\" agregado");
            txtBuscar.clear();
            chkConAdeudo.setSelected(false); // un cliente nuevo no debe nada: que se vea en la lista
            cargar(creado[0].getId());
        }
        tabla.requestFocus();
    }

    private void editar() {
        if (!admin) {
            return;
        }
        Cliente sel = seleccionado();
        if (sel == null) {
            return;
        }
        TextField nombre = new TextField(sel.getNombre());
        TextField telefono = new TextField(sel.getTelefono() == null ? "" : sel.getTelefono());
        TextField notas = notas(sel.getNotas());
        boolean ok = new Formulario("Editar cliente", "Guardar (Enter)")
                .campo("Nombre", nombre)
                .campo("Teléfono", telefono)
                .campo("Notas", notas)
                .campo("Saldo", new Label(sel.saldoTexto()))
                .nota("El saldo no se edita: cambia solo con ventas a fiado, abonos y cancelaciones.")
                .mostrar(() -> intentar(() -> servicio.editar(sel.getId(), nombre.getText(), telefono.getText(),
                        notas.getText())));
        if (ok) {
            Alertas.toast("Cambios guardados");
            cargar(sel.getId());
        }
        tabla.requestFocus();
    }

    private void cambiarActivo() {
        if (!admin) {
            return;
        }
        Cliente sel = seleccionado();
        if (sel == null) {
            return;
        }
        boolean activar = !sel.isActivo();
        if (!activar && !Alertas.confirmarPeligro("¿Desactivar a " + sel.getNombre() + "?\n"
                + "Ya no aparecerá para vender a fiado. Puedes reactivarlo con \"Mostrar inactivos\".")) {
            tabla.requestFocus();
            return;
        }
        String error = intentar(() -> servicio.cambiarActivo(sel.getId(), activar));
        if (error != null) {
            Alertas.advertencia(error);
        } else {
            Alertas.toast(activar ? "Cliente reactivado" : "Cliente desactivado");
            cargar(sel.getId());
        }
        tabla.requestFocus();
    }

    private void abonar() {
        Cliente sel = seleccionado();
        if (sel == null) {
            return;
        }
        if (FiadosDialogos.abonar(sel)) {
            cargar(sel.getId());
        }
        tabla.requestFocus();
    }

    private void historial() {
        Cliente sel = seleccionado();
        if (sel == null) {
            return;
        }
        FiadosDialogos.historial(sel);
        cargar(sel.getId());
        tabla.requestFocus();
    }

    /** DECISION: notas en un campo de una línea, para que Enter avance/guarde como en todos los formularios. */
    private static TextField notas(String texto) {
        TextField t = new TextField(texto == null ? "" : texto);
        t.setPromptText("Opcional (ej. vecino de la tienda, paga los viernes)");
        return t;
    }

    @FunctionalInterface
    private interface Accion {
        void ejecutar() throws SQLException;
    }

    /** Ejecuta la acción del servicio y regresa el mensaje de error para el usuario, o null si salió bien. */
    private static String intentar(Accion accion) {
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
}
