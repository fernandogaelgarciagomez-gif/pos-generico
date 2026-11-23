package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.dao.MovimientoCajaDao;
import com.fgsistemas.pos.model.Carrito;
import com.fgsistemas.pos.model.Cliente;
import com.fgsistemas.pos.model.MetodoPago;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.Venta;
import com.fgsistemas.pos.service.AuthService;
import com.fgsistemas.pos.service.Autorizacion;
import com.fgsistemas.pos.service.CajaService;
import com.fgsistemas.pos.service.ClienteService;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.service.VentaService;
import com.fgsistemas.pos.service.VentaService.Pago;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Sesion;
import com.fgsistemas.pos.util.Teclado;
import com.fgsistemas.pos.util.Texto;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Diálogos de la pantalla Vender. */
final class VentaDialogos {

    /** Resultado especial de {@link #elegirCliente}: quitar el cliente de la venta. */
    static final Cliente SIN_CLIENTE = new Cliente();

    private VentaDialogos() {
    }

    // ================================================================== cantidad

    /**
     * Pide la cantidad de un producto. A granel permite capturar por cantidad o por importe (tecla I).
     *
     * @param actual cantidad actual (milésimas) para editar, o null
     * @return cantidad en milésimas
     */
    static Optional<Long> pedirCantidad(Producto p, Long actual) {
        boolean granel = p.getTipoVenta().aceptaDecimales();
        ComboBox<String> cmbModo = new ComboBox<>(FXCollections.observableArrayList(
                "Por cantidad (" + p.getUnidad() + ")", "Por importe ($)"));
        cmbModo.getSelectionModel().selectFirst();
        cmbModo.setMaxWidth(Double.MAX_VALUE);
        Teclado.comboConFlechas(cmbModo);
        TextField txtValor = new TextField(actual == null ? (granel ? "" : "1")
                : Cantidad.paraCampo(actual, p.getTipoVenta()));
        txtValor.getStyleClass().add("campo-grande");
        Label lblResultado = new Label();
        lblResultado.getStyleClass().add("subtitulo");

        Runnable formato = () -> {
            boolean porImporte = cmbModo.getSelectionModel().getSelectedIndex() == 1;
            String t = txtValor.getText();
            Teclado.soloNumeros(txtValor, porImporte ? 2 : (granel ? 3 : 0));
            txtValor.setText(t.replaceAll("[^0-9.]", ""));
        };
        Runnable vista = () -> {
            try {
                long q = calcularCantidad(p, cmbModo.getSelectionModel().getSelectedIndex() == 1, txtValor.getText());
                lblResultado.setText(p.formatear(q) + "  =  " + Dinero.formatear(Cantidad.importe(p.getPrecioVenta(), q)));
            } catch (IllegalArgumentException e) {
                lblResultado.setText("—");
            }
        };
        cmbModo.valueProperty().addListener((o, a, n) -> {
            formato.run();
            vista.run();
            txtValor.requestFocus();
        });
        txtValor.textProperty().addListener((o, a, n) -> vista.run());
        formato.run();
        vista.run();

        Formulario f = new Formulario(p.getNombre(), "Agregar (Enter)");
        f.campo("Precio", new Label(Dinero.formatear(p.getPrecioVenta()) + " /" + p.getUnidad()
                + "   ·   Quedan " + p.stockTexto()));
        if (granel) {
            // El modo va al lado del campo (no en la cadena de Enter): Enter en el número agrega directo.
            cmbModo.setPrefWidth(230);
            f.campo("Cantidad o importe", txtValor, cmbModo)
                    .atajo(new KeyCodeCombination(KeyCode.I), () -> cmbModo.getSelectionModel().select(
                            1 - cmbModo.getSelectionModel().getSelectedIndex()));
        } else {
            f.campo("Cantidad", txtValor);
        }
        f.campo("Resultado", lblResultado)
                .nota(granel ? "I alterna entre capturar cantidad (" + p.getUnidad() + ") o importe ($50 de…)."
                        : "Solo números enteros.");
        long[] resultado = new long[1];
        boolean ok = f.mostrar(() -> {
            try {
                long q = calcularCantidad(p, cmbModo.getSelectionModel().getSelectedIndex() == 1, txtValor.getText());
                if (q <= 0) {
                    return "La cantidad debe ser mayor a cero.";
                }
                resultado[0] = q;
                return null;
            } catch (IllegalArgumentException e) {
                return e.getMessage();
            }
        });
        return ok ? Optional.of(resultado[0]) : Optional.empty();
    }

    private static long calcularCantidad(Producto p, boolean porImporte, String texto) {
        if (porImporte) {
            return Cantidad.porImporte(Dinero.parsear(texto), p.getPrecioVenta());
        }
        return Cantidad.parsear(texto, p.getTipoVenta());
    }

    // ================================================================== cobro

    /** Fila del pago mixto. */
    private record FilaPago(MetodoPago metodo, long monto) {
    }

