package com.fgsistemas.pos.util;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.Locale;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Registro de errores en archivo, mensajes de error para el usuario (sin stack traces) y candado para no
 * abrir el sistema dos veces (Fase 9).
 */
public final class Registro {

    private static final Logger LOG = Logger.getLogger(Registro.class.getName());

    /** Tamaño máximo de cada archivo de registro y cuántos se conservan (pos0.log es el más reciente). */
    private static final int LIMITE_BYTES = 2 * 1024 * 1024;
    private static final int ARCHIVOS = 5;

    private static FileChannel canalCandado;
    private static FileLock candado;

    private Registro() {
    }

    /**
     * Manda el log de toda la app a {@code <carpeta>/pos0.log … pos4.log} (rotan a los 2 MB).
     * Si no se puede, la app sigue funcionando (solo en consola).
     */
    public static void configurar(Path carpetaLogs) {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT %4$-7s %2$s - %5$s%6$s%n");
        try {
            Files.createDirectories(carpetaLogs);
            FileHandler archivo = new FileHandler(carpetaLogs.resolve("pos%g.log").toString(), LIMITE_BYTES,
                    ARCHIVOS, true);
            archivo.setEncoding("UTF-8");
            archivo.setFormatter(new SimpleFormatter());
            archivo.setLevel(Level.INFO);
            Logger raiz = Logger.getLogger("");
            raiz.addHandler(archivo);
            LOG.info("=== Inicio del sistema ===");
        } catch (IOException | SecurityException e) {
            LOG.log(Level.WARNING, "No se pudo abrir el archivo de registro en " + carpetaLogs, e);
        }
    }

    /**
     * Evita abrir el sistema dos veces a la vez (doble clic de más): dos ventanas con la misma caja confunden y
     * pueden dejar cobros o capturas a medias.
     *
     * @return true si esta es la única instancia abierta
     */
    public static synchronized boolean tomarCandado(Path carpetaDatos) {
        try {
            Files.createDirectories(carpetaDatos);
            canalCandado = FileChannel.open(carpetaDatos.resolve("pos.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            candado = canalCandado.tryLock();
            return candado != null;
        } catch (OverlappingFileLockException e) {
            return false;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo revisar si el sistema ya estaba abierto", e);
            return true; // ante la duda, no impedir que trabajen
        }
    }

    /** Mensaje entendible para un error de la base de datos (el detalle técnico va al log, no a la pantalla). */
    public static String mensajeBaseDeDatos(Throwable e) {
        String m = mensajeCompleto(e).toUpperCase(Locale.ROOT);
        if (m.contains("SQLITE_BUSY") || m.contains("LOCKED")) {
            return "La base de datos está ocupada en este momento. Espera unos segundos e intenta de nuevo.";
        }
        if (m.contains("SQLITE_FULL") || m.contains("DISK IS FULL") || m.contains("ESPACIO")) {
            return "El disco de la computadora está lleno. Libera espacio y vuelve a intentar.";
        }
        if (m.contains("SQLITE_READONLY") || m.contains("READONLY") || m.contains("ACCESS DENIED")) {
            return "No se puede escribir en la carpeta de datos del sistema. Revisa los permisos o el antivirus.";
        }
        if (m.contains("SQLITE_CORRUPT") || m.contains("MALFORMED") || m.contains("NOT A DATABASE")) {
            return "La base de datos parece dañada. Un administrador puede restaurar el último respaldo en "
                    + "Configuración → Respaldos.";
        }
        if (m.contains("SQLITE_CANTOPEN") || m.contains("UNABLE TO OPEN")) {
            return "No se pudo abrir la base de datos. Revisa que la carpeta de datos exista y no esté bloqueada.";
        }
        return "Ocurrió un problema al guardar o leer los datos. Intenta de nuevo; si se repite, avisa al "
                + "administrador (el detalle quedó en el registro de errores).";
    }

    /** Mensaje para un error inesperado del programa. */
    public static String mensajeInesperado(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException) {
                return mensajeBaseDeDatos(t);
            }
        }
        return "Ocurrió un error inesperado. Lo que estabas haciendo puede no haberse guardado: revísalo.\n\n"
                + "El detalle quedó en el registro de errores (carpeta logs).";
    }

    private static String mensajeCompleto(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            sb.append(' ').append(t.getMessage());
        }
        return sb.toString();
    }
}
