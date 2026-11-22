package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.config.Migraciones;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.ClienteDao;
import com.fgsistemas.pos.dao.CorteDao;
import com.fgsistemas.pos.dao.NegocioDao;
import com.fgsistemas.pos.dao.ProductoDao;
import com.fgsistemas.pos.dao.UsuarioDao;
import com.fgsistemas.pos.dao.VentaDao;
import com.fgsistemas.pos.model.Cliente;
import com.fgsistemas.pos.model.Corte;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.Usuario;
import com.fgsistemas.pos.model.Venta;
import com.fgsistemas.pos.service.ExportService.Cant;
import com.fgsistemas.pos.service.ExportService.Hoja;
import com.fgsistemas.pos.service.ExportService.Monto;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Sesion;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Respaldos de la BD (23-respaldos.md): automático al abrir y después de cada corte, manual, restaurar
 * y "Exportar todo a Excel".
 * <p>
 * La copia se hace con {@code VACUUM INTO}: es consistente aunque la BD esté abierta.
 * Los respaldos se llaman {@code pos_AAAA-MM-DD_HHmm[_motivo].db}.
 */
public class RespaldoService {

    private static final Logger LOG = Logger.getLogger(RespaldoService.class.getName());

    public static final String PALABRA_RESTAURAR = "RESTAURAR";
    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");
    private static final Pattern NOMBRE = Pattern.compile("pos_(\\d{4}-\\d{2}-\\d{2})_(\\d{2})(\\d{2})(_.*)?\\.db",
            Pattern.CASE_INSENSITIVE);

    /** Último fallo del respaldo automático, para avisarle al admin al entrar (sin bloquear la app). */
    private static volatile String avisoPendiente;

    /** Un respaldo de la carpeta de respaldos. */
    public record Respaldo(Path archivo, LocalDateTime fecha, long bytes) {
        public String nombre() {
            return archivo.getFileName().toString();
        }

        /** "1.2 MB", "350 KB". */
        public String tamanoTexto() {
            if (bytes >= 1024L * 1024) {
                return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
            }
            return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
        }

        public boolean esPrevioARestaurar() {
            return nombre().contains("antes-de-restaurar");
        }
    }

    private final NegocioDao negocioDao = new NegocioDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();

    /** {@code <carpeta de datos>/respaldos} (en las pruebas, dentro de su carpeta temporal). */
    public static Path carpeta() {
        return NegocioService.carpetaDatos().resolve("respaldos");
    }

    // ------------------------------------------------------------------ automáticos

    /**
     * Al abrir la app: si el respaldo automático está activo y aún no hay respaldo de hoy, lo hace
     * y borra los viejos. Nunca lanza excepción: si falla lo deja en el log y en {@link #tomarAviso()}.
     */
    public Optional<Path> automaticoAlAbrir() {
        try {
            Negocio n = configuracion();
            if (n == null || !n.isRespaldoAutomatico() || hayRespaldoDe(Fechas.ahora().toLocalDate())) {
                return Optional.empty();
            }
            Path archivo = crear(carpeta(), null);
            limpiar(n.getDiasConservarRespaldos());
            LOG.info("Respaldo automático: " + archivo);
            return Optional.of(archivo);
        } catch (Exception e) {
            registrarFallo("al abrir el sistema", e);
            return Optional.empty();
        }
    }

    /** Después de cada corte de caja (si el respaldo automático está activo). Nunca lanza excepción. */
    public Optional<Path> automaticoDespuesDeCorte() {
        try {
            Negocio n = configuracion();
            if (n == null || !n.isRespaldoAutomatico()) {
                return Optional.empty();
            }
            Path archivo = crear(carpeta(), "corte");
            limpiar(n.getDiasConservarRespaldos());
            return Optional.of(archivo);
        } catch (Exception e) {
            registrarFallo("después del corte", e);
            return Optional.empty();
        }
    }

    /** Regresa (y borra) el aviso de un respaldo automático fallido, o null. */
    public static String tomarAviso() {
        String aviso = avisoPendiente;
        avisoPendiente = null;
        return aviso;
    }

