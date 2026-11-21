package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Turno;
import com.fgsistemas.pos.dao.VentaDao.Totales;
import com.fgsistemas.pos.service.InventarioService;
import com.fgsistemas.pos.service.ResumenService;
import com.fgsistemas.pos.service.TurnoService;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Navegador;
import com.fgsistemas.pos.util.Sesion;
import com.fgsistemas.pos.util.Teclado;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Menú principal con botones grandes según permisos (13-menu-principal.md). */
public class MenuController {

    private static final Logger LOG = Logger.getLogger(MenuController.class.getName());
    private static final int COLUMNAS = 4;

    /** Opción del menú. {@code fase} > 0 = aún no construida (muestra "próximamente"). */
    private record Opcion(int numero, String texto, String icono, Permiso permiso, String vista, int fase) {
    }

    private static final List<Opcion> OPCIONES = List.of(
            new Opcion(1, "Vender", "mdi2c-cart", Permiso.VENDER, "vender", 0),
            new Opcion(2, "Inventario", "mdi2p-package-variant-closed", Permiso.VER_INVENTARIO, "inventario", 0),
            new Opcion(3, "Entrada de mercancía", "mdi2t-truck-delivery", Permiso.ENTRADA_MERCANCIA, "entrada", 0),
            new Opcion(4, "Resumen del día", "mdi2c-chart-bar", Permiso.VER_RESUMEN_DIA, "resumen", 0),
            new Opcion(5, "Corte de caja", "mdi2c-cash-register", Permiso.HACER_CORTE, "corte", 0),
            new Opcion(6, "Cortes pasados", "mdi2h-history", Permiso.VER_CORTES_PASADOS, "cortes", 0),
            new Opcion(7, "Fiados / Clientes", "mdi2a-account-cash", Permiso.FIADOS_VENDER, "fiados", 0),
            new Opcion(8, "Usuarios", "mdi2a-account-multiple", Permiso.USUARIOS, "usuarios", 0),
            new Opcion(9, "Configuración", "mdi2c-cog", Permiso.CONFIGURACION, null, 8),
            // Pedido del cliente tras la Fase 7: F10 o 0.
            new Opcion(10, "Gastos e ingresos", "mdi2c-cash-multiple", Permiso.VENDER, "gastos", 0));

    @FXML private BorderPane raiz;
    @FXML private HBox bandaAviso;
    @FXML private Label lblAviso;
    @FXML private FlowPane grid;
    @FXML private FlowPane barraAcciones;
    @FXML private Label lblVendidoTurno;
    @FXML private Label lblVentasTurno;
    @FXML private VBox cajaHoy;
    @FXML private Label lblVendidoHoy;
    @FXML private Label lblVentasHoy;

    private final List<Button> botones = new ArrayList<>();
    private final List<Opcion> visibles = new ArrayList<>();

    @FXML
    private void initialize() {
        for (Opcion op : OPCIONES) {
            if (Sesion.tiene(op.permiso())) {
                visibles.add(op);
                grid.getChildren().add(crearBoton(op));
            }
        }
        mostrarAvisoTurno();
        mostrarContador();
        registrarAtajos();
        Acciones.espaciador(barraAcciones);
        barraAcciones.getChildren().add(Acciones.boton("Cerrar sesión", "F12", Flujo::cerrarSesion));
        if (!botones.isEmpty()) {
            Teclado.enfocar(botones.get(0)); // "Vender"
        }
    }

    private Node crearBoton(Opcion op) {
        Button b = new Button(op.texto());
        b.getStyleClass().add("boton-menu");
        b.setWrapText(true);
        b.setGraphic(icono(op.icono()));
        b.setOnAction(e -> abrir(op));
        Teclado.enterDispara(b);
        botones.add(b);

        Label atajo = new Label("F" + op.numero());
        atajo.getStyleClass().add("atajo-boton");
        atajo.setMouseTransparent(true);
        StackPane.setAlignment(atajo, Pos.TOP_RIGHT);
        StackPane.setMargin(atajo, new javafx.geometry.Insets(10, 14, 0, 0));

        StackPane celda = new StackPane(b, atajo);
        if (op.numero() == 2 && Sesion.esAdmin()) {
            agregarInsigniaStockBajo(celda);
        }
        return celda;
    }

    private static Node icono(String literal) {
        try {
            FontIcon i = new FontIcon(literal);
            i.getStyleClass().add("icono-menu");
            return i;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Ícono no encontrado: " + literal, e);
            return null;
        }
    }

