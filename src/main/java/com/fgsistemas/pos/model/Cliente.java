package com.fgsistemas.pos.model;

import com.fgsistemas.pos.util.Dinero;

import java.time.LocalDateTime;

/** Cliente (para fiados). Saldo en centavos: lo que debe (negativo = saldo a favor). */
public class Cliente {

    private long id;
    private String nombre;
    private String telefono;
    private String notas;
    private long saldo;
    private boolean activo = true;
    private LocalDateTime ultimaCompra;
    private LocalDateTime ultimoAbono;

    public LocalDateTime getUltimaCompra() {
        return ultimaCompra;
    }

    public void setUltimaCompra(LocalDateTime ultimaCompra) {
        this.ultimaCompra = ultimaCompra;
    }

    public LocalDateTime getUltimoAbono() {
        return ultimoAbono;
    }

    public void setUltimoAbono(LocalDateTime ultimoAbono) {
        this.ultimoAbono = ultimoAbono;
    }

    /** "$150.00", o "A favor $50.00" si el saldo es negativo. */
    public String saldoTexto() {
        return textoSaldo(saldo);
    }

    public static String textoSaldo(long saldo) {
        return saldo < 0 ? "A favor " + Dinero.formatear(-saldo) : Dinero.formatear(saldo);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getNotas() {
        return notas;
    }

    public void setNotas(String notas) {
        this.notas = notas;
    }

    public long getSaldo() {
        return saldo;
    }

    public void setSaldo(long saldo) {
        this.saldo = saldo;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
