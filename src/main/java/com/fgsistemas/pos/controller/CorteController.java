package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.model.Corte;
import com.fgsistemas.pos.model.Turno;
import com.fgsistemas.pos.service.Autorizacion;
import com.fgsistemas.pos.service.CorteService;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Navegador;
import com.fgsistemas.pos.util.Sesion;
import com.fgsistemas.pos.util.Teclado;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Corte de caja (18-corte-de-caja.md). Paso 1: conteo a ciegas (total o por denominación);
 * Paso 2: resultado contra lo esperado; Paso 3: turno cerrado y exportar.
 * También corta el turno de otro usuario desde la pantalla de turno (12-turno-fondo-caja.md).
 */
public class CorteController {

    /** Denominaciones en centavos (billetes y monedas de México). */
    private static final long[] DENOMINACIONES = {100000, 50000, 20000, 10000, 5000, 2000, 1000, 500, 200, 100, 50};

    @FXML private BorderPane raiz;
    @FXML private ScrollPane scroll;
    @FXML private StackPane centro;

    private final CorteService servicio = new CorteService();
    private Turno turno;
    private Autorizacion autorizacion;
    private boolean ajeno;
    private int paso;

    // Paso 1
    private TextField txtTotal;
    private final List<TextField> cantidades = new ArrayList<>();
    private Label lblTotalDesglose;
    private VBox modoTotal;
    private VBox modoDesglose;
    private Button btnModo;
    private TextField txtNotas;
    private Label lblError;
    private boolean porDenominacion;

    // Paso 2 y 3
    private Corte previa;
    private Corte guardado;
    private TextField txtNotaDiferencia;
    private Label lblError2;

    @FXML
    private void initialize() {
        raiz.addEventFilter(KeyEvent.KEY_PRESSED, this::teclas);
        turno = Sesion.turno();
        if (turno != null) {
            mostrarPaso1();
        }
    }

    /** Corte del turno abierto por otro usuario (con la autorización del admin si hizo falta). */
    public void iniciarTurnoAjeno(Turno turnoAjeno, Autorizacion auth) {
        this.turno = turnoAjeno;
        this.autorizacion = auth;
        this.ajeno = true;
        mostrarPaso1();
    }

    // ------------------------------------------------------------------ teclado

