package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Respostas dos endpoints do dashboard (somente leitura). */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Periodo(LocalDate inicio, LocalDate fim, long dias) {

        public static Periodo de(FiltroDashboard f) {
            return new Periodo(f.inicio(), f.fim(), f.dias());
        }
    }

    /** RF01 — indicadores gerais. Faturamento considera apenas pedidos não cancelados. */
    public record Resumo(
            Periodo periodo,
            BigDecimal faturamento,
            long totalPedidos,
            long pedidosValidos,
            BigDecimal ticketMedio,
            long pedidosCancelados,
            BigDecimal taxaCancelamento,
            BigDecimal descontosConcedidos,
            long itensVendidos,
            long pedidosEmAndamento,
            long pedidosEntrega,
            long pedidosRetirada,
            Comparativo comparativo,
            LocalDateTime geradoEm) {
    }

    /** Comparação com o período anterior de mesmo tamanho. Variação nula quando não há base. */
    public record Comparativo(
            Periodo periodoAnterior,
            BigDecimal faturamentoAnterior,
            BigDecimal variacaoFaturamento,
            long pedidosAnterior,
            BigDecimal variacaoPedidos,
            BigDecimal ticketMedioAnterior,
            BigDecimal variacaoTicketMedio) {
    }

    /** RF02 — série temporal de vendas. */
    public record SerieVendas(Periodo periodo, Agrupamento agrupamento, List<PontoVendas> pontos) {
    }

    public record PontoVendas(LocalDate inicio, String rotulo, long pedidos, BigDecimal faturamento) {
    }

    public enum Agrupamento {
        DIA, SEMANA, MES
    }

    /** RF02 — distribuição dos pedidos ao longo do dia. */
    public record VendasPorHora(Periodo periodo, List<PontoHora> horas) {
    }

    public record PontoHora(int hora, long pedidos, BigDecimal faturamento) {
    }

    /** RF03 — situação dos pedidos. */
    public record SituacaoPedidos(
            Periodo periodo,
            long total,
            long emAndamento,
            List<QuantidadePorStatus> porStatus,
            Long tempoMedioAteSaidaMinutos,
            Long tempoMedioAteFinalizacaoMinutos) {
    }

    public record QuantidadePorStatus(StatusPedido status, String descricao, long quantidade,
            BigDecimal percentual, BigDecimal valor) {
    }

    /** RF04 — ranking de produtos. */
    public record RankingProdutos(Periodo periodo, OrdenacaoProdutos ordenacao, long totalItens,
            List<ProdutoVendido> produtos) {
    }

    public record ProdutoVendido(int posicao, String produtoId, String nome, long quantidade,
            BigDecimal faturamento, BigDecimal participacao) {
    }

    public enum OrdenacaoProdutos {
        QUANTIDADE, FATURAMENTO
    }

    /** RF03 — últimos pedidos do período. */
    public record PedidoRecente(String id, String clienteNome, TipoEntrega tipoEntrega, StatusPedido status,
            String statusDescricao, BigDecimal valorTotal, int itens, LocalDateTime dataHoraEntrada) {
    }

    /** RF05 — opções disponíveis para os filtros da tela. */
    public record OpcoesFiltro(List<Opcao> status, List<Opcao> tiposEntrega, List<Opcao> agrupamentos,
            FiltroDashboard padrao) {
    }

    public record Opcao(String valor, String descricao) {
    }
}
