package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Hardware;
import com.fgsistemas.pos.model.MetodoPago;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.model.Venta;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketTest {

    private static Negocio negocio() {
        Negocio n = new Negocio();
        n.setNombre("Tienda de la Esquinita");
        n.setDireccion("Av. Juárez 10");
        n.setTelefono("555 123 4567");
        n.setMensajeTicket("¡Gracias por su compra!");
        return n;
    }

    private static Venta.Resumen venta(long total, boolean cancelada) {
        return new Venta.Resumen(7, 15, LocalDateTime.of(2026, 11, 23, 14, 5), "Ana", null, total,
                cancelada ? "CANCELADA" : "COMPLETADA", "Efectivo", null);
    }

    private static List<Venta.Renglon> renglones() {
        return List.of(
                new Venta.Renglon("750100", "Refresco de cola 600 ml con nombre muy largo para partir", TipoVenta.PIEZA,
                        "pza", 2000, 1800, 3600),
                new Venta.Renglon("FRIJOL", "Frijol a granel", TipoVenta.PESO, "kg", 500, 3000, 1500));
    }

    @Test
    void ticketConTotalesCambioYDescuento() {
        Hardware hw = new Hardware();
        List<Venta.Pago> pagos = List.of(new Venta.Pago(MetodoPago.EFECTIVO, 4600, 5000L, 400L, null));
        EscPos t = TicketService.armar(negocio(), venta(4600, false), renglones(), pagos, hw, false, false);
        String v = t.vistaPrevia();
        assertTrue(v.contains("Folio #15"), v);
        assertTrue(v.contains("Atendió: Ana"), v);
        assertTrue(v.contains("Descuento"), v);       // 5,100 - 4,600 = 500 de descuento
        assertTrue(v.contains("TOTAL"), v);
        assertTrue(v.contains("Su cambio"), v);
        assertTrue(v.contains("3 artículos"), v);     // 2 piezas + 1 producto a granel
        assertTrue(v.contains("¡Gracias por su compra!"), v);
        assertFalse(v.contains("REIMPRESIÓN"), v);
        for (String linea : v.split("\n")) {
            assertTrue(linea.length() <= hw.columnas() || linea.contains("corte"), "No cabe: [" + linea + "]");
        }
    }

    @Test
    void reimpresionYCancelada() {
        Hardware hw = new Hardware();
        hw.setAnchoPapelMm(80);
        EscPos t = TicketService.armar(negocio(), venta(5100, true), renglones(), List.of(), hw, true, false);
        String v = t.vistaPrevia();
        assertTrue(v.contains("REIMPRESIÓN"));
        assertTrue(v.contains("VENTA CANCELADA"));
        assertTrue(v.lines().anyMatch(l -> l.length() == 48));
    }

    @Test
    void bytesEscPosConAcentosYCajon() {
        Hardware hw = new Hardware();
        hw.setAbrirCajon(true);
        byte[] b = TicketService.armar(negocio(), venta(5100, false), renglones(), List.of(), hw, false, true).bytes();
        assertEquals(0x1B, b[0]);   // ESC @ (reiniciar)
        assertEquals('@', b[1]);
        assertEquals('t', b[3]);    // ESC t 2 (PC850)
        assertEquals(2, b[4]);
        assertEquals('p', b[6]);    // ESC p: abrir cajón antes del texto
        assertTrue(contiene(b, (byte) 0xA0)); // á de "Juárez" en PC850
        assertTrue(contiene(b, (byte) 0xA1)); // í de "artículos"
        assertTrue(contiene(b, new byte[]{0x1D, 'V', 66, 0})); // corte
    }

    @Test
    void sinAcentosQuitaLosAcentos() {
        EscPos e = new EscPos(32, true);
        assertEquals('a', e.codificar('á'));
        assertEquals('N', e.codificar('Ñ'));
        assertEquals('?', e.codificar('¿'));
        assertEquals(' ', e.codificar(' '));
        EscPos conAcentos = new EscPos(32, false);
        assertEquals(0xA4, conAcentos.codificar('ñ'));
        assertEquals('-', conAcentos.codificar('−'));
    }

    private static boolean contiene(byte[] datos, byte b) {
        for (byte x : datos) {
            if (x == b) {
                return true;
            }
        }
        return false;
    }

    private static boolean contiene(byte[] datos, byte[] patron) {
        outer:
        for (int i = 0; i <= datos.length - patron.length; i++) {
            for (int j = 0; j < patron.length; j++) {
                if (datos[i + j] != patron[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
