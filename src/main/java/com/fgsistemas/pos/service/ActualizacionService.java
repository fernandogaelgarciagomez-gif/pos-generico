package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.AppConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.function.LongConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Actualización del programa sin reinstalar (decisión 85).
 *
 * <p>Cada versión se publica en GitHub Releases con dos archivos: {@code pos-generico.jar} (el programa) y
 * {@code actualizacion.properties} (versión, dirección del jar, huella SHA-256, notas). El programa lee el
 * manifiesto de la última versión, baja el jar, revisa la huella y lanza {@link com.fgsistemas.pos.Actualizador},
 * que espera a que el programa se cierre, cambia el jar y lo vuelve a abrir. Los datos no se tocan; si la versión
 * nueva trae cambios de base de datos, se migran solos al abrir (con respaldo previo).
 *
 * <p>Solo cambia el jar del programa. Si una versión cambia librerías o el Java incluido, el manifiesto dice
 * {@code requiereInstalador=true} y hay que usar el instalador completo.
 */
public class ActualizacionService {

    private static final Logger LOG = Logger.getLogger(ActualizacionService.class.getName());

    /** Nombre fijo del programa dentro de {@code app\} (no cambia entre versiones: lo usan los accesos directos). */
    public static final String NOMBRE_JAR = "pos-generico.jar";
    public static final String NOMBRE_JAR_ANTERIOR = "pos-generico.anterior.jar";
    /** Para pruebas o para cambiar de dónde se bajan las versiones. */
    public static final String PROP_URL = "pos.actualizaciones";
    private static final long MAXIMO_BYTES = 100L * 1024 * 1024;

    /** Versión publicada que es más nueva que la instalada. */
    public record Disponible(String version, URI jar, String sha256, long bytes, String notas,
                             boolean requiereInstalador, String paginaDescarga) {
    }

    /** Dónde está instalado el programa (o null si se corre desde el proyecto, sin instalar). */
    public record Instalacion(Path carpetaApp, Path raiz, Path jar, Path java, List<String> lanzador) {
    }

    private final HttpClient http;
    private final URI manifiesto;
    private final Path carpetaTrabajo;

    public ActualizacionService() {
        this(URI.create(System.getProperty(PROP_URL, AppConfig.URL_ACTUALIZACIONES)),
                AppConfig.carpetaDatos().resolve("actualizacion"));
    }

