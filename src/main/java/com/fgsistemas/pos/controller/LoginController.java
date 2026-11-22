package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.config.AppConfig;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.service.AuthService;
import com.fgsistemas.pos.service.LoginException;
import com.fgsistemas.pos.service.NegocioService;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Teclado;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/** Login centrado (docs/modulos/11-login.md). */
public class LoginController {

    @FXML private StackPane raiz;
    @FXML private ImageView imgLogo;
    @FXML private StackPane logoGenerico;
    @FXML private Label lblNegocio;
    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblError;
    @FXML private Button btnEntrar;
    @FXML private Label lblPie;
    @FXML private Button btnSalir;

    private final AuthService auth = AuthService.instancia();
    private Timeline reloj;

    @FXML
    private void initialize() {
        cargarNegocio();

        Teclado.cadenaEnter(this::entrar, txtUsuario, txtPassword);
        Teclado.enterDispara(btnEntrar, btnSalir);
        Teclado.ordenTab(txtUsuario, txtPassword, btnEntrar, btnSalir);

        // Reloj del pie + cuenta regresiva del bloqueo, cada segundo.
        reloj = new Timeline(new KeyFrame(Duration.seconds(1), e -> tic()));
        reloj.setCycleCount(Animation.INDEFINITE);
        reloj.play();
        // Detener el Timeline cuando esta pantalla deje de mostrarse.
        raiz.sceneProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) {
                reloj.stop();
            }
        });
        tic();

        Teclado.enfocar(txtUsuario);
    }

    /** Viene de la bienvenida: usuario ya escrito y foco en la contraseña. */
    public void prellenar(String usuario) {
        txtUsuario.setText(usuario);
        Teclado.enfocar(txtPassword);
    }

    @FXML
    private void entrar() {
        if (auth.segundosBloqueo() > 0) {
            return;
        }
        if (txtUsuario.getText().isBlank()) {
            lblError.setText("Escribe tu usuario.");
            txtUsuario.requestFocus();
            return;
        }
        try {
            auth.login(txtUsuario.getText(), txtPassword.getText());
        } catch (LoginException e) {
            lblError.setText(e.getMessage());
            txtPassword.clear();
            if (e.getTipo() == LoginException.Tipo.BLOQUEADO) {
                aplicarBloqueo();
            } else {
                txtPassword.requestFocus();
            }
            return;
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        reloj.stop();
        Flujo.despuesDeLogin();
    }

    @FXML
    private void salir() {
        if (Alertas.confirmar("¿Cerrar el sistema?")) {
            Platform.exit();
        }
    }

    private void tic() {
        LocalDateTime ahora = Fechas.ahora();
        String fecha = Fechas.FECHA_LARGA.format(ahora);
        fecha = Character.toUpperCase(fecha.charAt(0)) + fecha.substring(1);
        lblPie.setText(fecha + "   ·   " + AppConfig.NOMBRE_APP + " v" + AppConfig.version());

        long espera = auth.segundosBloqueo();
        if (espera > 0) {
            aplicarBloqueo();
        } else if (txtUsuario.isDisabled()) {
            txtUsuario.setDisable(false);
            txtPassword.setDisable(false);
            btnEntrar.setDisable(false);
            lblError.setText("");
            txtPassword.requestFocus();
        }
    }

    private void aplicarBloqueo() {
        long espera = auth.segundosBloqueo();
        txtUsuario.setDisable(true);
        txtPassword.setDisable(true);
        btnEntrar.setDisable(true);
        lblError.setText("Demasiados intentos fallidos. Espera " + espera + " s.");
        btnSalir.requestFocus();
    }

    private void cargarNegocio() {
        String nombre = AppConfig.NOMBRE_APP;
        try {
            Optional<Negocio> negocio = new NegocioService().obtener();
            if (negocio.isPresent()) {
                nombre = negocio.get().getNombre();
                mostrarLogo(negocio.get().getLogoPath());
            }
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        }
        lblNegocio.setText(nombre);
    }

    private void mostrarLogo(String logoPath) {
        if (logoPath == null || logoPath.isBlank()) {
            return;
        }
        Path ruta = AppConfig.carpetaDatos().resolve(logoPath);
        if (!Files.isRegularFile(ruta)) {
            return;
        }
        try (java.io.InputStream in = Files.newInputStream(ruta)) {
            Image imagen = new Image(in); // por stream: si cambian el logo se ve el nuevo, sin caché
            if (imagen.isError()) {
                return;
            }
            imgLogo.setImage(imagen);
        } catch (java.io.IOException e) {
            return;
        }
        imgLogo.setVisible(true);
        imgLogo.setManaged(true);
        logoGenerico.setVisible(false);
        logoGenerico.setManaged(false);
    }
}
