package com.fgsistemas.pos.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Impresora de tickets y lector de código de barras de ESTA computadora.
 * <p>
 * DECISION: se guarda en {@code <datos>/hardware.properties} y no en la BD: el nombre de la impresora es de
 * cada equipo de Windows; así restaurar un respaldo hecho en otra computadora no deja una impresora que
 * aquí no existe.
 */
public class Hardware {

    private static final Logger LOG = Logger.getLogger(Hardware.class.getName());
    static final String ARCHIVO = "hardware.properties";

    /** Cuándo se imprime el ticket al cobrar. */
    public enum ModoTicket {
        AUTOMATICO("Imprimir siempre al cobrar"),
        PREGUNTAR("Preguntar en cada venta"),
        NUNCA("No imprimir al cobrar (solo reimprimir)");

        private final String etiqueta;

        ModoTicket(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    private String impresora = "";
    private int anchoPapelMm = 58;
    private ModoTicket modo = ModoTicket.AUTOMATICO;
    private boolean cortarPapel = true;
    private boolean abrirCajon;
    private boolean sinAcentos;
    private boolean capturarLector = true;

    public static Path archivo() {
        return AppConfig.carpetaDatos().resolve(ARCHIVO);
    }

    /** Lee la configuración; si no existe o está dañada, regresa los valores por defecto (sin impresora). */
    public static Hardware cargar() {
        Hardware h = new Hardware();
        Path a = archivo();
        if (!Files.exists(a)) {
            return h;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(a)) {
            p.load(in);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo leer " + a, e);
            return h;
        }
        h.impresora = p.getProperty("impresora", "").trim();
        h.anchoPapelMm = "80".equals(p.getProperty("ancho_papel_mm", "58").trim()) ? 80 : 58;
        try {
            h.modo = ModoTicket.valueOf(p.getProperty("modo_ticket", ModoTicket.AUTOMATICO.name()).trim());
        } catch (IllegalArgumentException e) {
            h.modo = ModoTicket.AUTOMATICO;
        }
        h.cortarPapel = Boolean.parseBoolean(p.getProperty("cortar_papel", "true").trim());
        h.abrirCajon = Boolean.parseBoolean(p.getProperty("abrir_cajon", "false").trim());
        h.sinAcentos = Boolean.parseBoolean(p.getProperty("sin_acentos", "false").trim());
        h.capturarLector = Boolean.parseBoolean(p.getProperty("capturar_lector", "true").trim());
        return h;
    }

    public void guardar() throws IOException {
        Properties p = new Properties();
        p.setProperty("impresora", impresora == null ? "" : impresora);
        p.setProperty("ancho_papel_mm", String.valueOf(anchoPapelMm));
        p.setProperty("modo_ticket", modo.name());
        p.setProperty("cortar_papel", String.valueOf(cortarPapel));
        p.setProperty("abrir_cajon", String.valueOf(abrirCajon));
        p.setProperty("sin_acentos", String.valueOf(sinAcentos));
        p.setProperty("capturar_lector", String.valueOf(capturarLector));
        Path a = archivo();
        Files.createDirectories(a.getParent());
        Path tmp = a.resolveSibling(ARCHIVO + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            p.store(out, "Impresora de tickets y lector de codigo de barras de esta computadora");
        }
        Files.move(tmp, a, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Caracteres por renglón con la fuente normal: 32 en papel de 58 mm, 48 en papel de 80 mm. */
    public int columnas() {
        return anchoPapelMm == 80 ? 48 : 32;
    }

    public boolean hayImpresora() {
        return impresora != null && !impresora.isBlank();
    }

    public String getImpresora() {
        return impresora;
    }

    public void setImpresora(String impresora) {
        this.impresora = impresora == null ? "" : impresora.trim();
    }

    public int getAnchoPapelMm() {
        return anchoPapelMm;
    }

    public void setAnchoPapelMm(int anchoPapelMm) {
        this.anchoPapelMm = anchoPapelMm == 80 ? 80 : 58;
    }

    public ModoTicket getModo() {
        return modo;
    }

    public void setModo(ModoTicket modo) {
        this.modo = modo == null ? ModoTicket.AUTOMATICO : modo;
    }

    public boolean isCortarPapel() {
        return cortarPapel;
    }

    public void setCortarPapel(boolean cortarPapel) {
        this.cortarPapel = cortarPapel;
    }

    public boolean isAbrirCajon() {
        return abrirCajon;
    }

    public void setAbrirCajon(boolean abrirCajon) {
        this.abrirCajon = abrirCajon;
    }

    public boolean isSinAcentos() {
        return sinAcentos;
    }

    public void setSinAcentos(boolean sinAcentos) {
        this.sinAcentos = sinAcentos;
    }

    public boolean isCapturarLector() {
        return capturarLector;
    }

    public void setCapturarLector(boolean capturarLector) {
        this.capturarLector = capturarLector;
    }
}
