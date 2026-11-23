package com.fgsistemas.pos.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Corrige la zona horaria de Java cuando no coincide con la de Windows (Fase 9).
 * <p>
 * Caso real: Java 17 asocia "(UTC-07:00) La Paz, Mazatlán" ({@code Mountain Standard Time (Mexico)}) con
 * {@code America/Chihuahua}, que desde 2022 está en UTC-6: todas las ventas quedaban con una hora de más.
 * Se lee la hora real de Windows (registro {@code ActiveTimeBias}) y, si Java difiere, se usa la zona correcta.
 * DECISION: corrección automática al arrancar (sin pedir nada al usuario); queda en el log.
 */
public final class ZonaHoraria {

    /** Zonas de Windows que el JDK asocia mal desde los cambios de horario de México de 2022. */
    static final Map<String, String> CORRECCIONES = Map.of(
            "Mountain Standard Time (Mexico)", "America/Mazatlan");

    private static final String CLAVE = "HKLM\\SYSTEM\\CurrentControlSet\\Control\\TimeZoneInformation";

    private ZonaHoraria() {
    }

    /**
     * Ajusta {@link TimeZone#setDefault} si hace falta. Se llama al arrancar, antes de usar cualquier fecha.
     *
     * @return qué se corrigió (para el log), o null si no hizo falta
     */
    public static String corregir() {
        try {
            if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
                return null;
            }
            String forzada = System.getProperty("user.timezone");
            if (forzada != null && !forzada.isBlank()) {
                return null; // alguien la fijó a propósito con -Duser.timezone
            }
            String claveWindows = leer("TimeZoneKeyName");
            String bias = leer("ActiveTimeBias");
            if (bias == null) {
                return null;
            }
            int biasMin = (int) Long.decode(bias).longValue();
            TimeZone actual = TimeZone.getDefault();
            TimeZone nueva = elegir(claveWindows, biasMin, actual, System.currentTimeMillis());
            if (nueva == null) {
                return null;
            }
            TimeZone.setDefault(nueva);
            return "Zona horaria corregida: Windows '" + claveWindows + "' (UTC" + offsetTexto(-biasMin) + "), Java tenía "
                    + actual.getID() + "; ahora " + nueva.getID();
        } catch (Exception e) {
            return null; // ante cualquier problema, se deja como estaba
        }
    }

    /**
     * Decide la zona a usar. {@code biasMin} = minutos que hay que SUMAR a la hora local para obtener UTC
     * (UTC-7 → 420), como lo guarda Windows.
     *
     * @return la zona correcta, o null si la actual ya coincide con Windows
     */
    static TimeZone elegir(String claveWindows, int biasMin, TimeZone actual, long ahora) {
        int offsetWindows = -biasMin;
        if (actual.getOffset(ahora) / 60_000 == offsetWindows) {
            return null;
        }
        String iana = claveWindows == null ? null : CORRECCIONES.get(claveWindows.trim());
        if (iana != null) {
            TimeZone t = TimeZone.getTimeZone(iana);
            if (t.getOffset(ahora) / 60_000 == offsetWindows) {
                return t;
            }
        }
        // Sin corrección conocida: la hora fija que dice Windows (no maneja horario de verano, pero es la correcta hoy).
        ZoneOffset fijo = ZoneOffset.ofTotalSeconds(offsetWindows * 60);
        return TimeZone.getTimeZone(ZoneId.ofOffset("GMT", fijo));
    }

    static String offsetTexto(int minutos) {
        return ZoneOffset.ofTotalSeconds(minutos * 60).getId().replace("Z", "+00:00");
    }

    /** Lee un valor de la clave de zona horaria con {@code reg query} (sin dependencias nativas). */
    private static String leer(String valor) throws Exception {
        Process p = new ProcessBuilder("reg", "query", CLAVE, "/v", valor).redirectErrorStream(true).start();
        String encontrado = null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = r.readLine()) != null) {
                String t = linea.trim();
                if (t.startsWith(valor)) {
                    String[] partes = t.split("\\s{2,}|\\t", 3);
                    if (partes.length == 3) {
                        encontrado = partes[2].trim();
                    }
                }
            }
        }
        if (!p.waitFor(3, TimeUnit.SECONDS)) {
            p.destroyForcibly();
        }
        return encontrado;
    }

    /** Para pruebas: instante fijo. */
    static long instante(String iso) {
        return Instant.parse(iso).toEpochMilli();
    }
}
