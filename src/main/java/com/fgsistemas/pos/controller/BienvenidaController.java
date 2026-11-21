package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.service.ConfiguracionInicialService;
import com.fgsistemas.pos.service.ConfiguracionInicialService.DatosIniciales;
import com.fgsistemas.pos.service.Validacion;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Teclado;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.function.Supplier;

/** Primera ejecución en 3 pasos (docs/modulos/10-bienvenida-primer-admin.md). */
public class BienvenidaController {

    @FXML private StackPane raiz;
    @FXML private Label punto1;
    @FXML private Label punto2;
    @FXML private Label punto3;
    @FXML private VBox paso1;
    @FXML private VBox paso2;
    @FXML private VBox paso3;
    @FXML private Button btnComenzar;

    @FXML private TextField txtNombre;
    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtPassword;
    @FXML private PasswordField txtConfirmar;
    @FXML private Label errNombre;
    @FXML private Label errUsuario;
    @FXML private Label errPassword;
    @FXML private Label errConfirmar;
    @FXML private Button btnSiguiente;

    @FXML private TextField txtNegocio;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtDireccion;
    @FXML private Label errNegocio;
    @FXML private Button btnTerminar;

    private final ConfiguracionInicialService servicio = new ConfiguracionInicialService();
    private int pasoActual = 1;

    @FXML
    private void initialize() {
        Teclado.enterDispara(btnComenzar, btnSiguiente, btnTerminar);

        // Paso 2
        Teclado.cadenaEnter(this::siguiente, txtNombre, txtUsuario, txtPassword, txtConfirmar, btnSiguiente);
        Teclado.ordenTab(txtNombre, txtUsuario, txtPassword, txtConfirmar, btnSiguiente);
        enVivo(txtNombre, errNombre, () -> Validacion.nombre(txtNombre.getText()));
        enVivo(txtUsuario, errUsuario, () -> Validacion.usuario(txtUsuario.getText()));
        enVivo(txtPassword, errPassword, () -> Validacion.password(txtPassword.getText()));
        enVivo(txtConfirmar, errConfirmar,
                () -> Validacion.confirmacion(txtPassword.getText(), txtConfirmar.getText()));
        // Si cambia la contraseña, revalidar la confirmación ya escrita.
        txtPassword.textProperty().addListener((o, a, n) -> {
            if (!txtConfirmar.getText().isEmpty()) {
                mostrar(txtConfirmar, errConfirmar, Validacion.confirmacion(n, txtConfirmar.getText()));
            }
        });

        // Paso 3
        Teclado.cadenaEnter(this::terminar, txtNegocio, txtTelefono, txtDireccion, btnTerminar);
        Teclado.ordenTab(txtNegocio, txtTelefono, txtDireccion, btnTerminar);
        enVivo(txtNegocio, errNegocio, () -> Validacion.nombreNegocio(txtNegocio.getText()));

        Teclado.atajo(raiz, KeyCode.ESCAPE, this::regresar);
        irAPaso(1);
    }

    @FXML
    private void comenzar() {
        irAPaso(2);
    }

    @FXML
    private void siguiente() {
        Node primerInvalido = null;
        primerInvalido = validar(txtNombre, errNombre, Validacion.nombre(txtNombre.getText()), primerInvalido);
        primerInvalido = validar(txtUsuario, errUsuario, Validacion.usuario(txtUsuario.getText()), primerInvalido);
        primerInvalido = validar(txtPassword, errPassword, Validacion.password(txtPassword.getText()), primerInvalido);
        primerInvalido = validar(txtConfirmar, errConfirmar,
                Validacion.confirmacion(txtPassword.getText(), txtConfirmar.getText()), primerInvalido);
        if (primerInvalido != null) {
            primerInvalido.requestFocus();
            return;
        }
        irAPaso(3);
    }

    @FXML
    private void terminar() {
        String error = Validacion.nombreNegocio(txtNegocio.getText());
        if (error != null) {
            mostrar(txtNegocio, errNegocio, error);
            txtNegocio.requestFocus();
            return;
        }
        DatosIniciales datos = new DatosIniciales(
                txtNombre.getText(), txtUsuario.getText(), txtPassword.getText(), txtConfirmar.getText(),
                txtNegocio.getText(), txtTelefono.getText(), txtDireccion.getText());
        try {
            servicio.configurar(datos);
        } catch (IllegalArgumentException e) {
            // No debería pasar (ya se validó en pantalla), pero el servicio es la fuente de verdad.
            Alertas.error(e.getMessage());
            irAPaso(2);
            return;
        } catch (IllegalStateException e) {
            Alertas.info("Sistema configurado", e.getMessage());
            Flujo.irALogin(null);
            return;
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        Alertas.info("¡Listo!", "¡Listo! Ya puedes iniciar sesión.");
        Flujo.irALogin(datos.usuario());
    }

    @FXML
    private void atras() {
        regresar();
    }

    private void regresar() {
        if (pasoActual > 1) {
            irAPaso(pasoActual - 1);
        }
    }

    private void irAPaso(int paso) {
        pasoActual = paso;
        mostrarPaso(paso1, paso == 1);
        mostrarPaso(paso2, paso == 2);
        mostrarPaso(paso3, paso == 3);
        punto1.setText("●");
        punto2.setText(paso >= 2 ? "●" : "○");
        punto3.setText(paso >= 3 ? "●" : "○");
        Teclado.enfocar(paso == 1 ? btnComenzar : paso == 2 ? txtNombre : txtNegocio);
    }

    private static void mostrarPaso(VBox paso, boolean visible) {
        paso.setVisible(visible);
        paso.setManaged(visible);
    }

    /** Valida al escribir, pero solo después de que el campo se tocó (no pinta rojo al inicio). */
    private static void enVivo(TextInputControl campo, Label etiqueta, Supplier<String> regla) {
        campo.textProperty().addListener((o, a, n) -> mostrar(campo, etiqueta, regla.get()));
        campo.focusedProperty().addListener((o, antes, ahora) -> {
            if (!ahora && !campo.getText().isEmpty()) {
                mostrar(campo, etiqueta, regla.get());
            }
        });
    }

    private static Node validar(TextInputControl campo, Label etiqueta, String error, Node primerInvalido) {
        mostrar(campo, etiqueta, error);
        return (error != null && primerInvalido == null) ? campo : primerInvalido;
    }

    private static void mostrar(TextInputControl campo, Label etiqueta, String error) {
        etiqueta.setText(error == null ? "" : error);
        if (error == null) {
            campo.getStyleClass().remove("campo-invalido");
        } else if (!campo.getStyleClass().contains("campo-invalido")) {
            campo.getStyleClass().add("campo-invalido");
        }
    }
}
