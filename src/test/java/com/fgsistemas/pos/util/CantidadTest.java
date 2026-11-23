package com.fgsistemas.pos.util;

import com.fgsistemas.pos.model.TipoVenta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CantidadTest {

    @Test
    void parseaPiezas() {
        assertEquals(3000, Cantidad.parsear("3", TipoVenta.PIEZA));
        assertEquals(3000, Cantidad.parsear("3.000", TipoVenta.PIEZA));
        assertEquals(0, Cantidad.parsear("0", TipoVenta.PIEZA));
    }

    @Test
    void piezaRechazaDecimales() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Cantidad.parsear("1.5", TipoVenta.PIEZA));
        assertEquals("Este producto se vende por pieza.", e.getMessage());
    }

    @Test
    void parseaPesoYFraccion() {
        assertEquals(250, Cantidad.parsear("0.250", TipoVenta.PESO));
        assertEquals(250, Cantidad.parsear(".25", TipoVenta.PESO));
        assertEquals(1500, Cantidad.parsear("1.5", TipoVenta.FRACCION));
        assertEquals(1, Cantidad.parsear("0.001", TipoVenta.PESO));
    }

    @Test
    void rechazaInvalidas() {
        assertThrows(IllegalArgumentException.class, () -> Cantidad.parsear("", TipoVenta.PESO));
        assertThrows(IllegalArgumentException.class, () -> Cantidad.parsear("-1", TipoVenta.PESO));
        // Un código de barras escaneado en el campo de cantidad se rechaza (Fase 9).
        assertThrows(IllegalArgumentException.class, () -> Cantidad.parsear("7501055300846", TipoVenta.PIEZA));
        assertEquals(Cantidad.MAXIMO, Cantidad.parsear("999999", TipoVenta.PIEZA));
        assertThrows(IllegalArgumentException.class, () -> Cantidad.parsear("0.0001", TipoVenta.PESO));
        assertThrows(IllegalArgumentException.class, () -> Cantidad.parsear("dos", TipoVenta.PIEZA));
    }

    @Test
    void formatea() {
        assertEquals("3", Cantidad.formatear(3000, TipoVenta.PIEZA));
        assertEquals("1,250", Cantidad.formatear(1250000, TipoVenta.PIEZA));
        assertEquals("0.250", Cantidad.formatear(250, TipoVenta.PESO));
        assertEquals("12.350 kg", Cantidad.formatear(12350, TipoVenta.PESO, "kg"));
        assertEquals("3 pza", Cantidad.formatear(3000, TipoVenta.PIEZA, "pza"));
        assertEquals("1.500 m", Cantidad.formatear(1500, TipoVenta.FRACCION, null));
    }

    @Test
    void paraCampo() {
        assertEquals("3", Cantidad.paraCampo(3000, TipoVenta.PIEZA));
        assertEquals("0.250", Cantidad.paraCampo(250, TipoVenta.PESO));
    }

    @Test
    void importe() {
        // 3 piezas a $12.50
        assertEquals(3750, Cantidad.importe(1250, 3000));
        // Criterio 14-vender: 0.375 kg a $189.90/kg = $71.21 (71.2125 → HALF_UP)
        assertEquals(7121, Cantidad.importe(18990, 375));
        // 0.5 centavo redondea hacia arriba
        assertEquals(1, Cantidad.importe(1, 500));
    }

    @Test
    void ventaPorImporte() {
        // Criterio 14-vender: $50 de un producto a $120/kg → 0.417 kg → $50.04
        long kg = Cantidad.porImporte(5000, 12000);
        assertEquals(417, kg);
        assertEquals(5004, Cantidad.importe(12000, kg));
        assertThrows(IllegalArgumentException.class, () -> Cantidad.porImporte(5000, 0));
    }
}
