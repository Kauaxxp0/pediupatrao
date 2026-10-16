package com.umc.pediupatrao.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.umc.pediupatrao.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PedidoTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 9, 23, 14, 35);

    private Pedido novoPedido(TipoEntrega tipo) {
        List<ItemPedido> itens = List.of(
                new ItemPedido("p1", "Pizza Calabresa", new BigDecimal("49.90"), 2),
                new ItemPedido("p2", "Refrigerante 2L", new BigDecimal("14.00"), 1));
        return Pedido.registrar("c1", "Maria", tipo, null, itens, "atendente01", "ATENDENTE", AGORA);
    }

    @Test
    void registraEntradaResponsavelEValores() {
        Pedido p = novoPedido(TipoEntrega.ENTREGA);

        assertThat(p.getStatus()).isEqualTo(StatusPedido.RECEBIDO);
        assertThat(p.getRegistradoPor()).isEqualTo("atendente01");
        assertThat(p.getDataHoraEntrada()).isEqualTo(AGORA);
        assertThat(p.getSubtotal()).isEqualByComparingTo("113.80");
        assertThat(p.getValorTotal()).isEqualByComparingTo("113.80");
        assertThat(p.getHistoricoStatus()).hasSize(1);
    }

    @Test
    void pedidoSemItensNaoPodeSerRegistrado() {
        assertThatThrownBy(() -> Pedido.registrar("c1", "Maria", TipoEntrega.ENTREGA, null, List.of(),
                "u", "ATENDENTE", AGORA))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void fluxoCompletoDeEntregaRegistraSaidaEFinalizacao() {
        Pedido p = novoPedido(TipoEntrega.ENTREGA);
        p.avancarStatus(StatusPedido.EM_PREPARACAO, "a", "ATENDENTE", AGORA.plusMinutes(1));
        p.avancarStatus(StatusPedido.PRONTO, "a", "ATENDENTE", AGORA.plusMinutes(20));
        p.avancarStatus(StatusPedido.SAIU_PARA_ENTREGA, "gerente01", "GERENTE", AGORA.plusMinutes(25));
        p.avancarStatus(StatusPedido.FINALIZADO, "a", "ATENDENTE", AGORA.plusMinutes(50));

        assertThat(p.getResponsavelSaida()).isEqualTo("gerente01");
        assertThat(p.getDataHoraSaida()).isEqualTo(AGORA.plusMinutes(25));
        assertThat(p.getDataHoraFinalizacao()).isEqualTo(AGORA.plusMinutes(50));
        assertThat(p.getHistoricoStatus()).hasSize(5);
    }

    @Test
    void naoPermiteFinalizarPedidoEmPreparacao() {
        Pedido p = novoPedido(TipoEntrega.ENTREGA);
        p.avancarStatus(StatusPedido.EM_PREPARACAO, "a", "ATENDENTE", AGORA);

        assertThatThrownBy(() -> p.avancarStatus(StatusPedido.FINALIZADO, "a", "ATENDENTE", AGORA))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Transição inválida");
    }

    @Test
    void retiradaNaoPodeSairParaEntrega() {
        Pedido p = novoPedido(TipoEntrega.RETIRADA);
        p.avancarStatus(StatusPedido.EM_PREPARACAO, "a", "ATENDENTE", AGORA);
        p.avancarStatus(StatusPedido.PRONTO, "a", "ATENDENTE", AGORA);

        assertThatThrownBy(() -> p.avancarStatus(StatusPedido.SAIU_PARA_ENTREGA, "a", "ATENDENTE", AGORA))
                .isInstanceOf(RegraNegocioException.class);
        p.avancarStatus(StatusPedido.RETIRADO, "a", "ATENDENTE", AGORA);
        assertThat(p.getResponsavelSaida()).isEqualTo("a");
    }

    @Test
    void statusCanceladoNaoPodeSerDefinidoPelaTransicaoComum() {
        Pedido p = novoPedido(TipoEntrega.ENTREGA);
        assertThatThrownBy(() -> p.avancarStatus(StatusPedido.CANCELADO, "a", "ATENDENTE", AGORA))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void descontoRecalculaTotalNoServidor() {
        Pedido p = novoPedido(TipoEntrega.ENTREGA);
        p.aplicarDesconto(new BigDecimal("10"), "gerente01", AGORA);

        assertThat(p.getValorDesconto()).isEqualByComparingTo("11.38");
        assertThat(p.getValorTotal()).isEqualByComparingTo("102.42");
        assertThat(p.getDescontoAplicadoPor()).isEqualTo("gerente01");
    }

    @Test
    void descontoAcimaDeVintePorCentoEhRecusado() {
        Pedido p = novoPedido(TipoEntrega.ENTREGA);
        assertThatThrownBy(() -> p.aplicarDesconto(new BigDecimal("20.01"), "g", AGORA))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("20");
        assertThatThrownBy(() -> p.aplicarDesconto(BigDecimal.ZERO, "g", AGORA))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void descontoNaoPermitidoAposSaida() {
        Pedido p = novoPedido(TipoEntrega.ENTREGA);
        p.avancarStatus(StatusPedido.EM_PREPARACAO, "a", "ATENDENTE", AGORA);
        p.avancarStatus(StatusPedido.PRONTO, "a", "ATENDENTE", AGORA);
        p.avancarStatus(StatusPedido.SAIU_PARA_ENTREGA, "a", "ATENDENTE", AGORA);

        assertThatThrownBy(() -> p.aplicarDesconto(new BigDecimal("5"), "g", AGORA))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> p.cancelar("Cliente desistiu do pedido", "g", "GERENTE", AGORA))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void cancelamentoExigeJustificativaEPreservaDados() {
        Pedido p = novoPedido(TipoEntrega.ENTREGA);
        assertThatThrownBy(() -> p.cancelar("curta", "g", "GERENTE", AGORA))
                .isInstanceOf(RegraNegocioException.class);

        p.cancelar("Cliente desistiu do pedido", "gerente01", "GERENTE", AGORA);

        assertThat(p.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(p.getCancelamento().getStatusAnterior()).isEqualTo(StatusPedido.RECEBIDO);
        assertThat(p.getItens()).hasSize(2);
        assertThat(p.getValorTotal()).isEqualByComparingTo("113.80");
    }

    @Test
    void pedidoCanceladoNaoAceitaNenhumaAlteracao() {
        Pedido p = novoPedido(TipoEntrega.ENTREGA);
        p.cancelar("Cliente desistiu do pedido", "g", "GERENTE", AGORA);

        assertThatThrownBy(() -> p.avancarStatus(StatusPedido.EM_PREPARACAO, "a", "ATENDENTE", AGORA))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> p.aplicarDesconto(new BigDecimal("5"), "g", AGORA))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> p.cancelar("Outra justificativa longa", "g", "GERENTE", AGORA))
                .isInstanceOf(RegraNegocioException.class);
    }
}
