package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.service.Autorizacion;
import com.fgsistemas.pos.service.DevolucionService;
import com.fgsistemas.pos.service.DevolucionService.Devolvible;
import com.fgsistemas.pos.service.DevolucionService.Linea;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Sesion;
import com.fgsistemas.pos.util.Teclado;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Devolución parcial (24-cancelaciones.md, Fase 9): elegir qué productos regresan y cuánto, ver el reembolso
 * en vivo, motivo y confirmación. El reembolso sale en efectivo de la caja del turno actual.
 */
final class DevolucionDialogos {

    private static final List<String> MOTIVOS = List.of("1 · Producto defectuoso", "2 · Cliente se arrepintió",
            "3 · Error de captura", "4 · Otro");

    private DevolucionDialogos() {
    }

    /** Pide el folio de cualquier venta (también de turnos ya cortados) y abre su devolución. */
    static boolean porFolio() {
        TextField txtFolio = new TextField();
        Teclado.soloNumeros(txtFolio, 0);
        long[] ventaId = new long[1];
        DevolucionService servicio = new DevolucionService();
        boolean ok = new Formulario("Devolución de una venta", "Buscar (Enter)")
                .campo("Folio de la venta", txtFolio)
                .nota("El folio viene en el ticket o en Cortes pasados. El reembolso sale de la caja de este turno.")
                .mostrar(() -> {
                    if (txtFolio.getText().isBlank()) {
                        return "Escribe el folio.";
                    }
                    try {
                        ventaId[0] = servicio.ventaPorFolio(Long.parseLong(txtFolio.getText()));
                        return null;
                    } catch (NumberFormatException e) {
                        return "Folio inválido.";
                    } catch (IllegalArgumentException | PermisoDenegadoException e) {
                        return e.getMessage();
                    } catch (SQLException e) {
                        Flujo.errorDatos(e);
                        return "No se pudo buscar la venta.";
                    }
                });
        return ok && devolver(ventaId[0], "#" + txtFolio.getText());
    }

