package com.umc.pediupatrao.entity;

public enum TipoOperacao {

    PEDIDO_CRIADO("Inclusão de pedido"),
    PEDIDO_STATUS_ALTERADO("Alteração de status do pedido"),
    PEDIDO_DESCONTO_APLICADO("Aplicação de desconto"),
    PEDIDO_CANCELADO("Cancelamento de pedido"),
    CLIENTE_CRIADO("Inclusão de cliente"),
    CLIENTE_ALTERADO("Alteração de cliente"),
    CLIENTE_EXCLUIDO("Exclusão de cliente"),
    USUARIO_CRIADO("Inclusão de usuário"),
    USUARIO_ALTERADO("Alteração de usuário"),
    USUARIO_EXCLUIDO("Exclusão de usuário");

    private final String descricao;

    TipoOperacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
