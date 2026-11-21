package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.model.Cliente;
import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Acceso a clientes. Se completa en la Fase 6 (Fiados). */
public class ClienteDao {

    private static final String SELECT = "SELECT id, nombre, telefono, notas, saldo, activo FROM clientes ";

    public List<Cliente> listarActivos(Connection c) throws SQLException {
        List<Cliente> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE activo = 1 ORDER BY nombre COLLATE NOCASE");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    public Optional<Cliente> buscarPorId(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    public long insertar(Connection c, Cliente cl) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO clientes (nombre, telefono, notas, saldo, activo, creado_en) VALUES (?, ?, ?, 0, 1, ?)")) {
            ps.setString(1, cl.getNombre());
            ps.setString(2, Dao.vacioANulo(cl.getTelefono()));
            ps.setString(3, Dao.vacioANulo(cl.getNotas()));
            ps.setString(4, Fechas.ahoraTexto());
            ps.executeUpdate();
        }
        long id = Dao.ultimoId(c);
        cl.setId(id);
        return id;
    }

    /** Suma {@code delta} al saldo (positivo = debe más) y regresa el saldo nuevo. */
    public long sumarSaldo(Connection c, long clienteId, long delta) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE clientes SET saldo = saldo + ? WHERE id = ?")) {
            ps.setLong(1, delta);
            ps.setLong(2, clienteId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT saldo FROM clientes WHERE id = ?")) {
            ps.setLong(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static Cliente mapear(ResultSet rs) throws SQLException {
        Cliente c = new Cliente();
        c.setId(rs.getLong("id"));
        c.setNombre(rs.getString("nombre"));
        c.setTelefono(rs.getString("telefono"));
        c.setNotas(rs.getString("notas"));
        c.setSaldo(rs.getLong("saldo"));
        c.setActivo(rs.getInt("activo") == 1);
        return c;
    }
}
