package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.config.AppConfig;
import com.fgsistemas.pos.model.Turno;
import com.fgsistemas.pos.model.Usuario;
import com.fgsistemas.pos.service.NegocioService;
import com.fgsistemas.pos.service.TurnoService;
import com.fgsistemas.pos.service.UsuarioService;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Sesion;
import com.fgsistemas.pos.util.Teclado;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Barra superior incluida en todas las pantallas (excepto login/bienvenida). */
public class BarraSuperiorController {

    private static final DateTimeFormatter RELOJ =
            DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.forLanguageTag("es-MX"));

    @FXML private HBox barra;
    @FXML private Label lblNegocio;
    @FXML private Label lblUsuario;
    @FXML private Label lblTurno;
    @FXML private Label lblReloj;
    @FXML private HBox zonaAcciones;

    private Timeline reloj;

    @FXML
    private void initialize() {
        String negocio = AppConfig.NOMBRE_APP;
        try {
            negocio = new NegocioService().obtener().map(n -> n.getNombre()).orElse(negocio);
        } catch (SQLException e) {
            // La barra no debe impedir abrir la pantalla.
        }
        lblNegocio.setText(negocio);
        actualizarUsuario();

        Turno t = Sesion.turno();
        if (t != null && t.getAbiertoEn() != null) {
            boolean viejo = TurnoService.esDeDiaAnterior(t, Fechas.ahora().toLocalDate());
            lblTurno.setText("Turno desde " + (viejo ? Fechas.FECHA_HORA : Fechas.HORA).format(t.getAbiertoEn()));
        }

        reloj = new Timeline(new KeyFrame(Duration.seconds(1), e -> tic()));
        reloj.setCycleCount(Animation.INDEFINITE);
        reloj.play();
        tic();
        barra.sceneProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) {
                reloj.stop();
            }
        });

        // DECISION (cliente): "Cambiar contraseña" ya no va en la barra; vive en la pantalla Usuarios.
    }

    private void actualizarUsuario() {
        Usuario u = Sesion.usuario();
        lblUsuario.setText(u == null ? "" : u.getNombre() + " · " + u.getRol().etiqueta());
    }

    private void tic() {
        String texto = RELOJ.format(Fechas.ahora());
        lblReloj.setText(Character.toUpperCase(texto.charAt(0)) + texto.substring(1));
    }

    /** Para pantallas que necesiten refrescar la barra (p. ej. al editar el propio nombre). */
    public void refrescar() {
        actualizarUsuario();
    }

    Node nodo() {
        return barra;
    }
}
