package com.umc.pediupatrao.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Fluxo obrigatório do pedido:
 * RECEBIDO -> EM_PREPARACAO -> PRONTO -> (SAIU_PARA_ENTREGA | RETIRADO) -> FINALIZADO.
 * CANCELADO é excepcional e só é alcançado pela operação de cancelamento.
 */
public enum StatusPedido {

    RECEBIDO("Recebido", "bg-secondary"),
    EM_PREPARACAO("Em preparação", "bg-warning text-dark"),
    PRONTO("Pronto", "bg-info text-dark"),
    SAIU_PARA_ENTREGA("Saiu para entrega", "bg-primary"),
    RETIRADO("Retirado", "bg-primary"),
    FINALIZADO("Finalizado", "bg-success"),
    CANCELADO("Cancelado", "bg-danger");

    private final String descricao;
    private final String cssBadge;

    StatusPedido(String descricao, String cssBadge) {
        this.descricao = descricao;
        this.cssBadge = cssBadge;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getCssBadge() {
        return cssBadge;
    }

    /** Próximos status permitidos pelo fluxo normal (não inclui CANCELADO). */
    public Set<StatusPedido> proximos(TipoEntrega tipoEntrega) {
        return switch (this) {
            case RECEBIDO -> EnumSet.of(EM_PREPARACAO);
            case EM_PREPARACAO -> EnumSet.of(PRONTO);
            case PRONTO -> tipoEntrega == TipoEntrega.RETIRADA
                    ? EnumSet.of(RETIRADO)
                    : EnumSet.of(SAIU_PARA_ENTREGA);
            case SAIU_PARA_ENTREGA, RETIRADO -> EnumSet.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> EnumSet.noneOf(StatusPedido.class);
        };
    }

    public boolean podeAvancarPara(StatusPedido destino, TipoEntrega tipoEntrega) {
        return destino != null && proximos(tipoEntrega).contains(destino);
    }

    /** Status que representam a saída do pedido do estabelecimento. */
    public boolean isSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    /** Desconto e cancelamento só são permitidos antes da saída para entrega/retirada. */
    public boolean permiteDescontoOuCancelamento() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }
}