    private static void registrarFallo(String cuando, Exception e) {
        LOG.log(Level.SEVERE, "Falló el respaldo automático " + cuando, e);
        avisoPendiente = "No se pudo hacer el respaldo automático " + cuando + ".\n\n" + e.getMessage()
                + "\n\nRevisa en Configuración → Respaldos y haz uno manual.";
    }

    // ------------------------------------------------------------------ manuales

    /** "Respaldar ahora" en la carpeta de respaldos. */
    public Path respaldarAhora() throws SQLException, IOException {
        Sesion.requiere(Permiso.RESPALDOS);
        Path archivo = crear(carpeta(), null);
        Negocio n = configuracion();
        if (n != null) {
            limpiar(n.getDiasConservarRespaldos());
        }
        bitacora("RESPALDO", archivo.toString());
        return archivo;
    }

    /** "Respaldar en otra ubicación" (USB, carpeta de Google Drive, etc.). */
    public Path respaldarEn(Path carpetaDestino) throws SQLException, IOException {
        Sesion.requiere(Permiso.RESPALDOS);
        if (carpetaDestino == null || !Files.isDirectory(carpetaDestino)) {
            throw new IllegalArgumentException("Elige una carpeta que exista.");
        }
        Path archivo = crear(carpetaDestino, null);
        bitacora("RESPALDO", archivo.toString());
        return archivo;
    }

    /** Respaldos de la carpeta de respaldos, el más reciente primero. */
    public List<Respaldo> listar() throws IOException {
        Sesion.requiere(Permiso.RESPALDOS);
        return respaldos(carpeta());
    }

    /**
     * Reemplaza la BD con {@code archivo}. Antes revisa que sea un respaldo válido y respalda el estado
     * actual. Quien llama debe cerrar la sesión y volver al inicio (la sesión era de la BD anterior).
     *
     * @return el respaldo de seguridad del estado anterior
     */
    public Path restaurar(Path archivo, String confirmacion) throws SQLException, IOException {
        Sesion.requiere(Permiso.RESPALDOS);
        if (confirmacion == null || !PALABRA_RESTAURAR.equalsIgnoreCase(confirmacion.trim())) {
            throw new IllegalArgumentException("Escribe la palabra " + PALABRA_RESTAURAR + " para confirmar.");
        }
        if (archivo == null || !Files.isRegularFile(archivo)) {
            throw new IllegalArgumentException("No se encontró el archivo del respaldo.");
        }
        Usuario quien = Sesion.usuario();
        Path bd = Database.archivo();
        Path temporal = bd.resolveSibling("pos.db.restaurando");
        Files.copy(archivo, temporal, StandardCopyOption.REPLACE_EXISTING);
        try {
            validar(temporal);
        } catch (IllegalArgumentException | SQLException e) {
            Files.deleteIfExists(temporal);
            throw e instanceof IllegalArgumentException iae ? iae
                    : new IllegalArgumentException("El archivo no es un respaldo válido del punto de venta.");
        }
        Path seguridad = crear(carpeta(), "antes-de-restaurar");
        try {
            Files.move(temporal, bd, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporal, bd, StandardCopyOption.REPLACE_EXISTING);
        }
        Database.inicializar(bd); // aplica migraciones si el respaldo es de una versión anterior
        Database.enTransaccion(c -> {
            // El usuario que restauró puede no existir (o ser otro) en el respaldo.
            Long id = new UsuarioDao().buscarPorId(c, quien.getId())
                    .filter(u -> u.getUsuario().equals(quien.getUsuario()))
                    .map(Usuario::getId).orElse(null);
            bitacoraDao.registrar(c, id, "RESTAURAR_RESPALDO", archivo.getFileName() + " · por "
                    + quien.getNombre() + " (" + quien.getUsuario() + ") · respaldo previo: " + seguridad.getFileName());
            return null;
        });
        LOG.warning("BD restaurada desde " + archivo + " (respaldo previo: " + seguridad + ")");
        return seguridad;
    }

    // ------------------------------------------------------------------ exportar todo

    /** "Exportar todo a Excel": Productos, Clientes, Ventas del mes y Cortes del mes. */
    public Path exportarTodo() throws SQLException, IOException {
        return new ExportService().excel("exportar_todo", hojasExportarTodo(Fechas.ahora().toLocalDate()));
    }

