package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.dao.MovimientoCajaDao.Movimiento;
import com.fgsistemas.pos.dao.MovimientoCajaDao.Tipo;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.service.Autorizacion;
import com.fgsistemas.pos.service.CajaService;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Sesion;
import com.fgsistemas.pos.util.Teclado;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.List;

/**
 * Gastos e ingresos de caja del turno (pedido del cliente después de la Fase 7).
 * Usa los mismos movimientos de caja que F8 en Vender: un gasto es un RETIRO y un ingreso una ENTRADA,
 * y los dos entran al corte. Ninguno se borra: un error se corrige con el movimiento contrario.
 */
public class GastosController {

    private static final String OTRO_GASTO = "Otro gasto";
    private static final String OTRO_INGRESO = "Otro ingreso";
    private static final List<String> CATEGORIAS_GASTO = List.of("Pago a proveedor", "Servicios (luz, agua, internet)",
            "Renta", "Sueldos o pagos a personal", "Insumos o limpieza del negocio", "Comida", "Retiro del dueño",
            "Devolución de dinero a un cliente", OTRO_GASTO);
    private static final List<String> CATEGORIAS_INGRESO = List.of("Cambio o morralla", "Aportación del dueño",
            "Cobro de un servicio", "Reposición de un gasto", OTRO_INGRESO);

    @FXML private BorderPane raiz;
    @FXML private FlowPane cajaTotales;
    @FXML private TableView<Movimiento> tabla;
    @FXML private FlowPane barraAcciones;

    private final CajaService servicio = new CajaService();

    @FXML
    private void initialize() {
        configurarTabla();
        Acciones.agregar(barraAcciones, raiz, "Nuevo gasto", "G", new KeyCodeCombination(KeyCode.G), true,
                () -> nuevo(Tipo.RETIRO));
        Acciones.agregar(barraAcciones, raiz, "Nuevo ingreso", "I", new KeyCodeCombination(KeyCode.I), true,
                () -> nuevo(Tipo.ENTRADA));
        Acciones.espaciador(barraAcciones);
        Acciones.agregar(barraAcciones, raiz, "Menú", "Esc", new KeyCodeCombination(KeyCode.ESCAPE), false,
                Flujo::irAlMenu);
        cargar();
        Teclado.enfocar(tabla);
    }

