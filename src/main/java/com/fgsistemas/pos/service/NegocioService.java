package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.NegocioDao;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.util.Sesion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Datos y configuración del negocio (lectura para todos; edición en Configuración, 22-configuracion.md). */
public class NegocioService {

    /** Ruta del logo dentro de la carpeta de datos. */
    public static final String LOGO_RELATIVO = "img/logo.png";
    static final long LOGO_MAX_BYTES = 5L * 1024 * 1024;
    public static final int DIAS_RESPALDO_MAX = 365;

    private final NegocioDao negocioDao = new NegocioDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();

    public Optional<Negocio> obtener() throws SQLException {
        try (Connection c = Database.conectar()) {
            return negocioDao.obtener(c);
        }
    }

    /**
     * Guarda todas las pestañas de Configuración de una vez (Ctrl+S).
     *
     * @throws IllegalArgumentException si algún dato es inválido (mensaje para el usuario)
     */
    public void guardar(Negocio n) throws SQLException {
        Sesion.requiere(Permiso.CONFIGURACION);
        Validacion.exigir(Validacion.nombreNegocio(n.getNombre()));
        validarLargo("La dirección", n.getDireccion(), 200);
        validarLargo("El teléfono", n.getTelefono(), 30);
        validarLargo("El RFC", n.getRfc(), 13);
        validarLargo("El mensaje del ticket", n.getMensajeTicket(), 200);
        if (n.getRfc() != null) {
            n.setRfc(n.getRfc().trim().toUpperCase(Locale.ROOT));
        }
        if (n.getStockMinimoDefault() < 0) {
            throw new IllegalArgumentException("El stock mínimo no puede ser negativo.");
        }
        if (n.getDescuentoMaximoPct() < 0 || n.getDescuentoMaximoPct() > 100) {
            throw new IllegalArgumentException("El descuento máximo debe estar entre 0 y 100 %.");
        }
        if (n.getDiasConservarRespaldos() < 1 || n.getDiasConservarRespaldos() > DIAS_RESPALDO_MAX) {
            throw new IllegalArgumentException("Los días para conservar respaldos deben estar entre 1 y "
                    + DIAS_RESPALDO_MAX + ".");
        }
        long usuarioId = Sesion.usuario().getId();
        Database.enTransaccion(c -> {
            Negocio antes = negocioDao.obtener(c).orElseThrow(() -> new IllegalStateException(
                    "El negocio no está configurado."));
            String cambios = cambios(antes, n);
            if (cambios.isEmpty()) {
                return null;
            }
            negocioDao.actualizar(c, n);
            bitacoraDao.registrar(c, usuarioId, "CONFIGURACION", cambios);
            return null;
        });
    }

    /**
     * Copia la imagen elegida a {@code <datos>/img/logo.png} y la deja como logo del negocio.
     * DECISION: se copia tal cual (PNG o JPG); JavaFX detecta el formato por el contenido, no por la extensión.
     */
    public void cambiarLogo(Path origen) throws SQLException, IOException {
        Sesion.requiere(Permiso.CONFIGURACION);
        if (origen == null || !Files.isRegularFile(origen)) {
            throw new IllegalArgumentException("No se encontró la imagen.");
        }
        String nombre = origen.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(nombre.endsWith(".png") || nombre.endsWith(".jpg") || nombre.endsWith(".jpeg"))) {
            throw new IllegalArgumentException("El logo debe ser una imagen PNG o JPG.");
        }
        if (Files.size(origen) > LOGO_MAX_BYTES) {
            throw new IllegalArgumentException("La imagen pesa más de 5 MB. Usa una más pequeña.");
        }
        Path destino = carpetaDatos().resolve(LOGO_RELATIVO);
        Files.createDirectories(destino.getParent());
        Path temporal = destino.resolveSibling("logo.tmp");
        Files.copy(origen, temporal, StandardCopyOption.REPLACE_EXISTING);
        Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
        long usuarioId = Sesion.usuario().getId();
        Database.enTransaccion(c -> {
            negocioDao.actualizarLogo(c, LOGO_RELATIVO);
            bitacoraDao.registrar(c, usuarioId, "CONFIGURACION", "Logo cambiado (" + origen.getFileName() + ")");
            return null;
        });
    }

    public void quitarLogo() throws SQLException, IOException {
        Sesion.requiere(Permiso.CONFIGURACION);
        long usuarioId = Sesion.usuario().getId();
        Database.enTransaccion(c -> {
            negocioDao.actualizarLogo(c, null);
            bitacoraDao.registrar(c, usuarioId, "CONFIGURACION", "Logo quitado");
            return null;
        });
        Files.deleteIfExists(carpetaDatos().resolve(LOGO_RELATIVO));
    }

    /** Archivo del logo actual, si existe. */
    public static Optional<Path> archivoLogo(Negocio n) {
        if (n == null || n.getLogoPath() == null || n.getLogoPath().isBlank()) {
            return Optional.empty();
        }
        Path ruta = carpetaDatos().resolve(n.getLogoPath());
        return Files.isRegularFile(ruta) ? Optional.of(ruta) : Optional.empty();
    }

    /**
     * Carpeta de datos = la carpeta de la BD abierta. En la app es {@code AppConfig.carpetaDatos()};
     * en las pruebas, su carpeta temporal (así nunca tocan los datos reales).
     */
    static Path carpetaDatos() {
        return Database.archivo().getParent();
    }

    private static void validarLargo(String campo, String valor, int maximo) {
        if (valor != null && valor.trim().length() > maximo) {
            throw new IllegalArgumentException(campo + " admite máximo " + maximo + " caracteres.");
        }
    }

    /** Texto para la bitácora con lo que cambió ("" si nada). */
    private static String cambios(Negocio a, Negocio b) {
        List<String> l = new ArrayList<>();
        comparar(l, "nombre", a.getNombre(), b.getNombre() == null ? null : b.getNombre().trim());
        comparar(l, "dirección", vacio(a.getDireccion()), vacio(b.getDireccion()));
        comparar(l, "teléfono", vacio(a.getTelefono()), vacio(b.getTelefono()));
        comparar(l, "RFC", vacio(a.getRfc()), vacio(b.getRfc()));
        comparar(l, "mensaje del ticket", vacio(a.getMensajeTicket()), vacio(b.getMensajeTicket()));
        comparar(l, "stock mínimo (milésimas)", a.getStockMinimoDefault(), b.getStockMinimoDefault());
        comparar(l, "stock negativo", a.isPermitirStockNegativo(), b.isPermitirStockNegativo());
        comparar(l, "tarjeta", a.isMetodoTarjeta(), b.isMetodoTarjeta());
        comparar(l, "transferencia", a.isMetodoTransferencia(), b.isMetodoTransferencia());
        comparar(l, "fiado", a.isMetodoFiado(), b.isMetodoFiado());
        comparar(l, "descuento máximo %", a.getDescuentoMaximoPct(), b.getDescuentoMaximoPct());
        comparar(l, "respaldo automático", a.isRespaldoAutomatico(), b.isRespaldoAutomatico());
        comparar(l, "días de respaldos", a.getDiasConservarRespaldos(), b.getDiasConservarRespaldos());
        return String.join(" · ", l);
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void comparar(List<String> l, String campo, Object antes, Object despues) {
        if (!Objects.equals(antes, despues)) {
            l.add(campo + ": " + texto(antes) + " → " + texto(despues));
        }
    }

    private static String texto(Object o) {
        if (o == null) {
            return "(vacío)";
        }
        if (o instanceof Boolean b) {
            return b ? "sí" : "no";
        }
        return o.toString();
    }
}