    /** @return true si se registró una devolución */
    static boolean devolver(long ventaId, String folioTexto) {
        DevolucionService servicio = new DevolucionService();
        List<Devolvible> productos;
        try {
            productos = servicio.devolvibles(ventaId);
        } catch (IllegalArgumentException | PermisoDenegadoException e) {
            Alertas.advertencia(e.getMessage());
            return false;
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return false;
        }
        if (productos.stream().allMatch(p -> p.disponible() <= 0)) {
            Alertas.advertencia("Ya se devolvió todo lo de la venta " + folioTexto + ".");
            return false;
        }

        Map<Long, Long> elegido = new HashMap<>();
        TableView<Devolvible> tabla = Tablas.tabla("Sin productos");
        tabla.getColumns().add(Tablas.col("Producto", 280, Devolvible::nombre));
        tabla.getColumns().add(Tablas.colDerecha("Vendido", 110, d -> Cantidad.formatear(d.vendido(), d.tipo(), d.unidad())));
        tabla.getColumns().add(Tablas.colDerecha("Ya devuelto", 110,
                d -> d.devuelto() == 0 ? "—" : Cantidad.formatear(d.devuelto(), d.tipo(), d.unidad())));
        tabla.getColumns().add(Tablas.colDerecha("Devolver", 110, d -> {
            long q = elegido.getOrDefault(d.productoId(), 0L);
            return q == 0 ? "" : Cantidad.formatear(q, d.tipo(), d.unidad());
        }));
        tabla.setItems(FXCollections.observableArrayList(productos));

        Label lblReembolso = new Label();
        lblReembolso.getStyleClass().add("subtitulo");
        Runnable actualizar = () -> {
            tabla.refresh();
            List<Linea> lineas = lineas(elegido);
            if (lineas.isEmpty()) {
                lblReembolso.setText("Reembolso en efectivo: $0.00   (elige productos con Enter)");
                return;
            }
            try {
                lblReembolso.setText("Reembolso en efectivo: " + Dinero.formatear(servicio.reembolso(ventaId, lineas)));
            } catch (IllegalArgumentException e) {
                lblReembolso.setText(e.getMessage());
            } catch (SQLException e) {
                lblReembolso.setText("—");
            }
        };
        actualizar.run();

        Runnable cantidad = () -> {
            Devolvible d = tabla.getSelectionModel().getSelectedItem();
            if (d == null) {
                return;
            }
            if (d.disponible() <= 0) {
                Alertas.advertencia("Ya se devolvió todo \"" + d.nombre() + "\".");
                return;
            }
            pedirCantidad(d, elegido);
            actualizar.run();
            tabla.requestFocus();
        };
        Runnable todo = () -> {
            for (Devolvible d : productos) {
                if (d.disponible() > 0) {
                    elegido.put(d.productoId(), d.disponible());
                }
            }
            actualizar.run();
        };
        boolean[] hecho = new boolean[1];
        Runnable confirmar = () -> {
            List<Linea> lineas = lineas(elegido);
            if (lineas.isEmpty()) {
                Alertas.advertencia("Elige qué productos se devuelven (Enter sobre el producto, o T para todo).");
                return;
            }
            if (registrar(servicio, ventaId, folioTexto, lineas)) {
                hecho[0] = true;
                Window w = tabla.getScene().getWindow();
                w.hide();
            }
        };
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                cantidad.run();
            } else if (e.getCode() == KeyCode.T) {
                e.consume();
                todo.run();
            } else if (e.getCode() == KeyCode.F10) {
                e.consume();
                confirmar.run();
            }
        });
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                cantidad.run();
            }
        });
        Button btnDevolver = Acciones.boton("Devolver", "F10", confirmar);
        btnDevolver.getStyleClass().add("boton-principal");
        Label ayuda = new Label("El reembolso es proporcional a lo que se cobró (incluye el descuento de la venta) y sale "
                + "en efectivo de la caja de este turno.");
        ayuda.getStyleClass().add("texto-secundario");
        ayuda.setWrapText(true);
        Tablas.dialogo("Devolución de la venta " + folioTexto, ayuda, tabla, lblReembolso, 860, 520, List.of(
                Acciones.boton("Cantidad a devolver", "Enter", cantidad),
                Acciones.boton("Devolver todo", "T", todo),
                btnDevolver));
        return hecho[0];
    }

    private static void pedirCantidad(Devolvible d, Map<Long, Long> elegido) {
        TextField txt = new TextField(Cantidad.paraCampo(elegido.getOrDefault(d.productoId(), d.disponible()), d.tipo()));
        Teclado.soloNumeros(txt, d.tipo().aceptaDecimales() ? 3 : 0);
        new Formulario("¿Cuánto se devuelve?", "Aceptar (Enter)")
                .campo("Producto", new Label(d.nombre()))
                .campo("Se puede devolver", new Label(Cantidad.formatear(d.disponible(), d.tipo(), d.unidad())))
                .campo("Devolver (" + d.unidad() + ")", txt)
                .nota("Escribe 0 para no devolver este producto.")
                .mostrar(() -> {
                    try {
                        long q = txt.getText().isBlank() ? 0 : Cantidad.parsear(txt.getText(), d.tipo());
                        if (q > d.disponible()) {
                            return "Solo se pueden devolver " + Cantidad.formatear(d.disponible(), d.tipo(), d.unidad())
                                    + ".";
                        }
                        if (q == 0) {
                            elegido.remove(d.productoId());
                        } else {
                            elegido.put(d.productoId(), q);
                        }
                        return null;
                    } catch (IllegalArgumentException e) {
                        return e.getMessage();
                    }
                });
    }

    private static boolean registrar(DevolucionService servicio, long ventaId, String folioTexto, List<Linea> lineas) {
        long total;
        try {
            total = servicio.reembolso(ventaId, lineas);
        } catch (IllegalArgumentException | PermisoDenegadoException e) {
            Alertas.advertencia(e.getMessage());
            return false;
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return false;
        }
        Autorizacion auth = null;
        if (!Sesion.tiene(Permiso.CANCELAR_VENTA)) {
            auth = VentaDialogos.pedirAutorizacion("Devolución de la venta " + folioTexto + " ("
                    + Dinero.formatear(total) + ")");
            if (auth == null) {
                return false;
            }
        }
        ComboBox<String> cmbMotivo = new ComboBox<>(FXCollections.observableArrayList(MOTIVOS));
        cmbMotivo.getSelectionModel().selectFirst();
        cmbMotivo.setMaxWidth(Double.MAX_VALUE);
        Teclado.comboConFlechas(cmbMotivo);
        TextField txtDetalle = new TextField();
        txtDetalle.setPromptText("Obligatorio si eliges \"Otro\"");
        String[] motivo = new String[1];
        Formulario f = new Formulario("Motivo de la devolución", "Continuar (Enter)")
                .campo("Reembolso", new Label(Dinero.formatear(total) + " en efectivo"))
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
        if (!ok || !Alertas.confirmarPeligro("¿Registrar la devolución de la venta " + folioTexto + "?\n\n"
                + "Regresan " + lineas.size() + (lineas.size() == 1 ? " producto" : " productos") + " al inventario y "
                + "se entregan " + Dinero.formatear(total) + " en efectivo al cliente.\nNo se puede deshacer.")) {
            return false;
        }
        try {
            DevolucionService.Resultado r = servicio.devolver(ventaId, lineas, motivo[0], auth);
            Alertas.info("Devolución registrada", "Devolución #" + r.devolucionId() + " de la venta #" + r.folio()
                    + ".\n\nEntrega al cliente " + Dinero.formatear(r.total()) + " en efectivo.");
            return true;
        } catch (IllegalArgumentException | IllegalStateException | PermisoDenegadoException e) {
            Alertas.advertencia(e.getMessage());
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        }
        return false;
    }

    private static List<Linea> lineas(Map<Long, Long> elegido) {
        List<Linea> l = new ArrayList<>();
        elegido.forEach((id, q) -> {
            if (q > 0) {
                l.add(new Linea(id, q));
            }
        });
        return l;
    }
}
