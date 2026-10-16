package com.umc.pediupatrao.entity;

public enum TipoEntrega {

    ENTREGA("Entrega"),
    RETIRADA("Retirada no balcão");

    private final String descricao;

    TipoEntrega(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