    public ActualizacionService(URI manifiesto, Path carpetaTrabajo) {
        this.manifiesto = manifiesto;
        this.carpetaTrabajo = carpetaTrabajo;
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    // ------------------------------------------------------------------ buscar

    /**
     * Lee el manifiesto publicado.
     *
     * @return la versión nueva, o vacío si la instalada ya es la más reciente
     * @throws IOException sin internet, GitHub no responde o el manifiesto viene mal
     */
    public Optional<Disponible> buscar() throws IOException {
        String texto = new String(get(manifiesto, 64 * 1024, null), StandardCharsets.UTF_8);
        Disponible d = leerManifiesto(texto);
        registrarRevision();
        return esMasNueva(d.version(), AppConfig.version()) ? Optional.of(d) : Optional.empty();
    }

    /** Interpreta {@code actualizacion.properties}. */
    public static Disponible leerManifiesto(String texto) throws IOException {
        Properties p = new Properties();
        p.load(new StringReader(texto));
        String version = p.getProperty("version", "").trim();
        String jar = p.getProperty("jar", "").trim();
        String sha = p.getProperty("sha256", "").trim().toLowerCase(Locale.ROOT);
        if (!version.matches("\\d+(\\.\\d+){0,3}") || jar.isEmpty() || !sha.matches("[0-9a-f]{64}")) {
            throw new IOException("El archivo de actualización publicado no es válido.");
        }
        URI uri;
        try {
            uri = new URI(jar);
        } catch (URISyntaxException e) {
            throw new IOException("La dirección de la actualización no es válida.", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("La actualización debe bajarse por https.");
        }
        long bytes;
        try {
            bytes = Long.parseLong(p.getProperty("bytes", "0").trim());
        } catch (NumberFormatException e) {
            bytes = 0;
        }
        return new Disponible(version, uri, sha, bytes, p.getProperty("notas", "").trim(),
                Boolean.parseBoolean(p.getProperty("requiereInstalador", "false").trim()),
                p.getProperty("pagina", "").trim());
    }

    /** true si {@code publicada} es más nueva que {@code instalada} (1.10.0 &gt; 1.9.3; "dev" cuenta como 0). */
    public static boolean esMasNueva(String publicada, String instalada) {
        int[] a = partes(publicada);
        int[] b = partes(instalada);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int[] partes(String v) {
        if (v == null) {
            return new int[0];
        }
        String[] s = v.trim().split("\\.");
        List<Integer> r = new ArrayList<>();
        for (String x : s) {
            try {
                r.add(Integer.parseInt(x.replaceAll("\\D.*$", "")));
            } catch (NumberFormatException e) {
                r.add(0);
            }
        }
        return r.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Para revisar automáticamente solo una vez al día. */
    public boolean yaSeRevisoHoy() {
        try {
            Path f = carpetaTrabajo.resolve("ultima-revision.txt");
            return Files.exists(f) && LocalDate.now().toString().equals(Files.readString(f).trim());
        } catch (IOException e) {
            return false;
        }
    }

    private void registrarRevision() {
        try {
            Files.createDirectories(carpetaTrabajo);
            Files.writeString(carpetaTrabajo.resolve("ultima-revision.txt"), LocalDate.now().toString());
        } catch (IOException e) {
            LOG.log(Level.FINE, "No se pudo guardar la fecha de revisión", e);
        }
    }

    // ------------------------------------------------------------------ descargar

    /**
     * Baja el jar de la versión nueva a la carpeta de trabajo y revisa su huella SHA-256.
     *
     * @param progreso recibe los bytes bajados hasta el momento (puede ser null)
     * @return el jar bajado y verificado
     */
    public Path descargar(Disponible d, LongConsumer progreso) throws IOException {
        Files.createDirectories(carpetaTrabajo);
        Path destino = carpetaTrabajo.resolve("pos-generico-" + d.version() + ".jar");
        Path parcial = carpetaTrabajo.resolve("pos-generico-" + d.version() + ".jar.part");
        byte[] datos = get(d.jar(), MAXIMO_BYTES, progreso);
        String huella = sha256(datos);
        if (!huella.equals(d.sha256())) {
            throw new IOException("El archivo bajado está incompleto o dañado (no coincide su huella). "
                    + "Intenta de nuevo.");
        }
        Files.write(parcial, datos);
        Files.move(parcial, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        LOG.info("Actualización " + d.version() + " bajada y verificada: " + destino);
        return destino;
    }

    public static String sha256(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] get(URI uri, long maximo, LongConsumer progreso) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "PuntoDeVenta-Actualizador/" + AppConfig.version())
                .GET().build();
        HttpResponse<InputStream> r;
        try {
            r = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Se interrumpió la descarga.", e);
        } catch (IOException e) {
            throw new IOException("No hay conexión con el servidor de actualizaciones. Revisa el internet.", e);
        }
        if (r.statusCode() == 404) {
            r.body().close();
            throw new IOException("Todavía no hay versiones publicadas.");
        }
        if (r.statusCode() / 100 != 2) {
            r.body().close();
            throw new IOException("El servidor de actualizaciones respondió con error " + r.statusCode() + ".");
        }
        try (InputStream in = r.body(); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > maximo) {
                    throw new IOException("El archivo de actualización es demasiado grande.");
                }
                out.write(buf, 0, n);
                if (progreso != null) {
                    progreso.accept(total);
                }
            }
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------ instalar

    /**
     * Dónde está instalado el programa que se está ejecutando. Vacío si no se corre desde la instalación
     * (por ejemplo desde el proyecto con Maven): ahí no se puede actualizar solo.
     */
    public static Optional<Instalacion> instalacionActual() {
        try {
            Path jar = Path.of(ActualizacionService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(jar) || !NOMBRE_JAR.equalsIgnoreCase(jar.getFileName().toString())) {
                return Optional.empty();
            }
            Path app = jar.getParent();
            Path raiz = app.getParent();
            if (app == null || raiz == null || !"app".equalsIgnoreCase(app.getFileName().toString())) {
                return Optional.empty();
            }
            Path home = Path.of(System.getProperty("java.home"));
            Path java = home.resolve("bin").resolve("javaw.exe");
            if (!Files.exists(java)) {
                java = home.resolve("bin").resolve("java.exe");
            }
            if (!Files.exists(java)) {
                return Optional.empty(); // instalación anterior a la 0.3.0 (su Java no trae java.exe)
            }
            List<String> lanzador;
            Path exe = raiz.resolve("Punto de Venta.exe");
            Path cmd = raiz.resolve("Punto de Venta.cmd");
            if (Files.exists(exe)) {
                lanzador = List.of(exe.toString());
            } else if (Files.exists(cmd)) {
                lanzador = List.of("cmd.exe", "/c", cmd.toString());
            } else {
                return Optional.empty();
            }
            return Optional.of(new Instalacion(app, raiz, jar, java, lanzador));
        } catch (URISyntaxException | RuntimeException e) {
            LOG.log(Level.FINE, "No se pudo ubicar la instalación", e);
            return Optional.empty();
        }
    }

    /**
     * Lanza el ayudante (desde el jar ya bajado, que no está bloqueado) que, en cuanto este programa se cierre,
     * cambia el jar y vuelve a abrir el programa. Quien llama debe cerrar el programa enseguida.
     */
    public void lanzarAyudante(Instalacion inst, Path jarNuevo, String versionNueva) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(inst.java().toString());
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(jarNuevo.toString());
        cmd.add("com.fgsistemas.pos.Actualizador");
        cmd.add(Long.toString(ProcessHandle.current().pid()));
        cmd.add(inst.carpetaApp().toString());
        cmd.add(jarNuevo.toString());
        cmd.add(versionNueva);
        cmd.add(AppConfig.version());
        cmd.add(carpetaTrabajo.toString());
        cmd.addAll(inst.lanzador());
        LOG.info("Lanzando el ayudante de actualización: " + cmd);
        new ProcessBuilder(cmd)
                .directory(inst.raiz().toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(
                        AppConfig.carpetaLogs().resolve("actualizacion.log").toFile()))
                .start();
    }

    /**
     * Resultado de la última actualización (lo escribe el ayudante). Se lee una sola vez al abrir el programa.
     *
     * @return mensaje para el usuario, o vacío si no hubo actualización
     */
    public Optional<String> tomarResultado() {
        Path f = carpetaTrabajo.resolve("resultado.properties");
        if (!Files.exists(f)) {
            return Optional.empty();
        }
        Properties p = new Properties();
        try (var in = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            p.load(in);
        } catch (IOException e) {
            return Optional.empty();
        } finally {
            try {
                Files.deleteIfExists(f);
            } catch (IOException ignorada) {
                // Si no se puede borrar, el mensaje saldría otra vez: no es grave.
            }
        }
        limpiarDescargas();
        if ("ok".equals(p.getProperty("estado"))) {
            return Optional.of("Listo: el programa se actualizó a la versión " + p.getProperty("a", "") + ".");
        }
        return Optional.of("No se pudo terminar la actualización a la versión " + p.getProperty("a", "")
                + "; sigues con la versión " + AppConfig.version() + ".\n\n" + p.getProperty("detalle", ""));
    }

    /** Borra jars ya instalados de la carpeta de trabajo (el ayudante ya los copió). */
    private void limpiarDescargas() {
        try (var s = Files.list(carpetaTrabajo)) {
            s.filter(x -> x.getFileName().toString().matches("pos-generico-.*\\.jar(\\.part)?")).forEach(x -> {
                try {
                    Files.deleteIfExists(x);
                } catch (IOException ignorada) {
                    // Se reintenta la próxima vez.
                }
            });
        } catch (IOException ignorada) {
            // Carpeta inexistente: nada que limpiar.
        }
    }

    /** Escribe un archivo de texto de forma atómica (temporal + mover). Lo usa también el ayudante. */
    public static void escribirAtomico(Path destino, Properties p) throws IOException {
        Files.createDirectories(destino.getParent());
        Path tmp = destino.resolveSibling(destino.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            p.store(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8), null);
        }
        Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
