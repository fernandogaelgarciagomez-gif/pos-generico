package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.model.MetodoPago;
import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Abonos de clientes a su cuenta de fiado. */
public class AbonoDao {

    public record Abono(long id, long clienteId, String clienteNombre, long turnoId, String usuarioNombre, long monto,
                        MetodoPago metodo,
                        long saldoAnterior, long saldoNuevo, LocalDateTime fecha, String notas) {
    }

    public long insertar(Connection c, long clienteId, long turnoId, long usuarioId, long monto, MetodoPago metodo,
                         long saldoAnterior, long saldoNuevo, String notas) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO abonos (cliente_id, turno_id, usuario_id, monto, metodo, saldo_anterior, saldo_nuevo, "
                        + "fecha, notas) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, clienteId);
            ps.setLong(2, turnoId);
            ps.setLong(3, usuarioId);
            ps.setLong(4, monto);
            ps.setString(5, metodo.name());
            ps.setLong(6, saldoAnterior);
            ps.setLong(7, saldoNuevo);
            ps.setString(8, Fechas.ahoraTexto());
            ps.setString(9, Dao.vacioANulo(notas));
            ps.executeUpdate();
        }
        return Dao.ultimoId(c);
    }

    public List<Abono> deCliente(Connection c, long clienteId) throws SQLException {
        return listar(c, "a.cliente_id = ?", clienteId);
    }

    /** Abonos registrados en un turno (para el corte, Fase 7). */
    public List<Abono> delTurno(Connection c, long turnoId) throws SQLException {
        return listar(c, "a.turno_id = ?", turnoId);
    }

    private List<Abono> listar(Connection c, String condicion, long valor) throws SQLException {
        List<Abono> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT a.id, a.cliente_id, cl.nombre, a.turno_id, u.nombre, a.monto, a.metodo, a.saldo_anterior, "
                        + "a.saldo_nuevo, a.fecha, a.notas FROM abonos a JOIN usuarios u ON u.id = a.usuario_id "
                        + "JOIN clientes cl ON cl.id = a.cliente_id "
                        + "WHERE " + condicion + " ORDER BY a.fecha, a.id")) {
            ps.setLong(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Abono(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getString(5),
                            rs.getLong(6),
                            MetodoPago.valueOf(rs.getString(7)), rs.getLong(8), rs.getLong(9),
                            Fechas.deTexto(rs.getString(10)), rs.getString(11)));
                }
            }
        }
        return lista;
    }
}