    /** Las hojas de "Exportar todo" para el mes de {@code dia}. */
    public List<Hoja> hojasExportarTodo(LocalDate dia) throws SQLException {
        Sesion.requiere(Permiso.RESPALDOS);
        LocalDate desde = dia.withDayOfMonth(1);
        LocalDate hasta = dia.withDayOfMonth(dia.lengthOfMonth());
        try (Connection c = Database.conectar()) {
            List<List<Object>> productos = new ArrayList<>();
            for (Producto p : new ProductoDao().listar(c, true)) {
                productos.add(List.of(p.getCodigo(), p.getNombre(), texto(p.getCategoriaNombre()),
                        p.getTipoVenta().etiqueta(), p.getUnidad(), new Monto(p.getPrecioVenta()),
                        new Monto(p.getCosto()), new Cant(p.getStock()), new Cant(p.getStockMinimo()),
                        p.isActivo() ? "Sí" : "No"));
            }
            List<List<Object>> clientes = new ArrayList<>();
            for (Cliente cl : new ClienteDao().listarConFechas(c, true)) {
                clientes.add(List.of(cl.getNombre(), texto(cl.getTelefono()), new Monto(cl.getSaldo()),
                        cl.isActivo() ? "Sí" : "No", fecha(cl.getUltimaCompra()), fecha(cl.getUltimoAbono()),
                        texto(cl.getNotas())));
            }
            List<List<Object>> ventas = new ArrayList<>();
            for (Venta.Resumen v : new VentaDao().listarRango(c, desde, hasta)) {
                ventas.add(List.of(v.folio(), fecha(v.fecha()), texto(v.usuarioNombre()), texto(v.clienteNombre()),
                        texto(v.metodos()), new Monto(v.total()), v.cancelada() ? "Cancelada" : "Completada",
                        texto(v.motivoCancelacion())));
            }
            List<List<Object>> cortes = new ArrayList<>();
            for (Corte k : new CorteDao().listar(c, desde, hasta, null, true)) {
                List<Object> f = new ArrayList<>();
                f.add(k.id());
                f.add(fecha(k.fecha()));
                f.add(texto(k.turnoUsuarioNombre()));
                f.add(new Monto(k.fondoInicial()));
                f.add(new Monto(k.totalVendido()));
                f.add(new Monto(k.efectivoEsperado()));
                f.add(new Monto(k.efectivoContado()));
                f.add(new Monto(k.diferencia()));
                f.add(k.numVentas());
                f.add(k.numCanceladas());
                f.add(k.ganancia() == null ? null : new Monto(k.ganancia()));
                f.add(texto(k.notas()));
                cortes.add(f);
            }
            return List.of(
                    new Hoja("Productos", List.of("Código", "Producto", "Categoría", "Se vende por", "Unidad",
                            "Precio", "Costo", "Stock", "Mínimo", "Activo"), productos),
                    new Hoja("Clientes", List.of("Cliente", "Teléfono", "Saldo (negativo = a favor)", "Activo",
                            "Última compra", "Último abono", "Notas"), clientes),
                    new Hoja("Ventas del mes", List.of("Folio", "Fecha", "Vendió", "Cliente", "Pago", "Total",
                            "Estado", "Motivo de cancelación"), ventas),
                    new Hoja("Cortes del mes", List.of("Corte", "Fecha", "Usuario", "Fondo", "Total vendido",
                            "Efectivo esperado", "Contado", "Diferencia", "Ventas", "Canceladas", "Ganancia",
                            "Notas"), cortes));
        }
    }

    // ------------------------------------------------------------------ internos

    /** ¿Hay en la carpeta de respaldos algún respaldo con fecha {@code dia}? */
    boolean hayRespaldoDe(LocalDate dia) throws IOException {
        return respaldos(carpeta()).stream().anyMatch(r -> r.fecha().toLocalDate().equals(dia));
    }

