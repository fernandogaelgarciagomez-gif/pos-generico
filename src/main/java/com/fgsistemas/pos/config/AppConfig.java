package com.fgsistemas.pos.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuración de la aplicación que no vive en la BD: rutas y versión.
 * Los datos del negocio (nombre, teléfono, etc.) salen de la tabla {@code negocio}.
 */
public final class AppConfig {

    public static final String NOMBRE_APP = "Punto de Venta";
    public static final String DESARROLLADOR = "FG Sistemas";
    /**
     * Contacto del desarrollador para "Acerca de" (22-configuracion.md: configurable en código).
     * DECISION: vacíos hasta que FG Sistemas dé sus datos; si están vacíos no se muestran.
     */
    public static final String CONTACTO_TELEFONO = "";
    public static final String CONTACTO_CORREO = "";

    /** Propiedad de sistema para cambiar la carpeta de datos (pruebas, instalaciones especiales). */
    public static final String PROP_CARPETA_DATOS = "pos.datos";

    private static final Properties PROPS = cargarPropiedades();

    private AppConfig() {
    }

    public static String version() {
        return PROPS.getProperty("app.version", "dev");
    }

    /**
     * Carpeta donde viven la BD, respaldos, logs e imágenes.
     * DECISION: %LOCALAPPDATA%\FGSistemas\POS (fuera del proyecto y de OneDrive; ver docs/05-decisiones.md).
     */
    public static Path carpetaDatos() {
        String personalizada = System.getProperty(PROP_CARPETA_DATOS);
        if (personalizada != null && !personalizada.isBlank()) {
            return Paths.get(personalizada).toAbsolutePath();
        }
        String base = System.getenv("LOCALAPPDATA");
        if (base == null || base.isBlank()) {
            base = System.getProperty("user.home");
        }
        return Paths.get(base, "FGSistemas", "POS");
    }

    public static Path archivoBD() {
        return carpetaDatos().resolve("pos.db");
    }

    public static Path carpetaRespaldos() {
        return carpetaDatos().resolve("respaldos");
    }

    public static Path carpetaLogs() {
        return carpetaDatos().resolve("logs");
    }

    public static Path carpetaImg() {
        return carpetaDatos().resolve("img");
    }

    public static void crearCarpetas() throws IOException {
        Files.createDirectories(carpetaDatos());
        Files.createDirectories(carpetaRespaldos());
        Files.createDirectories(carpetaLogs());
        Files.createDirectories(carpetaImg());
    }

    private static Properties cargarPropiedades() {
        Properties p = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/app.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException ignorada) {
            // Sin archivo de propiedades se usa la versión "dev".
        }
        return p;
    }
}
