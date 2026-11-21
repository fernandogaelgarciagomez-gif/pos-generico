package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.App;
import com.fgsistemas.pos.model.Cliente;
import com.fgsistemas.pos.model.EstadoCuenta;
import com.fgsistemas.pos.model.MetodoPago;
import com.fgsistemas.pos.service.ClienteService;
import com.fgsistemas.pos.service.ClienteService.AbonoRegistrado;
import com.fgsistemas.pos.service.EstadoCuentaExport;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Teclado;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Diálogos de Fiados: abonar y estado de cuenta (20-fiados-clientes.md). */
final class FiadosDialogos {

    private static final Logger LOG = Logger.getLogger(FiadosDialogos.class.getName());

    private FiadosDialogos() {
    }

    // ================================================================== abonar

    /**
     * Abono a la cuenta del cliente. Monto pre-llenado con el saldo completo (todo seleccionado),
     * método E / T / R, "Saldo nuevo" en vivo y L = liquidar todo.
     *
     * @return true si se registró el abono
     */
    static boolean abonar(Cliente seleccionado) {
        ClienteService servicio = new ClienteService();
        Cliente cliente;
        Set<MetodoPago> metodos;
        try {
            cliente = servicio.obtener(seleccionado.getId()); // saldo fresco de la BD
            metodos = servicio.metodosAbono();
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return false;
        } catch (IllegalArgumentException | PermisoDenegadoException e) {
            Alertas.advertencia(e.getMessage());
            return false;
        }
        long saldo = cliente.getSaldo();
        if (saldo <= 0) {
            Alertas.advertencia(cliente.getNombre() + " no tiene adeudo"
                    + (saldo < 0 ? " (tiene saldo a favor de " + Dinero.formatear(-saldo) + ")." : "."));
            return false;
        }

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Abonar");
        ButtonType tipoAbonar = new ButtonType("Abonar (Enter)", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(tipoAbonar,
                new ButtonType("Cancelar (Esc)", ButtonBar.ButtonData.CANCEL_CLOSE));
        Button btnAbonar = (Button) d.getDialogPane().lookupButton(tipoAbonar);

        Label lblTitulo = new Label("Abono de " + cliente.getNombre());
        lblTitulo.getStyleClass().add("subtitulo");
        Label lblSaldo = new Label(Dinero.formatear(saldo));
        lblSaldo.getStyleClass().add("total");

        TextField txtMonto = new TextField(Dinero.paraCampo(saldo));
        txtMonto.getStyleClass().add("campo-grande");
        Teclado.campoDinero(txtMonto);

        // Métodos (solo los habilitados en Configuración)
        ToggleGroup grupo = new ToggleGroup();
        Map<MetodoPago, ToggleButton> botones = new EnumMap<>(MetodoPago.class);
        HBox cajaMetodos = new HBox(8);
        for (MetodoPago m : List.of(MetodoPago.EFECTIVO, MetodoPago.TARJETA, MetodoPago.TRANSFERENCIA)) {
            if (metodos.contains(m)) {
                ToggleButton t = botonMetodo(m.etiqueta(), m.tecla(), grupo);
                t.setUserData(m);
                botones.put(m, t);
                cajaMetodos.getChildren().add(t);
            }
        }
        botones.get(MetodoPago.EFECTIVO).setSelected(true);
        // Que siempre haya uno elegido (un clic sobre el elegido no lo deselecciona).
        grupo.selectedToggleProperty().addListener((o, antes, ahora) -> {
            if (ahora == null && antes != null) {
                antes.setSelected(true);
            }
        });

        Label lblNuevo = new Label();
        lblNuevo.getStyleClass().add("cambio");
        Label lblError = new Label();
        lblError.getStyleClass().add("texto-error");
        lblError.setWrapText(true);
        lblError.setMaxWidth(460);
        Runnable calcular = () -> {
            lblError.setText("");
            lblNuevo.getStyleClass().remove("texto-peligro");
            try {
                long monto = txtMonto.getText().isBlank() ? 0 : Dinero.parsear(txtMonto.getText());
                if (monto > saldo) {
                    lblNuevo.setText("El abono es mayor a lo que debe");
                    lblNuevo.getStyleClass().add("texto-peligro");
                } else {
                    lblNuevo.setText(Dinero.formatear(saldo - monto));
                }
            } catch (IllegalArgumentException e) {
                lblNuevo.setText("—");
            }
        };
        txtMonto.textProperty().addListener((o, a, n) -> calcular.run());
        calcular.run();

        AbonoRegistrado[] resultado = new AbonoRegistrado[1];
        btnAbonar.addEventFilter(ActionEvent.ACTION, e -> {
            try {
                long monto = txtMonto.getText().isBlank() ? 0 : Dinero.parsear(txtMonto.getText());
                MetodoPago metodo = (MetodoPago) grupo.getSelectedToggle().getUserData();
                resultado[0] = servicio.abonar(cliente.getId(), monto, metodo, null);
            } catch (IllegalArgumentException | IllegalStateException | PermisoDenegadoException ex) {
                lblError.setText(ex.getMessage());
                txtMonto.requestFocus();
                e.consume();
            } catch (SQLException ex) {
                Flujo.errorDatos(ex);
                e.consume();
            }
        });

        Runnable liquidar = () -> {
            txtMonto.setText(Dinero.paraCampo(saldo));
            btnAbonar.fire();
        };
        Button btnLiquidar = Acciones.boton("Liquidar todo", "L", liquidar);

        // Atajos del diálogo. El campo de monto solo acepta números, así que las letras no le estorban.
        d.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() || e.isAltDown() || e.isShortcutDown()) {
                return;
            }
            MetodoPago m = switch (e.getCode()) {
                case E -> MetodoPago.EFECTIVO;
                case T -> MetodoPago.TARJETA;
                case R -> MetodoPago.TRANSFERENCIA;
                default -> null;
            };
            if (m != null) {
                e.consume();
                if (botones.containsKey(m)) {
                    botones.get(m).setSelected(true);
                }
            } else if (e.getCode() == KeyCode.L) {
                e.consume();
                liquidar.run();
            }
        });
        txtMonto.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                btnAbonar.fire();
            }
        });

        VBox contenido = new VBox(10,
                lblTitulo,
                etiqueta("Saldo actual"), lblSaldo,
                etiqueta("Monto del abono"), txtMonto,
                etiqueta("Método (E / T / R)"), cajaMetodos,
                etiqueta("Saldo nuevo"), lblNuevo,
                lblError,
                btnLiquidar);
        contenido.setPrefWidth(480);
        d.getDialogPane().setContent(contenido);
        Alertas.estilizar(d);
        d.setOnShown(e -> {
            txtMonto.requestFocus();
            txtMonto.selectAll();
        });
        d.showAndWait();

        if (resultado[0] == null) {
            return false;
        }
        Alertas.toast("Abono registrado. Saldo restante: " + Dinero.formatear(resultado[0].saldoNuevo()));
        return true;
    }

    private static ToggleButton botonMetodo(String texto, String tecla, ToggleGroup grupo) {
        ToggleButton t = new ToggleButton(texto);
        t.getStyleClass().setAll("metodo-pago");
        Label chip = new Label(tecla);
        chip.getStyleClass().add("tecla-chip");
        t.setGraphic(chip);
        t.setContentDisplay(ContentDisplay.LEFT);
        t.setToggleGroup(grupo);
        t.setFocusTraversable(false);
        t.setMinWidth(Region.USE_PREF_SIZE);
        return t;
    }

    // ================================================================== historial

    /** Estado de cuenta: ventas a fiado (+) y abonos (−) con saldo acumulado. Enter en una venta = productos. */
    static void historial(Cliente cliente) {
        ClienteService servicio = new ClienteService();
        EstadoCuenta ec;
        try {
            ec = servicio.estadoCuenta(cliente.getId());
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        } catch (IllegalArgumentException | PermisoDenegadoException e) {
            Alertas.advertencia(e.getMessage());
            return;
        }

        TableView<EstadoCuenta.Movimiento> tabla = Tablas.tabla("Sin movimientos todavía");
        tabla.getColumns().add(Tablas.col("Fecha", 160, m -> Fechas.FECHA_HORA.format(m.fecha())));
        tabla.getColumns().add(Tablas.col("Concepto", 260, EstadoCuenta.Movimiento::concepto));
        tabla.getColumns().add(Tablas.colDerecha("Cargo", 130,
                m -> m.cargo() > 0 ? Dinero.formatear(m.cargo()) : ""));
        tabla.getColumns().add(Tablas.colDerecha("Abono", 130,
                m -> m.abono() > 0 ? Dinero.formatear(m.abono()) : ""));
        tabla.getColumns().add(Tablas.colDerecha("Saldo", 140, m -> Cliente.textoSaldo(m.saldo())));
        tabla.getColumns().forEach(c -> c.setSortable(false)); // el orden es cronológico
        tabla.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(EstadoCuenta.Movimiento m, boolean vacio) {
                super.updateItem(m, vacio);
                getStyleClass().remove("cancelada");
                if (!vacio && m != null && m.cancelada()) {
                    getStyleClass().add("cancelada");
                }
            }
        });
        tabla.setItems(FXCollections.observableArrayList(ec.movimientos()));
        if (!ec.movimientos().isEmpty()) { // lo más reciente hasta abajo, ya seleccionado
            int ultimo = ec.movimientos().size() - 1;
            tabla.getSelectionModel().select(ultimo);
            tabla.scrollTo(ultimo);
        }

        Cliente c = ec.cliente();
        Label encabezado = new Label(c.getNombre() + (c.getTelefono() == null ? "" : "  ·  Tel. " + c.getTelefono())
                + (c.getNotas() == null ? "" : "\n" + c.getNotas()));
        encabezado.getStyleClass().add("texto-secundario");
        encabezado.setWrapText(true);
        Label pie = new Label((ec.saldoFinal() < 0 ? "Saldo a favor: " + Dinero.formatear(-ec.saldoFinal())
                : "Saldo actual: " + Dinero.formatear(ec.saldoFinal()))
                + (ec.movimientos().stream().anyMatch(EstadoCuenta.Movimiento::cancelada)
                ? "      (las ventas tachadas se cancelaron y no suman)" : ""));
        pie.getStyleClass().add("subtitulo");

        Runnable verVenta = () -> {
            EstadoCuenta.Movimiento m = tabla.getSelectionModel().getSelectedItem();
            if (m != null && m.ventaId() != null) {
                VentaDialogos.verVenta(m.ventaId());
                tabla.requestFocus();
            }
        };
        Runnable pdf = () -> exportarPdf(servicio, ec);
        Runnable excel = () -> Exportar.excel(EstadoCuentaExport.nombreBase(c), "Estado de cuenta",
                EstadoCuentaExport.ENCABEZADOS, EstadoCuentaExport.filasExcel(ec));
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                verVenta.run();
            } else if (e.getCode() == KeyCode.F7 && e.isShiftDown()) {
                e.consume();
                excel.run();
            } else if (e.getCode() == KeyCode.F7) {
                e.consume();
                pdf.run();
            }
        });
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                verVenta.run();
            }
        });

        Tablas.dialogo("Estado de cuenta", encabezado, tabla, pie, 920, 560, List.of(
                Acciones.boton("Ver productos de la venta", "Enter", verVenta),
                Acciones.boton("Exportar a PDF", "F7", pdf),
                Acciones.boton("Exportar a Excel", "Shift+F7", excel)));
    }

    private static void exportarPdf(ClienteService servicio, EstadoCuenta ec) {
        try {
            Path archivo = servicio.exportarPdf(ec);
            if (Alertas.confirmar("Archivo guardado en:\n" + archivo + "\n\n¿Abrirlo ahora?")) {
                App.abrirArchivo(archivo);
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "No se pudo exportar el PDF", e);
            Alertas.error("No se pudo guardar el PDF.\n\n" + e.getMessage()
                    + "\n\nSi el archivo está abierto, ciérralo e intenta de nuevo.");
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        }
    }

    private static Label etiqueta(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("texto-secundario");
        return l;
    }
}
