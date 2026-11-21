package com.fgsistemas.pos.util;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Fechas en BD como TEXT ISO-8601 con segundos: 2026-11-21T14:30:00. */
public final class Fechas {

    private static final Locale MX = Locale.forLanguageTag("es-MX");

    public static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    public static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", MX);
    public static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy", MX);
    public static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", MX);
    public static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy · HH:mm:ss", MX);

    private static Clock reloj = Clock.systemDefaultZone();

    private Fechas() {
    }

    public static LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
    }

    public static String ahoraTexto() {
        return aTexto(ahora());
    }

    public static String aTexto(LocalDateTime fecha) {
        return fecha == null ? null : ISO.format(fecha);
    }

    public static LocalDateTime deTexto(String texto) {
        return (texto == null || texto.isBlank()) ? null : LocalDateTime.parse(texto, ISO);
    }

    /** Solo para pruebas. */
    public static void setReloj(Clock nuevo) {
        reloj = nuevo == null ? Clock.systemDefaultZone() : nuevo;
    }

    public static Clock reloj() {
        return reloj;
    }
}
