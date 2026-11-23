package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.model.MetodoPago;
import com.fgsistemas.pos.model.ResumenCaja;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Calcula las cifras de caja de un turno o de un día completo (todos los turnos). */
public class ResumenDao {

    /** De qué se calcula: un turno, o todo un día. */
    public record Filtro(Long turnoId, LocalDate dia) {

        public static Filtro turno(long turnoId) {
            return new Filtro(turnoId, null);
        }

        public static Filtro dia(LocalDate dia) {
            return new Filtro(null, dia);
        }

        /** Condición SQL para la tabla con alias {@code alias} (columnas turno_id y fecha). */
        String donde(String alias) {
            return turnoId != null ? alias + ".turno_id = ?" : alias + ".fecha >= ? AND " + alias + ".fecha < ?";
        }

        /** Pone los parámetros de {@link #donde} a partir de la posición {@code desde}; regresa la siguiente. */
        int parametros(PreparedStatement ps, int desde) throws SQLException {
            if (turnoId != null) {
                ps.setLong(desde, turnoId);
                return desde + 1;
            }
            ps.setString(desde, Fechas.aTexto(dia.atStartOfDay()));
            ps.setString(desde + 1, Fechas.aTexto(dia.plusDays(1).atStartOfDay()));
            return desde + 2;
        }
    }

    public ResumenCaja calcular(Connection c, Filtro f, boolean conGanancia) throws SQLException {
        long fondo = fondo(c, f);

        // Pagos por método (solo ventas completadas)
        Map<MetodoPago, Long> ventas = new EnumMap<>(MetodoPago.class);
        Map<MetodoPago, Integer> num = new EnumMap<>(MetodoPago.class);
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT p.metodo, SUM(p.monto), COUNT(DISTINCT v.id) FROM venta_pagos p "
                        + "JOIN ventas v ON v.id = p.venta_id WHERE v.estado = 'COMPLETADA' AND " + f.donde("v")
                        + " GROUP BY p.metodo")) {
            f.parametros(ps, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MetodoPago m = MetodoPago.valueOf(rs.getString(1));
                    ventas.put(m, rs.getLong(2));
                    num.put(m, rs.getInt(3));
                }
            }
        }

        // Totales de ventas completadas y canceladas
        int numVentas = 0;
        long total = 0;
        long descuentos = 0;
        int numCanceladas = 0;
        long montoCanceladas = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT v.estado, COUNT(*), COALESCE(SUM(v.total), 0), COALESCE(SUM(v.descuento), 0) FROM ventas v "
                        + "WHERE " + f.donde("v") + " GROUP BY v.estado")) {
            f.parametros(ps, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if ("COMPLETADA".equals(rs.getString(1))) {
                        numVentas = rs.getInt(2);
                        total = rs.getLong(3);
                        descuentos = rs.getLong(4);
                    } else {
                        numCanceladas = rs.getInt(2);
                        montoCanceladas = rs.getLong(3);
                    }
                }
            }
        }

        // Abonos por método
        Map<MetodoPago, Long> abonos = new EnumMap<>(MetodoPago.class);
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT a.metodo, SUM(a.monto) FROM abonos a WHERE " + f.donde("a") + " GROUP BY a.metodo")) {
            f.parametros(ps, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    abonos.put(MetodoPago.valueOf(rs.getString(1)), rs.getLong(2));
                }
            }
        }

        // Entradas y retiros de caja
        long entradas = 0;
        long retiros = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT m.tipo, SUM(m.monto) FROM movimientos_caja m WHERE " + f.donde("m") + " GROUP BY m.tipo")) {
            f.parametros(ps, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if ("ENTRADA".equals(rs.getString(1))) {
                        entradas = rs.getLong(2);
                    } else {
                        retiros = rs.getLong(2);
                    }
                }
            }
        }

        Long ganancia = conGanancia ? ganancia(c, f) - descuentos : null;
        return new ResumenCaja(fondo, ventas, num, abonos, entradas, retiros, numVentas, total, descuentos,
                numCanceladas, montoCanceladas, ganancia, top(c, f), porHora(c, f));
    }

    private long fondo(Connection c, Filtro f) throws SQLException {
        String sql = f.turnoId() != null
                ? "SELECT COALESCE(SUM(fondo_inicial), 0) FROM turnos WHERE id = ?"
                : "SELECT COALESCE(SUM(fondo_inicial), 0) FROM turnos WHERE abierto_en >= ? AND abierto_en < ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            f.parametros(ps, 1);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Σ importe − Σ costo × cantidad por renglón (redondeo HALF_UP por renglón, como el importe). */
    private long ganancia(Connection c, Filtro f) throws SQLException {
        long suma = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT d.importe, d.costo_unitario, d.cantidad FROM venta_detalle d JOIN ventas v ON v.id = d.venta_id "
                        + "WHERE v.estado = 'COMPLETADA' AND " + f.donde("v"))) {
            f.parametros(ps, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    suma += rs.getLong(1) - Cantidad.importe(rs.getLong(2), rs.getLong(3));
                }
            }
        }
        // Devoluciones parciales (Fase 9): lo devuelto deja de ser ganancia. El reembolso ya trae el descuento.
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT dd.importe, dd.costo_unitario, dd.cantidad FROM devolucion_detalle dd "
                        + "JOIN devoluciones d ON d.id = dd.devolucion_id WHERE " + f.donde("d"))) {
            f.parametros(ps, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    suma -= rs.getLong(1) - Cantidad.importe(rs.getLong(2), rs.getLong(3));
                }
            }
        }
        return suma;
    }

    /** Los 10 productos con más importe vendido (las unidades se mezclan, por eso se ordena por importe). */
    private List<ResumenCaja.ProductoVendido> top(Connection c, Filtro f) throws SQLException {
        List<ResumenCaja.ProductoVendido> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT p.codigo, p.nombre, p.tipo_venta, p.unidad, SUM(d.cantidad), SUM(d.importe) "
                        + "FROM venta_detalle d JOIN ventas v ON v.id = d.venta_id JOIN productos p ON p.id = d.producto_id "
                        + "WHERE v.estado = 'COMPLETADA' AND " + f.donde("v")
                        + " GROUP BY p.id ORDER BY SUM(d.importe) DESC, p.nombre LIMIT 10")) {
            f.parametros(ps, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new ResumenCaja.ProductoVendido(rs.getString(1), rs.getString(2),
                            TipoVenta.valueOf(rs.getString(3)), rs.getString(4), rs.getLong(5), rs.getLong(6)));
                }
            }
        }
        return lista;
    }

    private long[] porHora(Connection c, Filtro f) throws SQLException {
        long[] horas = new long[24];
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT CAST(substr(v.fecha, 12, 2) AS INTEGER), SUM(v.total) FROM ventas v "
                        + "WHERE v.estado = 'COMPLETADA' AND " + f.donde("v") + " GROUP BY 1")) {
            f.parametros(ps, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int h = rs.getInt(1);
                    if (h >= 0 && h < 24) {
                        horas[h] = rs.getLong(2);
                    }
                }
            }
        }
        return horas;
    }
}
