package com.fgsistemas.pos.model;

import java.time.LocalDateTime;

public class Turno {

    public enum Estado { ABIERTO, CERRADO }

    private long id;
    private long usuarioId;
    private LocalDateTime abiertoEn;
    private long fondoInicial; // centavos
    private LocalDateTime cerradoEn;
    private Estado estado = Estado.ABIERTO;
    private String usuarioNombre; // dato de consulta (JOIN), no se guarda

    public String getUsuarioNombre() {
        return usuarioNombre;
    }

    public void setUsuarioNombre(String usuarioNombre) {
        this.usuarioNombre = usuarioNombre;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public LocalDateTime getAbiertoEn() {
        return abiertoEn;
    }

    public void setAbiertoEn(LocalDateTime abiertoEn) {
        this.abiertoEn = abiertoEn;
    }

    public long getFondoInicial() {
        return fondoInicial;
    }

    public void setFondoInicial(long fondoInicial) {
        this.fondoInicial = fondoInicial;
    }

    public LocalDateTime getCerradoEn() {
        return cerradoEn;
    }

    public void setCerradoEn(LocalDateTime cerradoEn) {
        this.cerradoEn = cerradoEn;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }
}
