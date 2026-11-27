package com.fgsistemas.pos;

import com.fgsistemas.pos.config.AppConfig;
import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.controller.Flujo;
import com.fgsistemas.pos.service.ActualizacionService;
import com.fgsistemas.pos.service.RespaldoService;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Navegador;
import com.fgsistemas.pos.util.Registro;
import javafx.application.Application;
import javafx.application.HostServices;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Arranque: BD + migraciones, y decide primera vez (bienvenida) vs login. */
public class App extends Application {

    /** Antes que nada (antes de leer cualquier fecha): la zona horaria igual a la de Windows. */
    private static final String CORRECCION_ZONA = com.fgsistemas.pos.util.ZonaHoraria.corregir();

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    static {
        // Apache POI usa log4j-api; sin implementación imprime un aviso. Usamos su logger simple.
        System.setProperty("log4j2.loggerContextFactory",
                "org.apache.logging.log4j.simple.SimpleLoggerContextFactory");
        // Actualizaciones por https: usar los certificados de Windows. Así funciona aunque el antivirus revise
        // las conexiones seguras (Avast, ESET...) con su propio certificado, que Java no conoce.
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows")
                && System.getProperty("javax.net.ssl.trustStoreType") == null) {
            System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT");
        }
    }

    private static HostServices hostServices;

    /** Abre un archivo (p. ej. un Excel exportado) con el programa predeterminado de Windows. */
    public static void abrirArchivo(Path archivo) {
        if (hostServices != null) {
            hostServices.showDocument(archivo.toUri().toString());
        }
    }

    @Override
    public void start(Stage stage) {
        hostServices = getHostServices();
        stage.setTitle(AppConfig.NOMBRE_APP);
        Navegador.init(stage);

        // Fase 9: registro de errores en %LOCALAPPDATA%\FGSistemas\POS\logs y errores inesperados sin stack trace.
        Registro.configurar(AppConfig.carpetaLogs());
        if (CORRECCION_ZONA != null) {
            LOG.info(CORRECCION_ZONA);
        }
        Thread.setDefaultUncaughtExceptionHandler(App::errorInesperado);

        if (!Registro.tomarCandado(AppConfig.carpetaDatos())) {
            Alertas.advertencia("El punto de venta ya está abierto en esta computadora.\n\n"
                    + "Búscalo en la barra de tareas de Windows.");
            Platform.exit();
            return;
        }

        try {
            AppConfig.crearCarpetas();
            Database.inicializar(AppConfig.archivoBD());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "No se pudo iniciar la base de datos", e);
            Alertas.error("No se pudo abrir la base de datos.\n\n" + Registro.mensajeBaseDeDatos(e));
            Platform.exit();
            return;
        }

        // Respaldo automático diario (23-respaldos.md). Si falla, se avisa al admin al entrar; no bloquea.
        new RespaldoService().automaticoAlAbrir();

        // Errores humanos: cerrar la ventana (X o Alt+F4) con una venta o captura a medias.
        stage.setOnCloseRequest(e -> {
            String pendiente = Navegador.cambiosPendientes();
            if (pendiente != null && !Alertas.confirmarPeligro(pendiente + "\n\n¿Cerrar el sistema de todos modos?")) {
                e.consume();
            }
        });

        stage.setMinWidth(1024);
        stage.setMinHeight(700);
        stage.setMaximized(true);
        stage.show();

        try {
            Flujo.irAInicio();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "No se pudo mostrar la pantalla inicial", e);
            Alertas.error("No se pudo iniciar el sistema.\n\n" + Registro.mensajeInesperado(e));
            Platform.exit();
            return;
        }

        // Si se acaba de actualizar (decisión 85), decir cómo salió.
        new ActualizacionService().tomarResultado().ifPresent(m -> Platform.runLater(() -> {
            LOG.info(m);
            if (m.startsWith("Listo")) {
                Alertas.info("Actualización", m);
            } else {
                Alertas.advertencia(m);
            }
        }));
    }

    private static volatile boolean mostrandoError;

    /** Cualquier excepción no atrapada: al log con detalle y al usuario un mensaje claro (una sola vez a la vez). */
    private static void errorInesperado(Thread hilo, Throwable e) {
        LOG.log(Level.SEVERE, "Error inesperado en " + hilo.getName(), e);
        Runnable mostrar = () -> {
            if (mostrandoError) {
                return;
            }
            mostrandoError = true;
            try {
                Alertas.error(Registro.mensajeInesperado(e));
            } catch (RuntimeException ignorada) {
                // Si ni el diálogo se puede mostrar, ya quedó en el log.
            } finally {
                mostrandoError = false;
            }
        };
        if (Platform.isFxApplicationThread()) {
            mostrar.run();
        } else {
            Platform.runLater(mostrar);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
