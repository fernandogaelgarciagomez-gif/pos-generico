package com.fgsistemas.pos;

import com.fgsistemas.pos.service.ActualizacionService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Ayudante de actualización (decisión 85). Lo lanza el programa desde el jar NUEVO ya bajado y verificado, y se
 * cierra. Este proceso espera a que el programa termine (Windows no deja reemplazar un jar en uso), guarda el jar
 * actual como {@code pos-generico.anterior.jar}, pone el nuevo, deja el resultado para mostrarlo al abrir y vuelve
 * a abrir el programa. No tiene ventana: lo que pasa queda en {@code logs\actualizacion.log}.
 *
 * <p>Argumentos: pid carpetaApp jarNuevo versionNueva versionAnterior carpetaTrabajo lanzador...
 * (este orden es un contrato con versiones anteriores del programa: no cambiarlo).
 */
public final class Actualizador {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Actualizador() {
    }

    public static void main(String[] args) {
        if (args.length < 6) {
            log("Argumentos incompletos: " + Arrays.toString(args));
            return;
        }
        long pid = Long.parseLong(args[0]);
        Path app = Path.of(args[1]);
        Path jarNuevo = Path.of(args[2]);
        String versionNueva = args[3];
        String versionAnterior = args[4];
        Path trabajo = Path.of(args[5]);
        List<String> lanzador = List.of(args).subList(6, args.length);

        log("Actualizando de " + versionAnterior + " a " + versionNueva + " en " + app);
        esperarCierre(pid);
        Properties resultado = new Properties();
        resultado.setProperty("de", versionAnterior);
        resultado.setProperty("a", versionNueva);
        try {
            instalar(app, jarNuevo);
            escribirVersion(app.getParent(), versionNueva);
            resultado.setProperty("estado", "ok");
            log("Actualización terminada.");
        } catch (IOException | RuntimeException e) {
            resultado.setProperty("estado", "error");
            resultado.setProperty("detalle", "Detalle: " + e.getMessage());
            log("ERROR: " + e);
        }
        try {
            ActualizacionService.escribirAtomico(trabajo.resolve("resultado.properties"), resultado);
        } catch (IOException e) {
            log("No se pudo guardar el resultado: " + e);
        }
        abrir(app.getParent(), lanzador);
    }

    private static void esperarCierre(long pid) {
        Optional<ProcessHandle> p = ProcessHandle.of(pid);
        if (p.isEmpty()) {
            return;
        }
        try {
            p.get().onExit().get(2, TimeUnit.MINUTES);
            Thread.sleep(700); // que Windows suelte los archivos
        } catch (Exception e) {
            log("El programa no se cerró a tiempo (" + e + "); se intenta de todos modos.");
        }
    }

    /**
     * Guarda el jar actual como anterior y pone el nuevo. Reintenta unos segundos por si Windows o el antivirus
     * tienen el archivo ocupado. Si falla a medias, deja el jar anterior en su lugar.
     */
    static void instalar(Path app, Path jarNuevo) throws IOException {
        Path jar = app.resolve(ActualizacionService.NOMBRE_JAR);
        Path anterior = app.resolve(ActualizacionService.NOMBRE_JAR_ANTERIOR);
        Path temporal = app.resolve(ActualizacionService.NOMBRE_JAR + ".nuevo");
        byte[] nuevo = Files.readAllBytes(jarNuevo);
        String huella = ActualizacionService.sha256(nuevo);
        Files.write(temporal, nuevo);
        IOException ultimo = null;
        for (int intento = 1; intento <= 40; intento++) {
            try {
                if (Files.exists(jar)) {
                    Files.copy(jar, anterior, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(temporal, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                if (!huella.equals(ActualizacionService.sha256(Files.readAllBytes(jar)))) {
                    throw new IOException("El programa copiado no coincide con el bajado.");
                }
                return;
            } catch (IOException e) {
                ultimo = e;
                log("Intento " + intento + ": " + e);
                dormir(500);
            }
        }
        Files.deleteIfExists(temporal);
        if (Files.exists(anterior) && (!Files.exists(jar) || Files.size(jar) == 0)) {
            Files.copy(anterior, jar, StandardCopyOption.REPLACE_EXISTING);
        }
        throw new IOException("No se pudo reemplazar el programa (¿sigue abierto o lo bloquea el antivirus?): "
                + (ultimo == null ? "" : ultimo.getMessage()), ultimo);
    }

    private static void escribirVersion(Path raiz, String version) {
        try {
            Files.writeString(raiz.resolve("version.txt"), version + System.lineSeparator());
        } catch (IOException e) {
            log("No se pudo escribir version.txt: " + e);
        }
    }

    private static void abrir(Path raiz, List<String> lanzador) {
        if (lanzador.isEmpty()) {
            return;
        }
        try {
            new ProcessBuilder(lanzador).directory(raiz.toFile()).start();
            log("Programa abierto de nuevo.");
        } catch (IOException e) {
            log("No se pudo volver a abrir el programa: " + e);
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String m) {
        System.out.println(LocalDateTime.now().format(HORA) + " " + m);
        System.out.flush();
    }
}