    private void configurarTabla() {
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.setPlaceholder(new Label("Sin gastos ni ingresos en este turno. Registra uno con G o I."));
        tabla.getColumns().add(Tablas.col("Hora", 90, m -> Fechas.HORA.format(m.fecha())));
        tabla.getColumns().add(Tablas.col("Tipo", 120, m -> m.tipo() == Tipo.RETIRO ? "Gasto" : "Ingreso"));
        tabla.getColumns().add(Tablas.col("Concepto", 420, Movimiento::concepto));
        tabla.getColumns().add(Tablas.col("Registró", 180, Movimiento::usuarioNombre));
        TableColumn<Movimiento, Movimiento> monto = new TableColumn<>("Monto");
        monto.setPrefWidth(140);
        monto.getStyleClass().add("dinero");
        monto.setCellValueFactory(f -> new ReadOnlyObjectWrapper<>(f.getValue()));
        monto.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Movimiento m, boolean vacio) {
                super.updateItem(m, vacio);
                getStyleClass().removeAll("saldo-deudor", "saldo-favor");
                if (vacio || m == null) {
                    setText(null);
                    return;
                }
                setText((m.tipo() == Tipo.RETIRO ? "− " : "+ ") + Dinero.formatear(m.monto()));
                getStyleClass().add(m.tipo() == Tipo.RETIRO ? "saldo-deudor" : "saldo-favor");
            }
        });
        tabla.getColumns().add(monto);
    }

    private void cargar() {
        List<Movimiento> lista;
        try {
            lista = servicio.movimientosTurno();
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        } catch (PermisoDenegadoException e) {
            Alertas.error(e.getMessage());
            return;
        }
        tabla.setItems(FXCollections.observableArrayList(lista));
        if (!lista.isEmpty()) {
            tabla.getSelectionModel().selectLast();
            tabla.scrollTo(lista.size() - 1);
        }
        long gastos = lista.stream().filter(m -> m.tipo() == Tipo.RETIRO).mapToLong(Movimiento::monto).sum();
        long ingresos = lista.stream().filter(m -> m.tipo() == Tipo.ENTRADA).mapToLong(Movimiento::monto).sum();
        cajaTotales.getChildren().setAll(
                tarjeta("Gastos del turno", "− " + Dinero.formatear(gastos), "salen de la caja"),
                tarjeta("Ingresos del turno", "+ " + Dinero.formatear(ingresos), "entran a la caja"),
                tarjeta("Neto", Dinero.formatear(ingresos - gastos), "ingresos − gastos"));
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

    /** Nuevo gasto o ingreso: categoría (teclas 1–9), monto y detalle. */
    private void nuevo(Tipo tipo) {
        boolean gasto = tipo == Tipo.RETIRO;
        List<String> categorias = gasto ? CATEGORIAS_GASTO : CATEGORIAS_INGRESO;
        String otro = gasto ? OTRO_GASTO : OTRO_INGRESO;
        ComboBox<String> cmb = new ComboBox<>(FXCollections.observableArrayList(numeradas(categorias)));
        cmb.getSelectionModel().selectFirst();
        cmb.setMaxWidth(Double.MAX_VALUE);
        Teclado.comboConFlechas(cmb);
        TextField txtMonto = new TextField();
        Teclado.campoDinero(txtMonto);
        TextField txtDetalle = new TextField();
        txtDetalle.setPromptText(gasto ? "Ej. Coca-Cola, recibo de luz de noviembre" : "Ej. Cambio que trajo el dueño");

        Formulario f = new Formulario(gasto ? "Nuevo gasto (sale dinero de la caja)" : "Nuevo ingreso (entra dinero a la caja)",
                "Registrar (Enter)")
                .campo("Categoría", cmb)
                .campo("Monto", txtMonto)
                .campo("Detalle", txtDetalle)
                .nota("Categoría: ↑ ↓ o teclas 1–" + categorias.size() + ". El detalle es obligatorio en \"" + otro + "\"."
                        + (gasto && !Sesion.tiene(Permiso.RETIROS_CAJA)
                        ? " Los gastos piden autorización de un administrador." : "")
                        + (gasto ? VentaDialogos.textoEfectivoEnCaja(servicio) : ""));
        // Teclas 1–9 eligen la categoría solo con el foco en ella (en Monto los números se escriben).
        cmb.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            String t = e.getText();
            if (t != null && t.length() == 1 && Character.isDigit(t.charAt(0))) {
                int indice = t.charAt(0) - '1';
                if (indice >= 0 && indice < categorias.size()) {
                    e.consume();
                    cmb.getSelectionModel().select(indice);
                }
            }
        });
        boolean ok = f.mostrar(() -> {
            try {
                long monto = txtMonto.getText().isBlank() ? 0 : Dinero.parsear(txtMonto.getText());
                if (monto <= 0) {
                    return "Escribe el monto.";
                }
                String categoria = categorias.get(cmb.getSelectionModel().getSelectedIndex());
                String detalle = txtDetalle.getText().trim();
                if (categoria.equals(otro) && detalle.isEmpty()) {
                    return "Escribe el detalle.";
                }
                String concepto = detalle.isEmpty() ? categoria : categoria + ": " + detalle;
                Autorizacion auth = null;
                if (gasto && !Sesion.tiene(Permiso.RETIROS_CAJA)) {
                    auth = VentaDialogos.pedirAutorizacion("Gasto de " + Dinero.formatear(monto) + " · " + concepto);
                    if (auth == null) {
                        return "El gasto necesita autorización de un administrador.";
                    }
                }
                servicio.registrar(tipo, monto, concepto, auth);
                return null;
            } catch (IllegalArgumentException | IllegalStateException | PermisoDenegadoException e) {
                return e.getMessage();
            } catch (SQLException e) {
                Flujo.errorDatos(e);
                return "No se pudo guardar.";
            }
        });
        if (ok) {
            Alertas.toast(gasto ? "Gasto registrado" : "Ingreso registrado");
            cargar();
        }
        tabla.requestFocus();
    }

    private static List<String> numeradas(List<String> categorias) {
        java.util.ArrayList<String> l = new java.util.ArrayList<>();
        for (int i = 0; i < categorias.size(); i++) {
            l.add((i + 1) + " · " + categorias.get(i));
        }
        return l;
    }
}
