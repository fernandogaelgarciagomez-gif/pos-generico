package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.model.Turno;
import com.fgsistemas.pos.service.ActualizacionService;
import com.fgsistemas.pos.service.AuthService;
import com.fgsistemas.pos.service.ConfiguracionInicialService;
import com.fgsistemas.pos.service.InventarioService;
import com.fgsistemas.pos.service.RespaldoService;
import com.fgsistemas.pos.service.TurnoService;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Navegador;
import com.fgsistemas.pos.util.Sesion;
import javafx.application.Platform;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Decisiones de navegación del flujo general (CLAUDE.md → "Flujo general de la app"):
 * bienvenida o login → turno → menú.
 */
public final class Flujo {

    private static final Logger LOG = Logger.getLogger(Flujo.class.getName());

    private Flujo() {
    }

    /** Al abrir la app: sin admin → bienvenida; con admin → login. */
    public static void irAInicio() throws SQLException {
        if (new ConfiguracionInicialService().requiereConfiguracion()) {
            Navegador.ir("bienvenida");
        } else {
            irALogin(null);
        }
    }

    public static void irALogin(String usuarioPrellenado) {
        LoginController login = Navegador.ir("login");
        if (usuarioPrellenado != null) {
            login.prellenar(usuarioPrellenado);
        }
    }

    /** Tras un login correcto: verificar turno y llegar al menú. */
    public static void despuesDeLogin() {
        try {
            TurnoService turnos = new TurnoService();
            // Errores humanos: con el reloj de Windows atrasado, ventas y cortes quedan con fecha equivocada.
            String avisoReloj = turnos.avisoRelojAtrasado();
            if (avisoReloj != null) {
                Alertas.advertencia(avisoReloj);
            }
            Optional<Turno> abierto = turnos.turnoAbierto();
            if (abierto.isEmpty()) {
                TurnoController tc = Navegador.ir("turno");
                tc.mostrarAbrirTurno();
            } else if (abierto.get().getUsuarioId() == Sesion.usuario().getId()) {
                turnos.continuar(abierto.get());
                entrarAlMenu();
            } else {
                TurnoController tc = Navegador.ir("turno");
                tc.mostrarTurnoDeOtro(abierto.get());
            }
        } catch (SQLException e) {
            errorDatos(e);
            cerrarSesion();
        }
    }

    /** Regresa al menú principal (Esc desde cualquier pantalla). */
    public static void irAlMenu() {
        Navegador.ir("menu");
    }

    /**
     * Primera llegada al menú tras el login / apertura de turno: avisa (sin bloquear) si el turno
     * viene de un día anterior y, para admin, si hay productos con stock bajo.
     */
    public static void entrarAlMenu() {
        Navegador.ir("menu");
        Turno t = Sesion.turno();
        if (t != null && TurnoService.esDeDiaAnterior(t, Fechas.ahora().toLocalDate())) {
            LocalDate dia = t.getAbiertoEn().toLocalDate();
            String desde = dia.equals(Fechas.ahora().toLocalDate().minusDays(1))
                    ? "desde ayer"
                    : "desde el " + Fechas.FECHA_CORTA.format(dia);
            Alertas.advertencia("Este turno lleva abierto " + desde + ". Se recomienda hacer corte.");
        }
        if (Sesion.esAdmin()) {
            String avisoRespaldo = RespaldoService.tomarAviso();
            if (avisoRespaldo != null) {
                Alertas.advertencia(avisoRespaldo);
            }
            try {
                int bajos = new InventarioService().contarStockBajo();
                if (bajos > 0) {
                    Alertas.toast("⚠ " + bajos + (bajos == 1 ? " producto" : " productos")
                            + " con stock bajo (F2 para ver)");
                }
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "No se pudo contar el stock bajo", e);
            }
            revisarActualizacionDelDia();
        }
    }

    /**
     * Una vez al día, en segundo plano, revisa si hay versión nueva y lo avisa con un mensaje breve (decisión 85).
     * Sin internet no pasa nada: no se insiste ni se muestra error.
     */
    private static void revisarActualizacionDelDia() {
        ActualizacionService svc = new ActualizacionService();
        if (svc.yaSeRevisoHoy() || ActualizacionService.instalacionActual().isEmpty()) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                svc.buscar().ifPresent(d -> Platform.runLater(() -> Alertas.toast(
                        "Hay una versión nueva del programa (" + d.version()
                                + "). Instálala en Configuración → Actualizaciones")));
            } catch (Exception e) {
                LOG.log(Level.INFO, "No se pudo revisar si hay actualizaciones: " + e.getMessage());
            }
        }, "revisar-actualizacion");
        t.setDaemon(true);
        t.start();
    }

    public static void cerrarSesion() {
        AuthService.instancia().logout();
        irALogin(null);
    }

    /** Muestra un error de BD sin stack trace al usuario y lo deja en el log. */
    public static void errorDatos(Exception e) {
        LOG.log(Level.SEVERE, "Error de datos", e);
        Alertas.error(com.fgsistemas.pos.util.Registro.mensajeBaseDeDatos(e));
    }
}