    private void teclas(KeyEvent e) {
        KeyCode k = e.getCode();
        boolean sinModificador = !e.isControlDown() && !e.isAltDown() && !e.isShortcutDown();
        switch (paso) {
            case 1 -> {
                if (k == KeyCode.ENTER) {
                    e.consume();
                    continuar();
                } else if (k == KeyCode.ESCAPE) {
                    e.consume();
                    salirSinCorte();
                } else if (k == KeyCode.D && sinModificador && !txtNotas.isFocused()) {
                    // Los campos de conteo solo aceptan números: la D no les estorba.
                    e.consume();
                    alternarModo();
                }
            }
            case 2 -> {
                if (k == KeyCode.ENTER) {
                    e.consume();
                    cerrarTurno();
                } else if (k == KeyCode.ESCAPE) {
                    e.consume();
                    regresarAlConteo();
                }
            }
            case 3 -> {
                if (k == KeyCode.F7 && e.isShiftDown()) {
                    e.consume();
                    CorteVista.exportarExcel(guardado, null);
                } else if (k == KeyCode.F7) {
                    e.consume();
                    CorteVista.exportarPdf(guardado);
                } else if (k == KeyCode.ENTER || k == KeyCode.ESCAPE) {
                    e.consume();
                    terminar();
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ paso 1: conteo a ciegas

    private void mostrarPaso1() {
        paso = 1;
        Label titulo = titulo("Corte de caja · Paso 1 de 3: contar el efectivo");
        Label info = secundario(infoTurno());
        Label ayuda = new Label("Cuenta todo el efectivo de la caja, incluido el fondo. "
                + "Por seguridad no se muestra cuánto debería haber hasta el siguiente paso.");
        ayuda.setWrapText(true);

        txtTotal = new TextField();
        txtTotal.getStyleClass().add("campo-grande");
        txtTotal.setPromptText("0.00");
        Teclado.campoDinero(txtTotal);
        modoTotal = new VBox(6, secundario("Efectivo contado"), txtTotal);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(6);
        ColumnConstraints c3 = new ColumnConstraints();
        c3.setHalignment(HPos.RIGHT);
        c3.setMinWidth(140);
        grid.getColumnConstraints().addAll(new ColumnConstraints(150), new ColumnConstraints(), c3);
        cantidades.clear();
        lblTotalDesglose = new Label(Dinero.formatear(0));
        lblTotalDesglose.getStyleClass().add("total");
        for (int i = 0; i < DENOMINACIONES.length; i++) {
            long d = DENOMINACIONES[i];
            TextField cant = new TextField();
            cant.setPromptText("0");
            cant.getStyleClass().add("denominacion");
            Teclado.soloNumeros(cant, 0);
            Label sub = new Label(Dinero.formatear(0));
            cant.textProperty().addListener((o, a, n) -> {
                sub.setText(Dinero.formatear(piezas(cant) * d));
                lblTotalDesglose.setText(Dinero.formatear(totalDesglose()));
                lblError.setText("");
            });
            cantidades.add(cant);
            grid.add(new Label((d >= 2000 ? "Billete " : "Moneda ") + Dinero.formatear(d)), 0, i);
            grid.add(cant, 1, i);
            grid.add(sub, 2, i);
        }
        Teclado.ordenTab(cantidades.toArray(new Node[0]));
        HBox total = new HBox(12, secundario("Total contado"), lblTotalDesglose);
        total.setAlignment(Pos.CENTER_LEFT);
        modoDesglose = new VBox(10, secundario("Escribe cuántas piezas hay de cada una (Tab avanza)"), grid, total);

        btnModo = Acciones.boton("", "D", this::alternarModo);
        txtNotas = new TextField();
        txtNotas.setPromptText("Notas (opcional)");
        lblError = new Label();
        lblError.getStyleClass().add("texto-error");
        lblError.setWrapText(true);
        txtTotal.textProperty().addListener((o, a, n) -> lblError.setText(""));

        Button continuar = Acciones.boton("Continuar", "Enter", this::continuar);
        continuar.getStyleClass().add("boton-principal");
        Button salir = Acciones.boton(ajeno ? "Regresar" : "Regresar al menú", "Esc", this::salirSinCorte);

        VBox tarjeta = tarjeta(titulo, info, ayuda, modoTotal, modoDesglose, btnModo,
                secundario("Notas"), txtNotas, lblError, barra(continuar, salir));
        mostrar(tarjeta);
        porDenominacion = !porDenominacion; // alternarModo lo regresa al modo elegido antes (o al total)
        alternarModo();
    }

    private void alternarModo() {
        porDenominacion = !porDenominacion;
        visible(modoTotal, !porDenominacion);
        visible(modoDesglose, porDenominacion);
        btnModo.setText(porDenominacion ? "Capturar solo el total" : "Contar por denominación");
        Teclado.enfocar(porDenominacion ? cantidades.get(0) : txtTotal);
    }

    private static long piezas(TextField t) {
        return t.getText().isBlank() ? 0 : Long.parseLong(t.getText());
    }

    private long totalDesglose() {
        long total = 0;
        for (int i = 0; i < DENOMINACIONES.length; i++) {
            total += piezas(cantidades.get(i)) * DENOMINACIONES[i];
        }
        return total;
    }

    private long contado() {
        if (porDenominacion) {
            return totalDesglose();
        }
        return txtTotal.getText().isBlank() ? 0 : Dinero.parsear(txtTotal.getText());
    }

    private void continuar() {
        long contado;
        try {
            contado = contado();
        } catch (IllegalArgumentException e) {
            lblError.setText(e.getMessage());
            return;
        }
        if (!porDenominacion && txtTotal.getText().isBlank()) {
            lblError.setText("Escribe cuánto efectivo contaste (0 si no hay).");
            txtTotal.requestFocus();
            return;
        }
        try {
            previa = servicio.vistaPrevia(turno.getId(), contado, autorizacion);
        } catch (IllegalArgumentException | IllegalStateException | PermisoDenegadoException e) {
            lblError.setText(e.getMessage());
            return;
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        mostrarPaso2();
    }

    private void salirSinCorte() {
        if (ajeno) {
            Flujo.despuesDeLogin(); // regresa a decidir qué hacer con el turno abierto
        } else {
            Flujo.irAlMenu();
        }
    }

    // ------------------------------------------------------------------ paso 2: resultado

    private void mostrarPaso2() {
        paso = 2;
        Label titulo = titulo("Corte de caja · Paso 2 de 3: resultado");
        Label info = secundario(infoTurno());
        Node vista = CorteVista.vista(previa);
        lblError2 = new Label();
        lblError2.getStyleClass().add("texto-error");
        lblError2.setWrapText(true);

        VBox nota = new VBox(6);
        txtNotaDiferencia = new TextField(txtNotas.getText());
        if (previa.diferencia() != 0) {
            Label pide = new Label("Hay diferencia: escribe una nota que la explique (obligatoria).");
            pide.getStyleClass().add("texto-advertencia");
            txtNotaDiferencia.setPromptText("Ej. Se dio mal un cambio");
            nota.getChildren().addAll(pide, txtNotaDiferencia);
        } else {
            txtNotaDiferencia.setPromptText("Notas (opcional)");
            nota.getChildren().addAll(secundario("Notas"), txtNotaDiferencia);
        }
        txtNotaDiferencia.textProperty().addListener((o, a, n) -> lblError2.setText(""));

        Button cerrar = Acciones.boton("Cerrar turno", "Enter", this::cerrarTurno);
        cerrar.getStyleClass().add("boton-principal");
        Button regresar = Acciones.boton("Regresar al conteo", "Esc", this::regresarAlConteo);
        mostrar(tarjeta(titulo, info, vista, nota, lblError2, barra(cerrar, regresar)));
        Teclado.enfocar(txtNotaDiferencia);
    }

    private void regresarAlConteo() {
        paso = 1;
        mostrar(tarjetaPaso1Actual);
        Teclado.enfocar(porDenominacion ? cantidades.get(0) : txtTotal);
    }

    private void cerrarTurno() {
        String nota = txtNotaDiferencia.getText();
        if (previa.diferencia() != 0 && nota.isBlank()) {
            lblError2.setText("Escribe la nota que explique la diferencia de " + Dinero.formatear(previa.diferencia())
                    + ".");
            txtNotaDiferencia.requestFocus();
            return;
        }
        if (!Alertas.confirmar("¿Cerrar turno? Esta acción no se puede deshacer.")) {
            txtNotaDiferencia.requestFocus();
            return;
        }
        try {
            guardado = servicio.cerrarTurno(turno.getId(), previa.efectivoContado(), nota, autorizacion);
        } catch (IllegalArgumentException | IllegalStateException | PermisoDenegadoException e) {
            lblError2.setText(e.getMessage());
            return;
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        mostrarPaso3();
    }

    // ------------------------------------------------------------------ paso 3: listo

    private void mostrarPaso3() {
        paso = 3;
        Label titulo = titulo("✓ Turno cerrado · Corte #" + guardado.id());
        Label info = secundario(com.fgsistemas.pos.service.CorteExport.encabezado(guardado));
        Node vista = CorteVista.vista(guardado);
        Label cambio = new Label();
        if (guardado.efectivoEsperado() != previa.efectivoEsperado()) {
            cambio.setText("Hubo movimientos mientras contabas: las cifras se actualizaron al cerrar.");
            cambio.getStyleClass().add("texto-advertencia");
        }
        Label sugerencia = new Label("Sugerencia: deja en caja el fondo para el siguiente turno y retira el resto. "
                + "El fondo nuevo se pregunta al abrir el siguiente turno.");
        sugerencia.setWrapText(true);
        sugerencia.getStyleClass().add("texto-secundario");

        Button pdf = Acciones.boton("Exportar a PDF", "F7", () -> CorteVista.exportarPdf(guardado));
        Button excel = Acciones.boton("Exportar a Excel", "Shift+F7", () -> CorteVista.exportarExcel(guardado, null));
        Button terminar = Acciones.boton(ajeno ? "Abrir mi turno" : "Terminar y cerrar sesión", "Enter",
                this::terminar);
        terminar.getStyleClass().add("boton-principal");
        mostrar(tarjeta(titulo, info, vista, cambio, sugerencia, barra(terminar, pdf, excel)));
        Teclado.enfocar(scroll);
        Alertas.toast("Corte guardado · diferencia " + Dinero.formatear(guardado.diferencia()));
    }

    private void terminar() {
        if (ajeno) {
            TurnoController tc = Navegador.ir("turno");
            tc.mostrarAbrirTurno();
        } else {
            Flujo.cerrarSesion();
        }
    }

    // ------------------------------------------------------------------ utilidades

    private VBox tarjetaPaso1Actual;

    private void mostrar(VBox tarjeta) {
        if (paso == 1) {
            tarjetaPaso1Actual = tarjeta;
        }
        centro.getChildren().setAll(tarjeta);
        StackPane.setAlignment(tarjeta, Pos.TOP_CENTER);
        scroll.setVvalue(0);
    }

    private static VBox tarjeta(Node... hijos) {
        VBox v = new VBox(12, hijos);
        v.getStyleClass().add("tarjeta");
        v.setMaxWidth(720);
        v.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return v;
    }

    private String infoTurno() {
        String desde = Fechas.FECHA_HORA.format(turno.getAbiertoEn());
        return "Turno de " + turno.getUsuarioNombre() + " · abierto " + desde + " · fondo inicial "
                + Dinero.formatear(turno.getFondoInicial()) + (ajeno ? " · lo corta " + Sesion.usuario().getNombre() : "");
    }

    private static Label titulo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("subtitulo");
        l.setWrapText(true);
        return l;
    }

    private static Label secundario(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("texto-secundario");
        l.setWrapText(true);
        return l;
    }

    private static HBox barra(Button... botones) {
        HBox h = new HBox(10, botones);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setStyle("-fx-padding: 8 0 0 0;");
        return h;
    }

    private static void visible(Node n, boolean v) {
        n.setVisible(v);
        n.setManaged(v);
    }
}
