package com.fgsistemas.pos.model;

/** Fila única (id = 1) con los datos y la configuración del negocio. */
public class Negocio {

    private String nombre;
    private String direccion;
    private String telefono;
    private String rfc;
    private String mensajeTicket = "¡Gracias por su compra!";
    private String logoPath;
    private long stockMinimoDefault = 5000;
    private boolean permitirStockNegativo;
    private boolean metodoTarjeta = true;
    private boolean metodoTransferencia = true;
    private boolean metodoFiado = true;
    private int descuentoMaximoPct = 20;
    private boolean respaldoAutomatico = true;
    private int diasConservarRespaldos = 30;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getRfc() {
        return rfc;
    }

    public void setRfc(String rfc) {
        this.rfc = rfc;
    }

    public String getMensajeTicket() {
        return mensajeTicket;
    }

    public void setMensajeTicket(String mensajeTicket) {
        this.mensajeTicket = mensajeTicket;
    }

    public String getLogoPath() {
        return logoPath;
    }

    public void setLogoPath(String logoPath) {
        this.logoPath = logoPath;
    }

    public long getStockMinimoDefault() {
        return stockMinimoDefault;
    }

    public void setStockMinimoDefault(long stockMinimoDefault) {
        this.stockMinimoDefault = stockMinimoDefault;
    }

    public boolean isPermitirStockNegativo() {
        return permitirStockNegativo;
    }

    public void setPermitirStockNegativo(boolean permitirStockNegativo) {
        this.permitirStockNegativo = permitirStockNegativo;
    }

    public boolean isMetodoTarjeta() {
        return metodoTarjeta;
    }

    public void setMetodoTarjeta(boolean metodoTarjeta) {
        this.metodoTarjeta = metodoTarjeta;
    }

    public boolean isMetodoTransferencia() {
        return metodoTransferencia;
    }

    public void setMetodoTransferencia(boolean metodoTransferencia) {
        this.metodoTransferencia = metodoTransferencia;
    }

    public boolean isMetodoFiado() {
        return metodoFiado;
    }

    public void setMetodoFiado(boolean metodoFiado) {
        this.metodoFiado = metodoFiado;
    }

    public int getDescuentoMaximoPct() {
        return descuentoMaximoPct;
    }

    public void setDescuentoMaximoPct(int descuentoMaximoPct) {
        this.descuentoMaximoPct = descuentoMaximoPct;
    }

    public boolean isRespaldoAutomatico() {
        return respaldoAutomatico;
    }

    public void setRespaldoAutomatico(boolean respaldoAutomatico) {
        this.respaldoAutomatico = respaldoAutomatico;
    }

    public int getDiasConservarRespaldos() {
        return diasConservarRespaldos;
    }

    public void setDiasConservarRespaldos(int diasConservarRespaldos) {
        this.diasConservarRespaldos = diasConservarRespaldos;
    }
}
