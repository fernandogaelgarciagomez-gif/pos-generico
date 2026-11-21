package com.fgsistemas.pos.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DineroTest {

    @Test
    void formatea() {
        assertEquals("$0.00", Dinero.formatear(0));
        assertEquals("$0.05", Dinero.formatear(5));
        assertEquals("$1,234.50", Dinero.formatear(123450));
        assertEquals("$1,000,000.00", Dinero.formatear(100000000));
        assertEquals("-$10.00", Dinero.formatear(-1000));
    }

    @Test
    void parsea() {
        assertEquals(123450, Dinero.parsear("$1,234.50"));
        assertEquals(123450, Dinero.parsear("1234.5"));
        assertEquals(1200, Dinero.parsear("12"));
        assertEquals(1200, Dinero.parsear(" 12. "));
        assertEquals(50, Dinero.parsear(".5"));
        assertEquals(-1000, Dinero.parsear("-10"));
        assertEquals(0, Dinero.parsear("0"));
    }

    @Test
    void rechazaInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> Dinero.parsear(""));
        assertThrows(IllegalArgumentException.class, () -> Dinero.parsear(null));
        assertThrows(IllegalArgumentException.class, () -> Dinero.parsear("abc"));
        assertThrows(IllegalArgumentException.class, () -> Dinero.parsear("1.234"));
        assertThrows(IllegalArgumentException.class, () -> Dinero.parsear("1.2.3"));
    }

    @Test
    void idaYVuelta() {
        for (long c : new long[]{0, 1, 99, 100, 123456789}) {
            assertEquals(c, Dinero.parsear(Dinero.formatear(c)));
            assertEquals(c, Dinero.parsear(Dinero.paraCampo(c)));
        }
    }

    @Test
    void porcentajeRedondeaHalfUp() {
        assertEquals(1000, Dinero.porcentaje(10000, BigDecimal.TEN));
        // 15% de $0.10 = 1.5 centavos → 2
        assertEquals(2, Dinero.porcentaje(10, new BigDecimal("15")));
    }
}