    /**
     * Borra los respaldos de la carpeta de respaldos con más de {@code dias} días.
     * Nunca borra el más reciente (siempre queda al menos uno).
     *
     * @return cuántos borró
     */
    int limpiar(int dias) throws IOException {
        List<Respaldo> lista = respaldos(carpeta());
        LocalDate limite = Fechas.ahora().toLocalDate().minusDays(dias);
        int borrados = 0;
        for (int i = 1; i < lista.size(); i++) { // i = 0 es el más reciente
            Respaldo r = lista.get(i);
            if (r.fecha().toLocalDate().isBefore(limite)) {
                try {
                    Files.deleteIfExists(r.archivo());
                    borrados++;
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "No se pudo borrar el respaldo viejo " + r.archivo(), e);
                }
            }
        }
        return borrados;
    }

    /** Copia consistente de la BD con {@code VACUUM INTO} en {@code carpeta}. */
    private static Path crear(Path carpeta, String motivo) throws SQLException, IOException {
        Files.createDirectories(carpeta);
        String base = "pos_" + SELLO.format(Fechas.ahora()) + (motivo == null ? "" : "_" + motivo);
        Path destino = carpeta.resolve(base + ".db");
        for (int n = 2; Files.exists(destino); n++) {
            destino = carpeta.resolve(base + "_" + n + ".db");
        }
        try (Connection c = Database.conectar();
             PreparedStatement ps = c.prepareStatement("VACUUM INTO ?")) {
            ps.setString(1, destino.toAbsolutePath().toString());
            ps.execute();
        } catch (SQLException e) {
            Files.deleteIfExists(destino);
            throw e;
        }
        return destino;
    }

    private static List<Respaldo> respaldos(Path carpeta) throws IOException {
        List<Respaldo> lista = new ArrayList<>();
        if (!Files.isDirectory(carpeta)) {
            return lista;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(carpeta, "*.db")) {
            for (Path p : ds) {
                Matcher m = NOMBRE.matcher(p.getFileName().toString());
                if (!m.matches() || !Files.isRegularFile(p)) {
                    continue;
                }
                LocalDateTime fecha;
                try {
                    fecha = LocalDate.parse(m.group(1)).atTime(Integer.parseInt(m.group(2)),
                            Integer.parseInt(m.group(3)));
                } catch (RuntimeException e) {
                    continue; // nombre con fecha imposible: no es nuestro
                }
                lista.add(new Respaldo(p, fecha, Files.size(p)));
            }
        }
        lista.sort(Comparator.comparing(Respaldo::fecha).thenComparing(Respaldo::nombre).reversed());
        return lista;
    }

    /** Revisa que el archivo sea una BD íntegra de este sistema, de esta versión o anterior. */
    private static void validar(Path archivo) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + archivo.toAbsolutePath());
             Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA integrity_check")) {
                if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                    throw new IllegalArgumentException("El respaldo está dañado (no pasó la revisión de integridad).");
                }
            }
            for (String tabla : List.of("schema_version", "negocio", "usuarios", "ventas")) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
                    ps.setString(1, tabla);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("El archivo no es un respaldo del punto de venta.");
                        }
                    }
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
                int version = rs.next() ? rs.getInt(1) : 0;
                if (version > Migraciones.versionMasReciente()) {
                    throw new IllegalArgumentException("El respaldo es de una versión más nueva del sistema. "
                            + "Actualiza el punto de venta antes de restaurarlo.");
                }
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM usuarios WHERE rol = 'ADMIN' AND activo = 1")) {
                if (!rs.next() || rs.getInt(1) == 0) {
                    throw new IllegalArgumentException("El respaldo no tiene ningún administrador activo.");
                }
            }
        }
    }

    private Negocio configuracion() throws SQLException {
        try (Connection c = Database.conectar()) {
            return negocioDao.obtener(c).orElse(null);
        }
    }

    private void bitacora(String accion, String detalle) throws SQLException {
        long usuarioId = Sesion.usuario().getId();
        Database.enTransaccion(c -> {
            bitacoraDao.registrar(c, usuarioId, accion, detalle);
            return null;
        });
    }

    private static String texto(String s) {
        return s == null ? "" : s;
    }

    private static String fecha(LocalDateTime f) {
        return f == null ? "" : Fechas.FECHA_HORA.format(f);
    }
}
