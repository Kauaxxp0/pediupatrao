package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;

public class Cancelamento {

    private String justificativa;
    private String usuario;
    private LocalDateTime dataHora;
    private StatusPedido statusAnterior;

    public Cancelamento() {
    }

    public Cancelamento(String justificativa, String usuario, LocalDateTime dataHora, StatusPedido statusAnterior) {
        this.justificativa = justificativa;
        this.usuario = usuario;
        this.dataHora = dataHora;
        this.statusAnterior = statusAnterior;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public String getUsuario() {
        return usuario;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public StatusPedido getStatusAnterior() {
        return statusAnterior;
    }
}
