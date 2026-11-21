package com.fgsistemas.pos;

import com.fgsistemas.pos.config.AppConfig;
import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.controller.Flujo;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Navegador;
import javafx.application.Application;
import javafx.application.HostServices;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Arranque: BD + migraciones, y decide primera vez (bienvenida) vs login. */
public class App extends Application {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    static {
        // Apache POI usa log4j-api; sin implementación imprime un aviso. Usamos su logger simple.
        System.setProperty("log4j2.loggerContextFactory",
                "org.apache.logging.log4j.simple.SimpleLoggerContextFactory");
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

        try {
            AppConfig.crearCarpetas();
            Database.inicializar(AppConfig.archivoBD());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "No se pudo iniciar la base de datos", e);
            Alertas.error("No se pudo abrir la base de datos.\n\n" + e.getMessage());
            Platform.exit();
            return;
        }

        // TODO Fase 8: respaldo automático diario (docs/modulos/23-respaldos.md).

        stage.setMinWidth(1024);
        stage.setMinHeight(700);
        stage.setMaximized(true);
        stage.show();

        try {
            Flujo.irAInicio();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "No se pudo mostrar la pantalla inicial", e);
            Alertas.error("No se pudo iniciar el sistema.\n\n" + e.getMessage());
            Platform.exit();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
