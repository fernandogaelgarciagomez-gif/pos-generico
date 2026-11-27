package com.fgsistemas.pos;

import com.fgsistemas.pos.service.ActualizacionService;
import com.fgsistemas.pos.service.ActualizacionService.Disponible;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Actualización automática (decisión 85): versiones, manifiesto, descarga verificada y cambio del jar. */
class ActualizacionTest {

    @TempDir
    Path tmp;

    private HttpServer servidor;

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void comparaVersionesComoNumeros() {
        assertTrue(ActualizacionService.esMasNueva("0.3.1", "0.3.0"));
        assertTrue(ActualizacionService.esMasNueva("1.10.0", "1.9.9"));
        assertTrue(ActualizacionService.esMasNueva("0.4", "0.3.9"));
        assertTrue(ActualizacionService.esMasNueva("0.3.0", "dev"));
        assertFalse(ActualizacionService.esMasNueva("0.3.0", "0.3.0"));
        assertFalse(ActualizacionService.esMasNueva("0.3", "0.3.0"));
        assertFalse(ActualizacionService.esMasNueva("0.2.9", "0.3.0"));
    }

    @Test
    void leeElManifiesto() throws IOException {
        Disponible d = ActualizacionService.leerManifiesto(manifiesto("0.4.0", "https://x.test/pos-generico.jar",
                "a".repeat(64)) + "notas=Línea 1\\nLínea 2\nrequiereInstalador=true\n");
        assertEquals("0.4.0", d.version());
        assertEquals("Línea 1\nLínea 2", d.notas());
        assertTrue(d.requiereInstalador());
        assertEquals(700, d.bytes());
    }

    @Test
    void rechazaManifiestosMalos() {
        assertThrows(IOException.class, () -> ActualizacionService.leerManifiesto(
                manifiesto("abc", "https://x.test/a.jar", "a".repeat(64))));
        assertThrows(IOException.class, () -> ActualizacionService.leerManifiesto(
                manifiesto("0.4.0", "http://x.test/a.jar", "a".repeat(64))));
        assertThrows(IOException.class, () -> ActualizacionService.leerManifiesto(
                manifiesto("0.4.0", "https://x.test/a.jar", "corta")));
        assertThrows(IOException.class, () -> ActualizacionService.leerManifiesto(""));
    }

    @Test
    void buscaYDescargaVerificandoLaHuella() throws IOException {
        byte[] jar = "contenido del programa nuevo".getBytes(StandardCharsets.UTF_8);
        String huella = ActualizacionService.sha256(jar);
        URI base = servir(manifiesto("99.0.0", "https://x.test/pos-generico.jar", huella), jar);
        ActualizacionService svc = new ActualizacionService(base.resolve("/actualizacion.properties"),
                tmp.resolve("trabajo"));

        assertFalse(svc.yaSeRevisoHoy());
        Optional<Disponible> d = svc.buscar();
        assertTrue(d.isPresent());
        assertTrue(svc.yaSeRevisoHoy());

        // La descarga real va por https; aquí se prueba contra el servidor local.
        Disponible local = new Disponible("99.0.0", base.resolve("/pos-generico.jar"), huella, jar.length, "",
                false, "");
        Path bajado = svc.descargar(local, null);
        assertArrayEquals(jar, Files.readAllBytes(bajado));

        Disponible danado = new Disponible("99.0.1", base.resolve("/pos-generico.jar"), "b".repeat(64),
                jar.length, "", false, "");
        IOException e = assertThrows(IOException.class, () -> svc.descargar(danado, null));
        assertTrue(e.getMessage().contains("huella"));
    }

    @Test
    void noOfreceLaMismaVersion() throws IOException {
        URI base = servir(manifiesto("0.0.1", "https://x.test/pos-generico.jar", "c".repeat(64)), new byte[0]);
        ActualizacionService svc = new ActualizacionService(base.resolve("/actualizacion.properties"),
                tmp.resolve("trabajo"));
        assertTrue(svc.buscar().isEmpty());
    }

    @Test
    void sinVersionesPublicadasDaMensajeClaro() throws IOException {
        URI base = servir("", new byte[0]);
        ActualizacionService svc = new ActualizacionService(base.resolve("/no-existe"), tmp.resolve("trabajo"));
        IOException e = assertThrows(IOException.class, svc::buscar);
        assertTrue(e.getMessage().contains("no hay versiones"), e.getMessage());
    }

    @Test
    void elAyudanteCambiaElJarYGuardaElAnterior() throws IOException {
        Path app = Files.createDirectories(tmp.resolve("Punto de Venta").resolve("app"));
        Files.writeString(app.resolve("pos-generico.jar"), "version vieja");
        Path nuevo = tmp.resolve("pos-generico-0.4.0.jar");
        Files.writeString(nuevo, "version nueva");

        Actualizador.instalar(app, nuevo);

        assertEquals("version nueva", Files.readString(app.resolve("pos-generico.jar")));
        assertEquals("version vieja", Files.readString(app.resolve("pos-generico.anterior.jar")));
        assertFalse(Files.exists(app.resolve("pos-generico.jar.nuevo")));
    }

    @Test
    void elAyudanteDejaElResultadoYLoMuestraUnaVez() throws IOException {
        Path raiz = tmp.resolve("Punto de Venta");
        Path app = Files.createDirectories(raiz.resolve("app"));
        Files.writeString(app.resolve("pos-generico.jar"), "vieja");
        Path trabajo = Files.createDirectories(tmp.resolve("trabajo"));
        Path nuevo = trabajo.resolve("pos-generico-0.4.0.jar");
        Files.writeString(nuevo, "nueva");

        // Argumentos incompletos: no hace nada.
        Actualizador.main(new String[]{"999999999", app.toString()});
        assertEquals("vieja", Files.readString(app.resolve("pos-generico.jar")));
        // pid que ya no existe: no espera; sin lanzador: no abre nada.
        Actualizador.main(new String[]{"999999999", app.toString(), nuevo.toString(), "0.4.0", "0.3.0",
                trabajo.toString()});

        assertEquals("nueva", Files.readString(app.resolve("pos-generico.jar")));
        assertEquals("0.4.0", Files.readString(raiz.resolve("version.txt")).trim());
        ActualizacionService svc = new ActualizacionService(URI.create("https://x.test/"), trabajo);
        Optional<String> r = svc.tomarResultado();
        assertTrue(r.isPresent() && r.get().contains("0.4.0"), r.toString());
        assertTrue(svc.tomarResultado().isEmpty());
        assertFalse(Files.exists(nuevo), "la descarga ya instalada se borra");
    }

    private static String manifiesto(String version, String jar, String sha) {
        return "version=" + version + "\njar=" + jar + "\nsha256=" + sha + "\nbytes=700\n";
    }

    private URI servir(String manifiesto, byte[] jar) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", ex -> {
            String ruta = ex.getRequestURI().getPath();
            byte[] cuerpo = ruta.equals("/actualizacion.properties") ? manifiesto.getBytes(StandardCharsets.UTF_8)
                    : ruta.equals("/pos-generico.jar") ? jar : null;
            if (cuerpo == null) {
                ex.sendResponseHeaders(404, -1);
            } else {
                ex.sendResponseHeaders(200, cuerpo.length == 0 ? -1 : cuerpo.length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(cuerpo);
                }
            }
            ex.close();
        });
        servidor.start();
        return URI.create("http://127.0.0.1:" + servidor.getAddress().getPort());
    }
}