    private void agregarInsigniaStockBajo(StackPane celda) {
        try {
            int bajos = new InventarioService().contarStockBajo();
            if (bajos > 0) {
                Label insignia = new Label("⚠ " + bajos);
                insignia.getStyleClass().add("insignia");
                insignia.setMouseTransparent(true);
                StackPane.setAlignment(insignia, Pos.TOP_LEFT);
                StackPane.setMargin(insignia, new javafx.geometry.Insets(10, 0, 0, 12));
                celda.getChildren().add(insignia);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "No se pudo contar el stock bajo", e);
        }
    }

    private void mostrarAvisoTurno() {
        Turno t = Sesion.turno();
        if (t != null && TurnoService.esDeDiaAnterior(t, Fechas.ahora().toLocalDate())) {
            lblAviso.setText("⚠  Este turno está abierto desde el " + Fechas.FECHA_HORA.format(t.getAbiertoEn())
                    + ". Se recomienda hacer corte de caja (F5).");
            bandaAviso.setVisible(true);
            bandaAviso.setManaged(true);
        }
    }

    private void mostrarContador() {
        try {
            ResumenService resumen = new ResumenService();
            Totales turno = resumen.vendidoTurno();
            lblVendidoTurno.setText(Dinero.formatear(turno.total()));
            lblVentasTurno.setText(textoVentas(turno.ventas()));
            if (Sesion.tiene(Permiso.VER_CORTES_PASADOS)) {
                Totales hoy = resumen.vendidoHoy();
                lblVendidoHoy.setText(Dinero.formatear(hoy.total()));
                lblVentasHoy.setText(textoVentas(hoy.ventas()));
                cajaHoy.setVisible(true);
                cajaHoy.setManaged(true);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "No se pudo calcular lo vendido", e);
        }
    }

    private static String textoVentas(int n) {
        return n == 1 ? "1 venta" : n + " ventas";
    }

    private void registrarAtajos() {
        raiz.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            int numero = numeroDeTecla(e.getCode());
            if (numero > 0 && !e.isControlDown() && !e.isAltDown()) {
                e.consume();
                // Atajo sin permiso: se ignora.
                visibles.stream().filter(op -> op.numero() == numero).findFirst().ifPresent(this::abrir);
                return;
            }
            switch (e.getCode()) {
                case LEFT -> mover(e, -1);
                case RIGHT -> mover(e, 1);
                case UP -> mover(e, -COLUMNAS);
                case DOWN -> mover(e, COLUMNAS);
                case F12 -> {
                    e.consume();
                    Flujo.cerrarSesion();
                }
                default -> {
                }
            }
        });
    }

    private static int numeroDeTecla(KeyCode code) {
        for (int n = 1; n <= 9; n++) {
            if (code == KeyCode.valueOf("F" + n) || code == KeyCode.valueOf("DIGIT" + n)
                    || code == KeyCode.valueOf("NUMPAD" + n)) {
                return n;
            }
        }
        if (code == KeyCode.F10 || code == KeyCode.DIGIT0 || code == KeyCode.NUMPAD0) {
            return 10;
        }
        return 0;
    }

    /** Mueve el foco en la cuadrícula; izquierda/derecha saltan de fila, arriba/abajo no se salen. */
    private void mover(KeyEvent e, int delta) {
        e.consume();
        if (botones.isEmpty()) {
            return;
        }
        int actual = botones.indexOf(raiz.getScene().getFocusOwner());
        if (actual < 0) {
            botones.get(0).requestFocus();
            return;
        }
        int total = botones.size();
        int destino;
        if (Math.abs(delta) == 1) {
            destino = (actual + delta + total) % total;
        } else {
            destino = actual + delta;
            if (destino < 0 || destino >= total) {
                // Abajo desde la penúltima fila incompleta: ir al último botón.
                destino = delta > 0 && actual / COLUMNAS < (total - 1) / COLUMNAS ? total - 1 : actual;
            }
        }
        botones.get(destino).requestFocus();
    }

    private void abrir(Opcion op) {
        if (!Sesion.tiene(op.permiso())) {
            return;
        }
        if (op.vista() != null) {
            Navegador.ir(op.vista());
        } else {
            Alertas.info("Próximamente", "\"" + op.texto() + "\" se construye en la Fase " + op.fase() + ".");
        }
    }
}