    /**
     * Diálogo de cobro (F10): E efectivo, T tarjeta, R transferencia, F fiado, M mixto; Enter confirma.
     *
     * @return los pagos (cuadran exacto con el total), o vacío si se canceló
     */
    static Optional<List<Pago>> cobrar(Carrito carrito, Set<MetodoPago> habilitados) {
        long total = carrito.total();
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Cobrar");
        ButtonType tipoCobrar = new ButtonType("Cobrar (Enter)", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(tipoCobrar,
                new ButtonType("Cancelar (Esc)", ButtonBar.ButtonData.CANCEL_CLOSE));
        Button btnCobrar = (Button) d.getDialogPane().lookupButton(tipoCobrar);

        Label lblTotalTitulo = new Label("Total a cobrar");
        lblTotalTitulo.getStyleClass().add("texto-secundario");
        Label lblTotal = new Label(Dinero.formatear(total));
        lblTotal.getStyleClass().add("total");
        Label lblError = new Label();
        lblError.getStyleClass().add("texto-error");
        lblError.setWrapText(true);

        // ---- Efectivo
        TextField txtRecibido = new TextField(Dinero.paraCampo(total));
        txtRecibido.getStyleClass().add("campo-grande");
        Teclado.campoDinero(txtRecibido);
        Label lblCambio = new Label();
        lblCambio.getStyleClass().add("cambio");
        Runnable calcularCambio = () -> {
            try {
                long rec = txtRecibido.getText().isBlank() ? total : Dinero.parsear(txtRecibido.getText());
                lblCambio.setText(rec >= total ? Dinero.formatear(rec - total) : "Falta " + Dinero.formatear(total - rec));
                lblCambio.getStyleClass().remove("texto-peligro");
                if (rec < total) {
                    lblCambio.getStyleClass().add("texto-peligro");
                }
            } catch (IllegalArgumentException e) {
                lblCambio.setText("—");
            }
        };
        txtRecibido.textProperty().addListener((o, a, n) -> calcularCambio.run());
        calcularCambio.run();
        VBox panelEfectivo = new VBox(8, etiqueta("Recibido"), txtRecibido, etiqueta("Cambio"), lblCambio);

        // ---- Tarjeta / transferencia
        TextField txtRefTarjeta = new TextField();
        txtRefTarjeta.setPromptText("Referencia o últimos 4 dígitos (opcional)");
        VBox panelTarjeta = new VBox(8, new Label("Cobrar " + Dinero.formatear(total) + " con tarjeta (terminal)."),
                etiqueta("Referencia"), txtRefTarjeta);
        TextField txtRefTransfer = new TextField();
        txtRefTransfer.setPromptText("Folio o referencia de la transferencia (opcional)");
        VBox panelTransfer = new VBox(8, new Label("Cobrar " + Dinero.formatear(total) + " por transferencia."),
                etiqueta("Referencia"), txtRefTransfer);

        // ---- Fiado (y cliente para mixto)
        Label lblClienteFiado = new Label();
        lblClienteFiado.setWrapText(true);
        Label lblClienteMixto = new Label();
        lblClienteMixto.setWrapText(true);
        lblClienteMixto.getStyleClass().add("texto-secundario");
        Runnable textoCliente = () -> {
            Cliente c = carrito.getCliente();
            if (c == null) {
                lblClienteFiado.setText("Sin cliente. Elige uno con F4 para poder fiar.");
                lblClienteMixto.setText("Cliente: ninguno (F4 si hay parte fiada)");
            } else {
                lblClienteFiado.setText("Cliente: " + c.getNombre() + "\nSaldo hoy: " + c.saldoTexto()
                        + "   →   después de esta venta: " + Cliente.textoSaldo(c.getSaldo() + total));
                lblClienteMixto.setText("Cliente: " + c.getNombre() + " (F4 para cambiar)");
            }
        };
        Runnable elegirCliente = () -> {
            elegirCliente(carrito.getCliente()).ifPresent(c -> carrito.setCliente(c == SIN_CLIENTE ? null : c));
            textoCliente.run();
        };
        textoCliente.run();
        Button btnCliente = Acciones.boton("Elegir cliente", "F4", elegirCliente);
        VBox panelFiado = new VBox(10, lblClienteFiado, btnCliente);

        // ---- Mixto
        ObservableList<FilaPago> filas = FXCollections.observableArrayList();
        TableView<FilaPago> tablaMixto = Tablas.tabla("Agrega los pagos: método + monto + Enter");
        tablaMixto.getColumns().add(Tablas.col("Método", 200, f -> f.metodo().etiqueta()));
        tablaMixto.getColumns().add(Tablas.colDerecha("Monto", 140, f -> Dinero.formatear(f.monto())));
        tablaMixto.setItems(filas);
        tablaMixto.setPrefHeight(150);
        ComboBox<MetodoPago> cmbMetodoMixto = new ComboBox<>(FXCollections.observableArrayList(
                habilitados.stream().sorted().toList()));
        cmbMetodoMixto.setConverter(convertidor(MetodoPago::etiqueta));
        cmbMetodoMixto.getSelectionModel().selectFirst();
        Teclado.comboConFlechas(cmbMetodoMixto);
        TextField txtMontoMixto = new TextField();
        Teclado.campoDinero(txtMontoMixto);
        Label lblRestante = new Label();
        lblRestante.getStyleClass().add("subtitulo");
        Runnable restante = () -> {
            long r = total - filas.stream().mapToLong(FilaPago::monto).sum();
            lblRestante.setText("Restante: " + Dinero.formatear(r));
            txtMontoMixto.setText(r > 0 ? Dinero.paraCampo(r) : "");
        };
        Runnable agregarMixto = () -> {
            try {
                long r = total - filas.stream().mapToLong(FilaPago::monto).sum();
                long m = Dinero.parsear(txtMontoMixto.getText());
                if (m <= 0) {
                    throw new IllegalArgumentException("El monto debe ser mayor a cero.");
                }
                if (m > r) {
                    throw new IllegalArgumentException("El monto excede lo que falta (" + Dinero.formatear(r) + ").");
                }
                filas.add(new FilaPago(cmbMetodoMixto.getValue(), m));
                lblError.setText("");
                restante.run();
                if (total - filas.stream().mapToLong(FilaPago::monto).sum() == 0) {
                    btnCobrar.requestFocus();
                } else {
                    cmbMetodoMixto.requestFocus();
                }
            } catch (IllegalArgumentException e) {
                lblError.setText(e.getMessage());
            }
        };
        txtMontoMixto.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                agregarMixto.run();
            }
        });
        Teclado.cadenaEnter(txtMontoMixto::requestFocus, cmbMetodoMixto);
        tablaMixto.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DELETE && tablaMixto.getSelectionModel().getSelectedItem() != null) {
                e.consume();
                filas.remove(tablaMixto.getSelectionModel().getSelectedItem());
                restante.run();
            }
        });
        restante.run();
        HBox capturaMixto = new HBox(8, cmbMetodoMixto, txtMontoMixto,
                Acciones.boton("Agregar", "Enter", agregarMixto));
        capturaMixto.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(txtMontoMixto, Priority.ALWAYS);
        VBox panelMixto = new VBox(8, capturaMixto, tablaMixto, lblRestante, lblClienteMixto,
                etiquetaPequena("Supr en la tabla quita un pago. F4 elige cliente."));

        // ---- Selector de método
        Map<MetodoPago, Node> paneles = new EnumMap<>(MetodoPago.class);
        paneles.put(MetodoPago.EFECTIVO, panelEfectivo);
        paneles.put(MetodoPago.TARJETA, panelTarjeta);
        paneles.put(MetodoPago.TRANSFERENCIA, panelTransfer);
        paneles.put(MetodoPago.FIADO, panelFiado);
        StackPane contenedor = new StackPane();
        contenedor.setMinHeight(260);
        ToggleGroup grupo = new ToggleGroup();
        FlowPane botonesMetodo = new FlowPane(8, 8);
        Map<String, ToggleButton> porTecla = new java.util.HashMap<>();
        Map<ToggleButton, Node> panelDe = new java.util.HashMap<>();
        Map<ToggleButton, Node> focoDe = new java.util.HashMap<>();
        for (MetodoPago m : MetodoPago.values()) {
            if (!habilitados.contains(m)) {
                continue;
            }
            ToggleButton t = botonMetodo(m.etiqueta(), m.tecla(), grupo);
            t.setUserData(m);
            botonesMetodo.getChildren().add(t);
            porTecla.put(m.tecla(), t);
            panelDe.put(t, paneles.get(m));
            focoDe.put(t, switch (m) {
                case EFECTIVO -> txtRecibido;
                case TARJETA -> txtRefTarjeta;
                case TRANSFERENCIA -> txtRefTransfer;
                case FIADO -> btnCliente;
            });
        }
        if (habilitados.size() > 1) {
            ToggleButton t = botonMetodo("Mixto", "M", grupo);
            t.setUserData("MIXTO");
            botonesMetodo.getChildren().add(t);
            porTecla.put("M", t);
            panelDe.put(t, panelMixto);
            focoDe.put(t, cmbMetodoMixto);
        }
        grupo.selectedToggleProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) {
                grupo.selectToggle(antes); // siempre hay un método elegido
                return;
            }
            lblError.setText("");
            contenedor.getChildren().setAll(panelDe.get((ToggleButton) ahora));
            Node foco = focoDe.get((ToggleButton) ahora);
            javafx.application.Platform.runLater(foco::requestFocus);
        });

        // Teclas E/T/R/F/M (no mientras se escribe una referencia), F4 cliente.
        d.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() || e.isAltDown()) {
                return;
            }
            if (e.getCode() == KeyCode.F4) {
                e.consume();
                elegirCliente.run();
                return;
            }
            Node foco = d.getDialogPane().getScene().getFocusOwner();
            boolean escribiendoTexto = foco == txtRefTarjeta || foco == txtRefTransfer;
            if (!escribiendoTexto && e.getCode().isLetterKey()) {
                ToggleButton t = porTecla.get(e.getCode().getName().toUpperCase());
                if (t != null) {
                    e.consume();
                    grupo.selectToggle(t);
                }
            }
            if (e.getCode() == KeyCode.ENTER && foco instanceof TextInputControl && foco != txtMontoMixto) {
                e.consume();
                btnCobrar.fire();
            }
        });

        // Validación al cobrar
        List<Pago> resultado = new ArrayList<>();
        btnCobrar.addEventFilter(ActionEvent.ACTION, e -> {
            resultado.clear();
            String error = armarPagos(grupo, total, txtRecibido, txtRefTarjeta, txtRefTransfer, filas, carrito, resultado);
            if (error != null) {
                lblError.setText(error);
                e.consume();
                return;
            }
            // Errores humanos: un cero de más en "Recibido" haría dar un cambio enorme.
            for (Pago p : resultado) {
                String aviso = p.metodo() == MetodoPago.EFECTIVO && p.recibido() != null
                        ? com.fgsistemas.pos.service.Avisos.cambio(p.monto(), p.recibido()) : null;
                if (aviso != null && !Alertas.confirmarPeligro(aviso)) {
                    e.consume();
                    txtRecibido.requestFocus();
                    txtRecibido.selectAll();
                    return;
                }
            }
        });

        Label titulo = new Label("Cobrar");
        titulo.getStyleClass().add("subtitulo");
        Label ayuda = etiquetaPequena("Elige el método con su letra: "
                + String.join(" · ", porTecla.keySet().stream().sorted().toList()) + ". Enter cobra.");
        VBox contenido = new VBox(12, titulo, new VBox(0, lblTotalTitulo, lblTotal), botonesMetodo, contenedor,
                lblError, ayuda);
        contenido.setPrefWidth(620);
        d.getDialogPane().setContent(contenido);
        Alertas.estilizar(d);
        grupo.selectToggle(porTecla.get("E"));
        d.setOnShown(e -> {
            txtRecibido.requestFocus();
            txtRecibido.selectAll();
        });
        Optional<ButtonType> r = d.showAndWait();
        return r.isPresent() && r.get() == tipoCobrar ? Optional.of(List.copyOf(resultado)) : Optional.empty();
    }

    /** Arma la lista de pagos según el método elegido; regresa el error para mostrar o null. */
    private static String armarPagos(ToggleGroup grupo, long total, TextField txtRecibido, TextField txtRefTarjeta,
                                     TextField txtRefTransfer, List<FilaPago> filas, Carrito carrito,
                                     List<Pago> salida) {
        Object metodo = grupo.getSelectedToggle() == null ? null : grupo.getSelectedToggle().getUserData();
        if (metodo == MetodoPago.EFECTIVO) {
            long rec;
            try {
                rec = txtRecibido.getText().isBlank() ? total : Dinero.parsear(txtRecibido.getText());
            } catch (IllegalArgumentException e) {
                return e.getMessage();
            }
            if (rec < total) {
                return "El efectivo recibido es menor al total.";
            }
            salida.add(new Pago(MetodoPago.EFECTIVO, total, rec, null));
        } else if (metodo == MetodoPago.TARJETA) {
            salida.add(new Pago(MetodoPago.TARJETA, total, null, txtRefTarjeta.getText()));
        } else if (metodo == MetodoPago.TRANSFERENCIA) {
            salida.add(new Pago(MetodoPago.TRANSFERENCIA, total, null, txtRefTransfer.getText()));
        } else if (metodo == MetodoPago.FIADO) {
            if (carrito.getCliente() == null) {
                return "Para fiar, elige un cliente (F4).";
            }
            salida.add(new Pago(MetodoPago.FIADO, total, null, null));
        } else if ("MIXTO".equals(metodo)) {
            long suma = filas.stream().mapToLong(FilaPago::monto).sum();
            if (filas.isEmpty() || suma != total) {
                return "Falta repartir " + Dinero.formatear(total - suma) + ".";
            }
            boolean fiado = filas.stream().anyMatch(f -> f.metodo() == MetodoPago.FIADO);
            if (fiado && carrito.getCliente() == null) {
                return "Hay una parte fiada: elige un cliente (F4).";
            }
            // DECISION: en mixto la parte en efectivo se registra exacta (sin cambio).
            for (FilaPago f : filas) {
                salida.add(new Pago(f.metodo(), f.monto(), f.metodo() == MetodoPago.EFECTIVO ? f.monto() : null, null));
            }
        } else {
            return "Elige un método de pago.";
        }
        return null;
    }

    private static ToggleButton botonMetodo(String texto, String tecla, ToggleGroup grupo) {
        ToggleButton t = new ToggleButton(texto);
        t.getStyleClass().setAll("metodo-pago");
        Label chip = new Label(tecla);
        chip.getStyleClass().add("tecla-chip");
        t.setGraphic(chip);
        t.setToggleGroup(grupo);
        t.setFocusTraversable(false);
        t.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        return t;
    }

    // ================================================================== cliente

    /**
     * Buscar / elegir cliente (F4). Permite alta rápida (nombre y teléfono) sin salir de la venta.
     *
     * @return el cliente elegido, {@link #SIN_CLIENTE} para quitarlo, o vacío si se canceló
     */
    static Optional<Cliente> elegirCliente(Cliente actual) {
        ClienteService servicio = new ClienteService();
        List<Cliente> clientes;
        try {
            clientes = servicio.listarActivos();
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return Optional.empty();
        } catch (PermisoDenegadoException e) {
            Alertas.advertencia(e.getMessage());
            return Optional.empty();
        }

        Dialog<Cliente> d = new Dialog<>();
        d.setTitle("Cliente de la venta");
        ButtonType elegir = new ButtonType("Elegir (Enter)", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(elegir, new ButtonType("Cancelar (Esc)", ButtonBar.ButtonData.CANCEL_CLOSE));
        Button btnElegir = (Button) d.getDialogPane().lookupButton(elegir);

        TextField txt = new TextField();
        txt.setPromptText("Buscar por nombre o teléfono…");
        FilteredList<Cliente> filtrados = new FilteredList<>(FXCollections.observableArrayList(clientes));
        TableView<Cliente> tabla = Tablas.tabla("Sin clientes. Crea uno con \"Nuevo cliente\" (Ins/Ctrl+N).");
        tabla.getColumns().add(Tablas.col("Nombre", 300, Cliente::getNombre));
        tabla.getColumns().add(Tablas.col("Teléfono", 160, c -> c.getTelefono() == null ? "" : c.getTelefono()));
        tabla.getColumns().add(Tablas.colDerecha("Saldo", 150, Cliente::saldoTexto));
        tabla.setItems(filtrados);
        txt.textProperty().addListener((o, a, n) -> {
            filtrados.setPredicate(c -> n.isBlank() || Texto.contieneTodas(c.getNombre() + " "
                    + (c.getTelefono() == null ? "" : c.getTelefono()), n));
            if (!filtrados.isEmpty()) {
                tabla.getSelectionModel().selectFirst();
            }
        });
        if (actual != null) {
            clientes.stream().filter(c -> c.getId() == actual.getId()).findFirst()
                    .ifPresent(c -> tabla.getSelectionModel().select(c));
        } else if (!filtrados.isEmpty()) {
            tabla.getSelectionModel().selectFirst();
        }
        btnElegir.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());

        Cliente[] especial = new Cliente[1];
        Runnable nuevo = () -> {
            TextField nombre = new TextField(txt.getText());
            TextField telefono = new TextField();
            Cliente[] creado = new Cliente[1];
            boolean ok = new Formulario("Nuevo cliente", "Crear (Enter)")
                    .campo("Nombre", nombre)
                    .campo("Teléfono", telefono)
                    .nota("Teléfono opcional. Los demás datos se completan en Fiados / Clientes.")
                    .mostrar(() -> {
                        try {
                            creado[0] = servicio.crearRapido(nombre.getText(), telefono.getText());
                            return null;
                        } catch (IllegalArgumentException | PermisoDenegadoException e) {
                            return e.getMessage();
                        } catch (SQLException e) {
                            Flujo.errorDatos(e);
                            return "No se pudo guardar.";
                        }
                    });
            if (ok) {
                especial[0] = creado[0];
                d.setResult(creado[0]);
                d.close();
            }
        };
        Runnable quitar = () -> {
            especial[0] = SIN_CLIENTE;
            d.setResult(SIN_CLIENTE);
            d.close();
        };
        List<Button> extras = new ArrayList<>();
        extras.add(Acciones.boton("Nuevo cliente", "Ins/Ctrl+N", nuevo));
        if (actual != null) {
            extras.add(Acciones.boton("Quitar cliente de la venta", "Supr", quitar));
        }
        Teclado.atajoNuevo(d.getDialogPane(), nuevo);
        if (actual != null) {
            Acciones.atajo(d.getDialogPane(), new KeyCodeCombination(KeyCode.DELETE), true, quitar);
        }

        txt.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DOWN && !filtrados.isEmpty()) {
                e.consume();
                tabla.requestFocus();
            } else if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                if (!btnElegir.isDisabled()) {
                    btnElegir.fire();
                }
            }
        });
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                if (!btnElegir.isDisabled()) {
                    btnElegir.fire();
                }
            }
        });
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && !btnElegir.isDisabled()) {
                btnElegir.fire();
            }
        });

        Label titulo = new Label("Cliente de la venta");
        titulo.getStyleClass().add("subtitulo");
        HBox barra = new HBox(8);
        barra.getChildren().addAll(extras);
        VBox contenido = new VBox(10, titulo, txt, tabla, barra);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        contenido.setPrefSize(720, 460);
        d.getDialogPane().setContent(contenido);
        d.setResultConverter(b -> {
            if (especial[0] != null) {
                return especial[0];
            }
            return b == elegir ? tabla.getSelectionModel().getSelectedItem() : null;
        });
        Alertas.estilizar(d);
        d.setOnShown(e -> txt.requestFocus());
        return d.showAndWait();
    }

    // ================================================================== descuento

    /** F6: descuento por monto o porcentaje. El cajero necesita autorización de admin. */
    static boolean descuento(Carrito carrito, Negocio config) {
        long subtotal = carrito.subtotal();
        ComboBox<String> cmbTipo = new ComboBox<>(FXCollections.observableArrayList("Porcentaje (%)", "Monto ($)"));
        cmbTipo.getSelectionModel().selectFirst();
        cmbTipo.setMaxWidth(Double.MAX_VALUE);
        Teclado.comboConFlechas(cmbTipo);
        TextField txtValor = new TextField(carrito.getDescuento() == 0 ? "" : Dinero.paraCampo(carrito.getDescuento()));
        if (carrito.getDescuento() > 0) {
            cmbTipo.getSelectionModel().select(1);
        }
        Teclado.campoDinero(txtValor);
        Label lblResultado = new Label();
        lblResultado.getStyleClass().add("subtitulo");

        Runnable vista = () -> {
            try {
                long d = calcularDescuento(cmbTipo.getSelectionModel().getSelectedIndex() == 0, txtValor.getText(), subtotal);
                lblResultado.setText("Descuento " + Dinero.formatear(d) + "  →  Total " + Dinero.formatear(subtotal - d));
            } catch (IllegalArgumentException e) {
                lblResultado.setText("—");
            }
        };
        cmbTipo.valueProperty().addListener((o, a, n) -> vista.run());
        txtValor.textProperty().addListener((o, a, n) -> vista.run());
        vista.run();

        return new Formulario("Descuento", "Aplicar (Enter)")
                .campo("Subtotal", new Label(Dinero.formatear(subtotal)))
                .campo("Tipo", cmbTipo)
                .campo("Valor", txtValor)
                .campo("Resultado", lblResultado)
                .nota("Máximo permitido: " + config.getDescuentoMaximoPct() + "%. Escribe 0 para quitar el descuento."
                        + (Sesion.tiene(Permiso.DESCUENTOS) ? "" : " Se pedirá autorización de un administrador."))
                .mostrar(() -> {
                    long d;
                    try {
                        d = calcularDescuento(cmbTipo.getSelectionModel().getSelectedIndex() == 0, txtValor.getText(),
                                subtotal);
                    } catch (IllegalArgumentException e) {
                        return e.getMessage();
                    }
                    if (d > subtotal) {
                        return "El descuento no puede ser mayor al subtotal.";
                    }
                    if (BigDecimal.valueOf(d).multiply(BigDecimal.valueOf(100))
                            .compareTo(BigDecimal.valueOf(subtotal).multiply(BigDecimal.valueOf(config.getDescuentoMaximoPct()))) > 0) {
                        return "Supera el máximo permitido (" + config.getDescuentoMaximoPct() + "%).";
                    }
                    Autorizacion auth = null;
                    if (d > 0 && !Sesion.tiene(Permiso.DESCUENTOS)) {
                        auth = pedirAutorizacion("Descuento de " + Dinero.formatear(d));
                        if (auth == null) {
                            return "El descuento necesita autorización de un administrador.";
                        }
                    }
                    carrito.setDescuento(d, auth);
                    return null;
                });
    }

    private static long calcularDescuento(boolean porcentaje, String texto, long subtotal) {
        if (texto == null || texto.isBlank()) {
            return 0;
        }
        if (porcentaje) {
            BigDecimal pct = new BigDecimal(texto.trim());
            if (pct.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("El porcentaje no puede pasar de 100.");
            }
            return VentaService.descuentoPorPorcentaje(subtotal, pct);
        }
        return Dinero.parsear(texto);
    }

    // ================================================================== caja

    /** " En la caja debe haber $X." para la nota de retiros y gastos (vacío si no se puede calcular). */
    static String textoEfectivoEnCaja(CajaService caja) {
        try {
            return " En la caja debe haber " + Dinero.formatear(caja.efectivoDisponible())
                    + " (no se puede retirar más).";
        } catch (Exception e) {
            return "";
        }
    }

    /** F8: entrada o retiro de efectivo con concepto. El cajero necesita autorización para retirar. */
    static void movimientoCaja() {
        ComboBox<MovimientoCajaDao.Tipo> cmbTipo = new ComboBox<>(FXCollections.observableArrayList(
                MovimientoCajaDao.Tipo.RETIRO, MovimientoCajaDao.Tipo.ENTRADA));
        cmbTipo.setConverter(convertidor(t -> t == MovimientoCajaDao.Tipo.RETIRO
                ? "Retiro de efectivo (sale dinero)" : "Entrada de efectivo (entra dinero)"));
        cmbTipo.getSelectionModel().selectFirst();
        cmbTipo.setMaxWidth(Double.MAX_VALUE);
        Teclado.comboConFlechas(cmbTipo);
        TextField txtMonto = new TextField();
        Teclado.campoDinero(txtMonto);
        TextField txtConcepto = new TextField();
        txtConcepto.setPromptText("Ej. Pago a proveedor de refrescos");
        CajaService caja = new CajaService();

        boolean ok = new Formulario("Movimiento de caja", "Registrar (Enter)")
                .campo("Tipo", cmbTipo)
                .campo("Monto", txtMonto)
                .campo("Concepto", txtConcepto)
                .nota("Tipo: ↑ ↓ para cambiar. Se refleja en el corte de caja."
                        + (Sesion.tiene(Permiso.RETIROS_CAJA) ? "" : " Los retiros piden autorización de un administrador.")
                        + textoEfectivoEnCaja(caja))
                .mostrar(() -> {
                    try {
                        long monto = txtMonto.getText().isBlank() ? 0 : Dinero.parsear(txtMonto.getText());
                        if (monto <= 0) {
                            return "Escribe el monto.";
                        }
                        if (txtConcepto.getText().isBlank()) {
                            return "Escribe el concepto.";
                        }
                        Autorizacion auth = null;
                        if (cmbTipo.getValue() == MovimientoCajaDao.Tipo.RETIRO && !Sesion.tiene(Permiso.RETIROS_CAJA)) {
                            auth = pedirAutorizacion("Retiro de caja de " + Dinero.formatear(monto));
                            if (auth == null) {
                                return "El retiro necesita autorización de un administrador.";
                            }
                        }
                        caja.registrar(cmbTipo.getValue(), monto, txtConcepto.getText(), auth);
                        return null;
                    } catch (IllegalArgumentException | IllegalStateException | PermisoDenegadoException e) {
                        return e.getMessage();
                    } catch (SQLException e) {
                        Flujo.errorDatos(e);
                        return "No se pudo guardar.";
                    }
                });
        if (ok) {
            Alertas.toast("Movimiento de caja registrado");
        }
    }

    // ================================================================== autorización

    /** Diálogo "Autorización de administrador". @return la autorización, o null si se canceló. */
    static Autorizacion pedirAutorizacion(String accion) {
        TextField txtUsuario = new TextField();
        PasswordField txtPassword = new PasswordField();
        Autorizacion[] auth = new Autorizacion[1];
        new Formulario("Autorización de administrador", "Autorizar (Enter)")
                .campo("Acción", new Label(accion))
                .campo("Usuario admin", txtUsuario)
                .campo("Contraseña", txtPassword)
                .nota("Se permite solo esta acción y queda registrado quién autorizó.")
                .mostrar(() -> {
                    try {
                        auth[0] = AuthService.instancia().autorizarAdmin(txtUsuario.getText(), txtPassword.getText(), accion);
                        return null;
                    } catch (IllegalArgumentException e) {
                        txtPassword.clear();
                        txtPassword.requestFocus();
                        return e.getMessage();
                    } catch (SQLException e) {
                        Flujo.errorDatos(e);
                        return "No se pudo validar.";
                    }
                });
        return auth[0];
    }

    // ================================================================== ventas del turno

    /**
     * F11: ventas del turno → detalle → cancelar.
     *
     * @return true si se canceló alguna venta (para refrescar stock)
     */
    static boolean ventasDelTurno() {
        VentaService servicio = new VentaService();
        ObservableList<Venta.Resumen> lista = FXCollections.observableArrayList();
        Label pie = new Label();
        pie.getStyleClass().add("pie-tabla");
        Runnable cargar = () -> {
            try {
                lista.setAll(servicio.ventasDelTurno());
                long total = lista.stream().filter(v -> !v.cancelada()).mapToLong(Venta.Resumen::total).sum();
                long canceladas = lista.stream().filter(Venta.Resumen::cancelada).count();
                pie.setText(lista.size() + " ventas · Total vendido " + Dinero.formatear(total)
                        + (canceladas > 0 ? " · " + canceladas + " canceladas (no suman)" : ""));
            } catch (SQLException e) {
                Flujo.errorDatos(e);
            }
        };
        cargar.run();

        TableView<Venta.Resumen> tabla = Tablas.tabla("Aún no hay ventas en este turno");
        tabla.getColumns().add(Tablas.colDerecha("Folio", 70, v -> "#" + v.folio()));
        tabla.getColumns().add(Tablas.col("Hora", 70, v -> Fechas.HORA.format(v.fecha())));
        tabla.getColumns().add(Tablas.col("Vendió", 140, Venta.Resumen::usuarioNombre));
        tabla.getColumns().add(Tablas.col("Cliente", 160, v -> v.clienteNombre() == null ? "" : v.clienteNombre()));
        tabla.getColumns().add(Tablas.col("Pago", 170, Venta.Resumen::metodos));
        tabla.getColumns().add(Tablas.colDerecha("Total", 110, v -> Dinero.formatear(v.total())));
        tabla.getColumns().add(Tablas.col("Estado", 110, v -> v.cancelada() ? "CANCELADA" : "Completada"));
        tabla.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Venta.Resumen v, boolean vacio) {
                super.updateItem(v, vacio);
                getStyleClass().remove("cancelada");
                if (!vacio && v != null && v.cancelada()) {
                    getStyleClass().add("cancelada");
                }
            }
        });
        tabla.setItems(lista);

        boolean[] huboCancelacion = new boolean[1];
        Runnable verDetalle = () -> {
            Venta.Resumen sel = tabla.getSelectionModel().getSelectedItem();
            if (sel != null && detalleVenta(servicio, sel, true)) {
                huboCancelacion[0] = true;
                cargar.run();
            }
            tabla.requestFocus();
        };
        Runnable cancelarDirecto = () -> {
            Venta.Resumen sel = tabla.getSelectionModel().getSelectedItem();
            if (sel != null && !sel.cancelada() && cancelarVenta(servicio, sel)) {
                huboCancelacion[0] = true;
                cargar.run();
            }
            tabla.requestFocus();
        };
        Runnable devolver = () -> {
            Venta.Resumen sel = tabla.getSelectionModel().getSelectedItem();
            if (sel != null && DevolucionDialogos.devolver(sel.id(), "#" + sel.folio())) {
                huboCancelacion[0] = true; // el stock cambió
                cargar.run();
            }
            tabla.requestFocus();
        };
        Runnable devolverPorFolio = () -> {
            if (DevolucionDialogos.porFolio()) {
                huboCancelacion[0] = true;
                cargar.run();
            }
            tabla.requestFocus();
        };
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                verDetalle.run();
            } else if (e.getCode() == KeyCode.C && !e.isControlDown()) {
                e.consume();
                cancelarDirecto.run();
            } else if (e.getCode() == KeyCode.D && !e.isControlDown()) {
                e.consume();
                if (e.isShiftDown()) {
                    devolverPorFolio.run();
                } else {
                    devolver.run();
                }
            }
        });
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                verDetalle.run();
            }
        });
        Tablas.dialogo("Ventas del turno", null, tabla, pie, 1000, 560, List.of(
                Acciones.boton("Ver detalle", "Enter", verDetalle),
                Acciones.boton("Cancelar venta", "C", cancelarDirecto),
                Acciones.boton("Devolver productos", "D", devolver),
                Acciones.boton("Devolución por folio", "Shift+D", devolverPorFolio)));
        return huboCancelacion[0];
    }

    /** Detalle de una venta de solo lectura (p. ej. desde el estado de cuenta de un cliente). */
    static void verVenta(long ventaId) {
        VentaService servicio = new VentaService();
        try {
            detalleVenta(servicio, servicio.resumen(ventaId), false);
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        } catch (IllegalArgumentException | PermisoDenegadoException e) {
            Alertas.advertencia(e.getMessage());
        }
    }

    /** Detalle de una venta. @return true si se canceló desde aquí. */
    private static boolean detalleVenta(VentaService servicio, Venta.Resumen v, boolean permitirCancelar) {
        List<Venta.Renglon> renglones;
        List<Venta.Pago> pagos;
        try {
            renglones = servicio.renglones(v.id());
            pagos = servicio.pagos(v.id());
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return false;
        }
        TableView<Venta.Renglon> tabla = Tablas.tabla("Sin productos");
        tabla.getColumns().add(Tablas.col("Código", 120, Venta.Renglon::codigo));
        tabla.getColumns().add(Tablas.col("Producto", 280, Venta.Renglon::nombre));
        tabla.getColumns().add(Tablas.colDerecha("Cantidad", 120, Venta.Renglon::cantidadTexto));
        tabla.getColumns().add(Tablas.colDerecha("Precio", 110, r -> Dinero.formatear(r.precioUnitario())));
        tabla.getColumns().add(Tablas.colDerecha("Importe", 110, r -> Dinero.formatear(r.importe())));
        tabla.setItems(FXCollections.observableArrayList(renglones));

        StringBuilder sb = new StringBuilder();
        for (Venta.Pago p : pagos) {
            sb.append(sb.length() == 0 ? "" : "   ·   ").append(p.metodo().etiqueta()).append(" ")
                    .append(Dinero.formatear(p.monto()));
            if (p.recibido() != null && p.cambio() != null && p.cambio() > 0) {
                sb.append(" (recibió ").append(Dinero.formatear(p.recibido())).append(", cambio ")
                        .append(Dinero.formatear(p.cambio())).append(")");
            }
            if (p.referencia() != null) {
                sb.append(" ref. ").append(p.referencia());
            }
        }
        Label encabezado = new Label(Fechas.FECHA_HORA.format(v.fecha()) + " · Vendió " + v.usuarioNombre()
                + (v.clienteNombre() == null ? "" : " · Cliente " + v.clienteNombre())
                + (v.cancelada() ? "\nCANCELADA · Motivo: " + v.motivoCancelacion() : ""));
        encabezado.setWrapText(true);
        encabezado.getStyleClass().add(v.cancelada() ? "texto-peligro" : "texto-secundario");
        Label pie = new Label("Pagos: " + sb + "\nTotal: " + Dinero.formatear(v.total()));
        pie.getStyleClass().add("subtitulo");
        pie.setWrapText(true);

        boolean[] cancelada = new boolean[1];
        List<Button> extras = new ArrayList<>();
        if (permitirCancelar && !v.cancelada()) {
            Button btn = Acciones.boton("Cancelar esta venta", "C", () -> { });
            btn.getStyleClass().add("boton-peligro");
            Runnable accion = () -> {
                if (!cancelada[0] && cancelarVenta(servicio, v)) {
                    cancelada[0] = true;
                    btn.setDisable(true);
                    encabezado.setText(encabezado.getText() + "\nCANCELADA");
                    encabezado.getStyleClass().setAll("label", "texto-peligro");
                }
            };
            btn.setOnAction(e -> accion.run());
            tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.C && !e.isControlDown()) {
                    e.consume();
                    accion.run();
                }
            });
            extras.add(btn);
        }
        Tablas.dialogo("Venta #" + v.folio(), encabezado, tabla, pie, 900, 520, extras);
        return cancelada[0];
    }

    /** Flujo de cancelación (24-cancelaciones.md). @return true si se canceló. */
    private static boolean cancelarVenta(VentaService servicio, Venta.Resumen v) {
        Autorizacion auth = null;
        if (!Sesion.tiene(Permiso.CANCELAR_VENTA)) {
            auth = pedirAutorizacion("Cancelar venta #" + v.folio() + " (" + Dinero.formatear(v.total()) + ")");
            if (auth == null) {
                return false;
            }
        }
        List<String> motivos = List.of("1 · Error de captura", "2 · Cliente se arrepintió", "3 · Producto defectuoso",
                "4 · Otro");
        ComboBox<String> cmbMotivo = new ComboBox<>(FXCollections.observableArrayList(motivos));
        cmbMotivo.getSelectionModel().selectFirst();
        cmbMotivo.setMaxWidth(Double.MAX_VALUE);
        Teclado.comboConFlechas(cmbMotivo);
        TextField txtDetalle = new TextField();
        txtDetalle.setPromptText("Obligatorio si eliges \"Otro\"");
        String[] motivo = new String[1];
        Formulario f = new Formulario("Cancelar venta #" + v.folio(), "Continuar (Enter)")
                .campo("Total", new Label(Dinero.formatear(v.total())))
                .campo("Motivo", cmbMotivo)
                .campo("Detalle", txtDetalle)
                .nota("Teclas 1 a 4 eligen el motivo.");
        for (int i = 1; i <= 4; i++) {
            final int indice = i - 1;
            f.atajo(new KeyCodeCombination(KeyCode.valueOf("DIGIT" + i)), () -> cmbMotivo.getSelectionModel().select(indice));
            f.atajo(new KeyCodeCombination(KeyCode.valueOf("NUMPAD" + i)), () -> cmbMotivo.getSelectionModel().select(indice));
        }
        boolean ok = f.mostrar(() -> {
            String base = cmbMotivo.getValue().substring(4);
            String detalle = txtDetalle.getText().trim();
            if (base.equals("Otro") && detalle.isEmpty()) {
                return "Escribe el detalle del motivo.";
            }
            motivo[0] = detalle.isEmpty() ? base : base + ": " + detalle;
            return null;
        });
        if (!ok) {
            return false;
        }
        if (!Alertas.confirmarPeligro("¿Cancelar la venta #" + v.folio() + " por " + Dinero.formatear(v.total()) + "?\n"
                + "Se regresará el stock y, si fue fiada, se descontará del saldo del cliente.\n"
                + "Esta acción no se puede deshacer.")) {
            return false;
        }
        try {
            VentaService.Cancelacion c = servicio.cancelar(v.id(), motivo[0], auth);
            Alertas.toast("Venta #" + c.folio() + " cancelada");
            if (c.saldoAFavor()) {
                Alertas.info("Saldo a favor", "El cliente ya había abonado: quedó con saldo a favor de "
                        + Dinero.formatear(-c.saldoCliente()) + ".");
            }
            return true;
        } catch (IllegalArgumentException | PermisoDenegadoException e) {
            Alertas.advertencia(e.getMessage());
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        }
        return false;
    }

    // ================================================================== utilidades

    private static Label etiqueta(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("texto-secundario");
        return l;
    }

    private static Label etiquetaPequena(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("texto-pequeno");
        l.setWrapText(true);
        return l;
    }

    private static <T> StringConverter<T> convertidor(java.util.function.Function<T, String> texto) {
        return new StringConverter<>() {
            @Override
            public String toString(T t) {
                return t == null ? "" : texto.apply(t);
            }

            @Override
            public T fromString(String s) {
                return null;
            }
        };
    }
}
