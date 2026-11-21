package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.model.Corte;
import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Cortes de caja guardados. */
public class CorteDao {

    private static final String SELECT =
            "SELECT co.id, co.turno_id, uc.nombre, ut.nombre, co.fecha, t.abierto_en, co.fondo_inicial, "
                    + "co.ventas_efectivo, co.ventas_tarjeta, co.ventas_transferencia, co.ventas_fiado, "
                    + "co.abonos_efectivo, co.abonos_otros, co.entradas_caja, co.retiros_caja, co.efectivo_esperado, "
                    + "co.efectivo_contado, co.diferencia, co.num_ventas, co.num_canceladas, "
                    + "(SELECT COALESCE(SUM(v.total), 0) FROM ventas v WHERE v.turno_id = co.turno_id "
                    + "AND v.estado = 'CANCELADA'), co.ganancia_estimada, co.notas "
                    + "FROM cortes co JOIN turnos t ON t.id = co.turno_id "
                    + "JOIN usuarios uc ON uc.id = co.usuario_id JOIN usuarios ut ON ut.id = t.usuario_id ";

    public long insertar(Connection c, Corte k, long usuarioId, long ganancia) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO cortes (turno_id, usuario_id, fecha, fondo_inicial, ventas_efectivo, ventas_tarjeta, "
                        + "ventas_transferencia, ventas_fiado, abonos_efectivo, abonos_otros, entradas_caja, "
                        + "retiros_caja, efectivo_esperado, efectivo_contado, diferencia, num_ventas, num_canceladas, "
                        + "ganancia_estimada, notas) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 1;
            ps.setLong(i++, k.turnoId());
            ps.setLong(i++, usuarioId);
            ps.setString(i++, Fechas.aTexto(k.fecha()));
            ps.setLong(i++, k.fondoInicial());
            ps.setLong(i++, k.ventasEfectivo());
            ps.setLong(i++, k.ventasTarjeta());
            ps.setLong(i++, k.ventasTransferencia());
            ps.setLong(i++, k.ventasFiado());
            ps.setLong(i++, k.abonosEfectivo());
            ps.setLong(i++, k.abonosOtros());
            ps.setLong(i++, k.entradasCaja());
            ps.setLong(i++, k.retirosCaja());
            ps.setLong(i++, k.efectivoEsperado());
            ps.setLong(i++, k.efectivoContado());
            ps.setLong(i++, k.diferencia());
            ps.setInt(i++, k.numVentas());
            ps.setInt(i++, k.numCanceladas());
            ps.setLong(i++, ganancia);
            ps.setString(i, Dao.vacioANulo(k.notas()));
            ps.executeUpdate();
        }
        return Dao.ultimoId(c);
    }

    /**
     * Cortes cuya fecha cae en [desde, hasta] (días completos), del más reciente al más antiguo.
     *
     * @param usuarioId quien abrió el turno, o null para todos
     */
    public List<Corte> listar(Connection c, LocalDate desde, LocalDate hasta, Long usuarioId, boolean conGanancia)
            throws SQLException {
        List<Corte> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE co.fecha >= ? AND co.fecha < ? "
                + (usuarioId == null ? "" : "AND t.usuario_id = ? ") + "ORDER BY co.fecha DESC, co.id DESC")) {
            ps.setString(1, Fechas.aTexto(desde.atStartOfDay()));
            ps.setString(2, Fechas.aTexto(hasta.plusDays(1).atStartOfDay()));
            if (usuarioId != null) {
                ps.setLong(3, usuarioId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs, conGanancia));
                }
            }
        }
        return lista;
    }

    public Optional<Corte> obtener(Connection c, long id, boolean conGanancia) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE co.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs, conGanancia)) : Optional.empty();
            }
        }
    }

    private static Corte mapear(ResultSet rs, boolean conGanancia) throws SQLException {
        return new Corte(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                Fechas.deTexto(rs.getString(5)), Fechas.deTexto(rs.getString(6)), rs.getLong(7), rs.getLong(8),
                rs.getLong(9), rs.getLong(10), rs.getLong(11), rs.getLong(12), rs.getLong(13), rs.getLong(14),
                rs.getLong(15), rs.getLong(16), rs.getLong(17), rs.getLong(18), rs.getInt(19), rs.getInt(20),
                rs.getLong(21), conGanancia ? rs.getLong(22) : null, rs.getString(23));
    }
}
