package com.fgsistemas.pos.util;

import org.junit.jupiter.api.Test;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Fase 9: Java 17 pone Mazatlán (UTC-7) como Chihuahua (UTC-6 desde 2022); se corrige al arrancar. */
class ZonaHorariaTest {

    private static final long NOVIEMBRE_2026 = ZonaHoraria.instante("2026-11-23T15:00:00Z");

    @Test
    void mazatlanMalAsociadoAChihuahuaSeCorrige() {
        TimeZone t = ZonaHoraria.elegir("Mountain Standard Time (Mexico)", 420,
                TimeZone.getTimeZone("America/Chihuahua"), NOVIEMBRE_2026);
        assertEquals("America/Mazatlan", t.getID());
        assertEquals(-7 * 3_600_000, t.getOffset(NOVIEMBRE_2026));
    }

    @Test
    void siYaCoincideNoCambiaNada() {
        assertNull(ZonaHoraria.elegir("Central Standard Time (Mexico)", 360,
                TimeZone.getTimeZone("America/Mexico_City"), NOVIEMBRE_2026));
    }

    @Test
    void sinCorreccionConocidaUsaLaHoraDeWindows() {
        TimeZone t = ZonaHoraria.elegir("Zona inventada", 300, TimeZone.getTimeZone("America/Mexico_City"), NOVIEMBRE_2026);
        assertEquals(-5 * 3_600_000, t.getOffset(NOVIEMBRE_2026));
    }
}
