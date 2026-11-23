package com.fgsistemas.pos.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodigoBarrasTest {

    @Test
    void limpiaCaracteresDeControlDelLector() {
        assertEquals("7501055363063", CodigoBarras.limpiar("\u00027501055363063\r\n"));
        assertEquals("ABC-1", CodigoBarras.limpiar("  ABC-1\t"));
        assertEquals("", CodigoBarras.limpiar(null));
    }

    @Test
    void validaDigitoVerificador() {
        assertTrue(CodigoBarras.digitoVerificadorValido("7501055363063"));   // EAN-13
        assertTrue(CodigoBarras.digitoVerificadorValido("036000291452"));    // UPC-A
        assertTrue(CodigoBarras.digitoVerificadorValido("96385074"));        // EAN-8
        assertFalse(CodigoBarras.digitoVerificadorValido("7501055363064"));
        assertFalse(CodigoBarras.digitoVerificadorValido("12345"));
        assertFalse(CodigoBarras.digitoVerificadorValido("ABC"));
    }

    @Test
    void tipoDeCodigo() {
        assertEquals("EAN-13", CodigoBarras.tipo("7501055363063"));
        assertEquals("UPC-A", CodigoBarras.tipo("036000291452"));
        assertTrue(CodigoBarras.tipo("COCA600").startsWith("Código interno"));
    }

    @Test
    void variantesConYSinCeroInicial() {
        // UPC-A leído como EAN-13 (con 0 adelante) encuentra el producto dado de alta con 12 dígitos, y al revés.
        assertEquals(List.of("0036000291452", "036000291452", "00036000291452"),
                CodigoBarras.variantes("0036000291452"));
        assertTrue(CodigoBarras.variantes("036000291452").contains("0036000291452"));
        assertEquals(List.of("COCA600"), CodigoBarras.variantes("COCA600"));
        assertEquals(List.of("123"), CodigoBarras.variantes("123"));
    }
}
