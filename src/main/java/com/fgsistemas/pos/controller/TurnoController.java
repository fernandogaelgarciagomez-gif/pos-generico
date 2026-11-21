package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.model.Turno;
import com.fgsistemas.pos.service.TurnoService;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Sesion;
import com.fgsistemas.pos.util.Teclado;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;

/** Abrir turno con fondo inicial, o decidir sobre el turno abierto de otro usuario (12-turno-fondo-caja.md). */
public class TurnoController {

    @FXML private StackPane raiz;
    @FXML private VBox tarjetaAbrir;
    @FXML private Label lblSaludo;
    @FXML private TextField txtFondo;
    @FXML private Label lblErrorFondo;
    @FXML private Button btnAbrir;

    @FXML private VBox tarjetaOtro;
    @FXML private Label lblTurnoOtro;
    @FXML private Label lblTurnoFondo;
    @FXML private Button btnContinuar;
    @FXML private Button btnCorte;

    private final TurnoService turnos = new TurnoService();
    private Turno turnoDeOtro;

    @FXML
    private void initialize() {
        Teclado.campoDinero(txtFondo);
        Teclado.cadenaEnter(this::abrirTurno, txtFondo);
        Teclado.enterDispara(btnAbrir, btnContinuar, btnCorte);
        Teclado.ordenTab(txtFondo, btnAbrir);
        Teclado.ordenTab(btnContinuar, btnCorte);
        txtFondo.textProperty().addListener((o, a, n) -> lblErrorFondo.setText(""));

        // Flechas y 1/2 para elegir en la tarjeta de turno de otro usuario.
        Teclado.atajo(btnContinuar, KeyCode.DOWN, btnCorte::requestFocus);
        Teclado.atajo(btnCorte, KeyCode.UP, btnContinuar::requestFocus);
        Teclado.atajo(tarjetaOtro, KeyCode.DIGIT1, this::continuarTurno);
        Teclado.atajo(tarjetaOtro, KeyCode.NUMPAD1, this::continuarTurno);
        Teclado.atajo(tarjetaOtro, KeyCode.DIGIT2, this::cortarYAbrir);
        Teclado.atajo(tarjetaOtro, KeyCode.NUMPAD2, this::cortarYAbrir);

        // Sin turno no se entra: Esc cierra sesión y regresa al login.
        Teclado.atajo(raiz, KeyCode.ESCAPE, Flujo::cerrarSesion);
    }

    public void mostrarAbrirTurno() {
        lblSaludo.setText("Hola, " + Sesion.usuario().getNombre() + ".");
        txtFondo.setText("0");
        mostrar(tarjetaAbrir, true);
        mostrar(tarjetaOtro, false);
        Teclado.enfocar(txtFondo);
    }

    public void mostrarTurnoDeOtro(Turno turno) {
        this.turnoDeOtro = turno;
        String desde = Fechas.HORA.format(turno.getAbiertoEn());
        if (TurnoService.esDeDiaAnterior(turno, Fechas.ahora().toLocalDate())) {
            desde = Fechas.FECHA_HORA.format(turno.getAbiertoEn());
        }
        lblTurnoOtro.setText("Hay un turno abierto de " + turno.getUsuarioNombre() + " desde las " + desde + ".");
        lblTurnoFondo.setText("Fondo inicial: " + Dinero.formatear(turno.getFondoInicial()));
        mostrar(tarjetaAbrir, false);
        mostrar(tarjetaOtro, true);
        Teclado.enfocar(btnContinuar);
    }

    @FXML
    private void abrirTurno() {
        long fondo;
        try {
            fondo = txtFondo.getText().isBlank() ? 0 : Dinero.parsear(txtFondo.getText());
        } catch (IllegalArgumentException e) {
            lblErrorFondo.setText(e.getMessage());
            txtFondo.requestFocus();
            return;
        }
        try {
            turnos.abrir(fondo);
        } catch (IllegalArgumentException e) {
            lblErrorFondo.setText(e.getMessage());
            txtFondo.requestFocus();
            return;
        } catch (IllegalStateException e) {
            // Alguien abrió turno mientras tanto: repetir la verificación.
            Alertas.advertencia(e.getMessage());
            Flujo.despuesDeLogin();
            return;
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        Flujo.entrarAlMenu();
        Alertas.toast("Turno abierto · Fondo " + Dinero.formatear(fondo));
    }

    @FXML
    private void continuarTurno() {
        if (turnoDeOtro == null) {
            return;
        }
        try {
            turnos.continuar(turnoDeOtro);
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        Flujo.entrarAlMenu();
    }

    @FXML
    private void cortarYAbrir() {
        if (turnoDeOtro == null) {
            return;
        }
        // TODO Fase 7: corte de caja del turno ajeno (admin, o cajero con autorización de admin)
        // y después pedir fondo para el turno nuevo.
        Alertas.info("Próximamente", "El corte de caja se construye en la Fase 7.\n"
                + "Por ahora puedes continuar con el turno abierto.");
        btnContinuar.requestFocus();
    }

    @FXML
    private void regresar() {
        Flujo.cerrarSesion();
    }

    private static void mostrar(VBox tarjeta, boolean visible) {
        tarjeta.setVisible(visible);
        tarjeta.setManaged(visible);
    }
}
