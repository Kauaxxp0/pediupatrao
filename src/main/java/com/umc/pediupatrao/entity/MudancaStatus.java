package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;

/** Registro de cada transição de status, mantido dentro do próprio pedido. */
public class MudancaStatus {

    private StatusPedido statusAnterior;
    private StatusPedido statusNovo;
    private String usuario;
    private String perfil;
    private LocalDateTime dataHora;

    public MudancaStatus() {
    }

    public MudancaStatus(StatusPedido statusAnterior, StatusPedido statusNovo,
            String usuario, String perfil, LocalDateTime dataHora) {
        this.statusAnterior = statusAnterior;
        this.statusNovo = statusNovo;
        this.usuario = usuario;
        this.perfil = perfil;
        this.dataHora = dataHora;
    }

    public StatusPedido getStatusAnterior() {
        return statusAnterior;
    }

    public StatusPedido getStatusNovo() {
        return statusNovo;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getPerfil() {
        return perfil;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }
}
