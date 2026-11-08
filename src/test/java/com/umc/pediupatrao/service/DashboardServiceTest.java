package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.dashboard.DashboardDtos.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.OrdenacaoProdutos;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.ProdutoVendido;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.RankingProdutos;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.Resumo;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.SerieVendas;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.SituacaoPedidos;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 8);

    @Mock
    private MongoTemplate mongoTemplate;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        service = new DashboardService(mongoTemplate, clock);
    }

    private static Pedido pedido(LocalDateTime entrada, TipoEntrega tipo, String produto, String preco, int qtd) {
        return Pedido.registrar("c1", "Maria", tipo, null,
                List.of(new ItemPedido(produto, produto, new BigDecimal(preco), qtd)), "atendente01", "ATENDENTE", entrada);
    }

    private static Pedido cancelado(LocalDateTime entrada, String preco) {
        Pedido p = pedido(entrada, TipoEntrega.ENTREGA, "Pizza Calabresa", preco, 1);
        p.cancelar("Cliente desistiu do pedido", "gerente01", "GERENTE", entrada.plusMinutes(5));
        return p;
    }

    private FiltroDashboard periodo(String inicio, String fim) {
        return FiltroDashboard.de(LocalDate.parse(inicio), LocalDate.parse(fim), null, null, HOJE);
    }

    @Test
    void resumoIgnoraCanceladosNoFaturamentoECalculaComparativo() {
        LocalDateTime dia = LocalDateTime.of(2026, 11, 5, 19, 0);
        List<Pedido> atuais = List.of(
                pedido(dia, TipoEntrega.ENTREGA, "Pizza Calabresa", "50.00", 2),   // 100
                pedido(dia, TipoEntrega.RETIRADA, "Pizza Marguerita", "50.00", 1), // 50
                cancelado(dia, "80.00"));                                           // não entra
        List<Pedido> anteriores = List.of(pedido(dia.minusDays(10), TipoEntrega.ENTREGA, "Pizza Calabresa", "60.00", 2)); // 120
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(atuais, anteriores);

        Resumo r = service.resumo(periodo("2026-11-01", "2026-11-08"));

        assertThat(r.faturamento()).isEqualByComparingTo("150.00");
        assertThat(r.totalPedidos()).isEqualTo(3);
        assertThat(r.pedidosValidos()).isEqualTo(2);
        assertThat(r.ticketMedio()).isEqualByComparingTo("75.00");
        assertThat(r.pedidosCancelados()).isEqualTo(1);
        assertThat(r.taxaCancelamento()).isEqualByComparingTo("33.3");
        assertThat(r.itensVendidos()).isEqualTo(3);
        assertThat(r.pedidosEntrega()).isEqualTo(2);
        assertThat(r.pedidosRetirada()).isEqualTo(1);
        assertThat(r.comparativo().faturamentoAnterior()).isEqualByComparingTo("120.00");
        assertThat(r.comparativo().variacaoFaturamento()).isEqualByComparingTo("25.0"); // 150 vs 120
        assertThat(r.comparativo().periodoAnterior().fim()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void variacaoEhNulaQuandoNaoHaBaseDeComparacao() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class)))
                .thenReturn(List.of(pedido(LocalDateTime.of(2026, 11, 7, 12, 0), TipoEntrega.ENTREGA, "Pizza", "40.00", 1)), List.of());

        Resumo r = service.resumo(periodo("2026-11-07", "2026-11-08"));

        assertThat(r.comparativo().variacaoFaturamento()).isNull();
        assertThat(r.comparativo().faturamentoAnterior()).isEqualByComparingTo("0");
    }

    @Test
    void resumoSemPedidosRetornaZerosSemErro() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of());

        Resumo r = service.resumo(periodo("2026-11-01", "2026-11-08"));

        assertThat(r.faturamento()).isEqualByComparingTo("0");
        assertThat(r.ticketMedio()).isEqualByComparingTo("0");
        assertThat(r.taxaCancelamento()).isEqualByComparingTo("0");
    }

    @Test
    void vendasPorDiaIncluiDiasSemVendaComZero() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(
                pedido(LocalDateTime.of(2026, 11, 2, 20, 0), TipoEntrega.ENTREGA, "Pizza", "40.00", 1),
                pedido(LocalDateTime.of(2026, 11, 2, 21, 0), TipoEntrega.ENTREGA, "Pizza", "60.00", 1),
                pedido(LocalDateTime.of(2026, 11, 4, 12, 0), TipoEntrega.ENTREGA, "Pizza", "30.00", 1),
                cancelado(LocalDateTime.of(2026, 11, 4, 13, 0), "99.00")));

        SerieVendas serie = service.vendas(periodo("2026-11-01", "2026-11-04"), Agrupamento.DIA);

        assertThat(serie.pontos()).hasSize(4);
        assertThat(serie.pontos().get(0).pedidos()).isZero();
        assertThat(serie.pontos().get(1).rotulo()).isEqualTo("02/11");
        assertThat(serie.pontos().get(1).faturamento()).isEqualByComparingTo("100.00");
        assertThat(serie.pontos().get(3).pedidos()).isEqualTo(1); // cancelado não conta
    }

    @Test
    void vendasPorSemanaAgrupaAPartirDaSegundaFeira() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(
                pedido(LocalDateTime.of(2026, 11, 3, 20, 0), TipoEntrega.ENTREGA, "Pizza", "40.00", 1),   // ter
                pedido(LocalDateTime.of(2026, 11, 8, 20, 0), TipoEntrega.ENTREGA, "Pizza", "60.00", 1))); // dom

        SerieVendas serie = service.vendas(periodo("2026-11-01", "2026-11-08"), Agrupamento.SEMANA);

        assertThat(serie.pontos()).extracting(p -> p.inicio().toString()).containsExactly("2026-10-26", "2026-11-02");
        assertThat(serie.pontos().get(1).faturamento()).isEqualByComparingTo("100.00");
    }

    @Test
    void rankingDeProdutosPorQuantidadeEPorFaturamento() {
        LocalDateTime dia = LocalDateTime.of(2026, 11, 6, 20, 0);
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(
                pedido(dia, TipoEntrega.ENTREGA, "Esfiha", "6.50", 10),          // 10 un / 65,00
                pedido(dia, TipoEntrega.ENTREGA, "Pizza Portuguesa", "54.00", 2), // 2 un / 108,00
                cancelado(dia, "54.00")));                                       // ignorado

        RankingProdutos porQtd = service.produtosMaisVendidos(periodo("2026-11-01", "2026-11-08"), 5, OrdenacaoProdutos.QUANTIDADE);
        RankingProdutos porValor = service.produtosMaisVendidos(periodo("2026-11-01", "2026-11-08"), 5, OrdenacaoProdutos.FATURAMENTO);

        assertThat(porQtd.produtos()).extracting(ProdutoVendido::nome).containsExactly("Esfiha", "Pizza Portuguesa");
        assertThat(porQtd.totalItens()).isEqualTo(12);
        assertThat(porQtd.produtos().get(0).participacao()).isEqualByComparingTo("83.3");
        assertThat(porValor.produtos()).extracting(ProdutoVendido::nome).containsExactly("Pizza Portuguesa", "Esfiha");
        assertThat(porValor.produtos().get(0).faturamento()).isEqualByComparingTo("108.00");
    }

    @Test
    void situacaoContaPorStatusEMedeTempoAteSaida() {
        LocalDateTime entrada = LocalDateTime.of(2026, 11, 6, 20, 0);
        Pedido entregue = pedido(entrada, TipoEntrega.ENTREGA, "Pizza", "50.00", 1);
        entregue.avancarStatus(StatusPedido.EM_PREPARACAO, "a", "ATENDENTE", entrada.plusMinutes(5));
        entregue.avancarStatus(StatusPedido.PRONTO, "a", "ATENDENTE", entrada.plusMinutes(25));
        entregue.avancarStatus(StatusPedido.SAIU_PARA_ENTREGA, "a", "ATENDENTE", entrada.plusMinutes(30));
        Pedido novo = pedido(entrada, TipoEntrega.RETIRADA, "Pizza", "40.00", 1);
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(entregue, novo, cancelado(entrada, "10.00")));

        SituacaoPedidos s = service.situacao(periodo("2026-11-01", "2026-11-08"));

        assertThat(s.total()).isEqualTo(3);
        assertThat(s.emAndamento()).isEqualTo(2);
        assertThat(s.porStatus()).hasSize(StatusPedido.values().length);
        assertThat(s.porStatus()).filteredOn(q -> q.status() == StatusPedido.CANCELADO)
                .singleElement().satisfies(q -> assertThat(q.quantidade()).isEqualTo(1));
        assertThat(s.tempoMedioAteSaidaMinutos()).isEqualTo(30L);
        assertThat(s.tempoMedioAteFinalizacaoMinutos()).isNull();
    }

    @Test
    void filtroUsaUltimos30DiasPorPadraoEValidaPeriodo() {
        FiltroDashboard padrao = FiltroDashboard.de(null, null, null, null, HOJE);
        assertThat(padrao.inicio()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(padrao.fim()).isEqualTo(HOJE);
        assertThat(padrao.dias()).isEqualTo(30);

        assertThatThrownBy(() -> FiltroDashboard.de(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 1), null, null, HOJE))
                .isInstanceOf(ParametroInvalidoException.class).hasMessageContaining("anterior");
        assertThatThrownBy(() -> FiltroDashboard.de(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 8), null, null, HOJE))
                .isInstanceOf(ParametroInvalidoException.class).hasMessageContaining("366");
    }

    @Test
    void limiteForaDaFaixaEhRecusado() {
        assertThat(DashboardService.validarLimite(null)).isEqualTo(DashboardService.LIMITE_PADRAO);
        assertThatThrownBy(() -> DashboardService.validarLimite(0)).isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> DashboardService.validarLimite(51)).isInstanceOf(ParametroInvalidoException.class);
    }
}
