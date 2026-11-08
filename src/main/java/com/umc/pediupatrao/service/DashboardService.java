package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.dashboard.DashboardDtos.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.Comparativo;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.Opcao;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.OpcoesFiltro;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.OrdenacaoProdutos;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.PedidoRecente;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.Periodo;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.PontoHora;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.PontoVendas;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.ProdutoVendido;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.QuantidadePorStatus;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.RankingProdutos;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.Resumo;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.SerieVendas;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.SituacaoPedidos;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.VendasPorHora;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Dados do dashboard administrativo. Os pedidos são filtrados no MongoDB (período, tipo e status,
 * com índice em dataHoraEntrada) e os indicadores são calculados aqui, onde as regras de negócio
 * ficam explícitas e testáveis. Acesso restrito a ADMIN e GERENTE.
 */
@Service
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
public class DashboardService {

    public static final int LIMITE_PADRAO = 10;
    public static final int LIMITE_MAXIMO = 50;

    private static final Set<StatusPedido> EM_ANDAMENTO = EnumSet.of(StatusPedido.RECEBIDO,
            StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO, StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO);
    private static final Predicate<Pedido> VALIDO = p -> p.getStatus() != StatusPedido.CANCELADO;
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MM/yyyy");

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public DashboardService(MongoTemplate mongoTemplate, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }

    // ===================== RF01 — indicadores gerais =====================

    public Resumo resumo(FiltroDashboard filtro) {
        List<Pedido> atuais = buscar(filtro);
        List<Pedido> anteriores = buscar(filtro.periodoAnterior());

        List<Pedido> validos = atuais.stream().filter(VALIDO).toList();
        BigDecimal faturamento = somarValor(validos);
        BigDecimal ticketMedio = media(faturamento, validos.size());
        long cancelados = atuais.size() - validos.size();

        List<Pedido> validosAnteriores = anteriores.stream().filter(VALIDO).toList();
        BigDecimal faturamentoAnterior = somarValor(validosAnteriores);
        BigDecimal ticketAnterior = media(faturamentoAnterior, validosAnteriores.size());
        Comparativo comparativo = new Comparativo(
                Periodo.de(filtro.periodoAnterior()),
                faturamentoAnterior, variacao(faturamento, faturamentoAnterior),
                validosAnteriores.size(), variacao(BigDecimal.valueOf(validos.size()), BigDecimal.valueOf(validosAnteriores.size())),
                ticketAnterior, variacao(ticketMedio, ticketAnterior));

        return new Resumo(
                Periodo.de(filtro),
                faturamento,
                atuais.size(),
                validos.size(),
                ticketMedio,
                cancelados,
                percentual(cancelados, atuais.size()),
                validos.stream().map(Pedido::getValorDesconto).reduce(BigDecimal.ZERO, BigDecimal::add),
                validos.stream().mapToLong(Pedido::getQuantidadeTotalItens).sum(),
                atuais.stream().filter(p -> EM_ANDAMENTO.contains(p.getStatus())).count(),
                atuais.stream().filter(p -> p.getTipoEntrega() == TipoEntrega.ENTREGA).count(),
                atuais.stream().filter(p -> p.getTipoEntrega() == TipoEntrega.RETIRADA).count(),
                comparativo,
                LocalDateTime.now(clock).withNano(0));
    }

    // ===================== RF02 — análise de vendas =====================

    public SerieVendas vendas(FiltroDashboard filtro, Agrupamento agrupamento) {
        Agrupamento agrup = agrupamento == null ? Agrupamento.DIA : agrupamento;
        Map<LocalDate, List<Pedido>> porBalde = new LinkedHashMap<>();
        // Todos os intervalos do período aparecem, inclusive os sem vendas (valor zero)
        for (LocalDate d = inicioDoBalde(filtro.inicio(), agrup); !d.isAfter(filtro.fim()); d = proximoBalde(d, agrup)) {
            porBalde.put(d, new ArrayList<>());
        }
        for (Pedido p : buscar(filtro)) {
            if (VALIDO.test(p)) {
                porBalde.get(inicioDoBalde(p.getDataHoraEntrada().toLocalDate(), agrup)).add(p);
            }
        }
        List<PontoVendas> pontos = porBalde.entrySet().stream()
                .map(e -> new PontoVendas(e.getKey(), rotulo(e.getKey(), agrup), e.getValue().size(), somarValor(e.getValue())))
                .toList();
        return new SerieVendas(Periodo.de(filtro), agrup, pontos);
    }

    public VendasPorHora vendasPorHora(FiltroDashboard filtro) {
        List<List<Pedido>> horas = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            horas.add(new ArrayList<>());
        }
        buscar(filtro).stream().filter(VALIDO).forEach(p -> horas.get(p.getDataHoraEntrada().getHour()).add(p));
        List<PontoHora> pontos = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            pontos.add(new PontoHora(h, horas.get(h).size(), somarValor(horas.get(h))));
        }
        return new VendasPorHora(Periodo.de(filtro), pontos);
    }

    // ===================== RF03 — situação dos pedidos =====================

    /** O filtro de status é ignorado aqui, pois o objetivo é justamente a distribuição por status. */
    public SituacaoPedidos situacao(FiltroDashboard filtro) {
        FiltroDashboard semStatus = filtro.semStatus();
        List<Pedido> pedidos = buscar(semStatus);
        List<QuantidadePorStatus> porStatus = Arrays.stream(StatusPedido.values())
                .map(s -> {
                    List<Pedido> doStatus = pedidos.stream().filter(p -> p.getStatus() == s).toList();
                    return new QuantidadePorStatus(s, s.getDescricao(), doStatus.size(),
                            percentual(doStatus.size(), pedidos.size()), somarValor(doStatus));
                })
                .toList();
        return new SituacaoPedidos(
                Periodo.de(semStatus),
                pedidos.size(),
                pedidos.stream().filter(p -> EM_ANDAMENTO.contains(p.getStatus())).count(),
                porStatus,
                minutosMedios(pedidos, Pedido::getDataHoraSaida),
                minutosMedios(pedidos, Pedido::getDataHoraFinalizacao));
    }

    public List<PedidoRecente> pedidosRecentes(FiltroDashboard filtro, Integer limite) {
        Query query = criarQuery(filtro).with(Sort.by(Sort.Direction.DESC, "dataHoraEntrada")).limit(validarLimite(limite));
        return mongoTemplate.find(query, Pedido.class).stream()
                .map(p -> new PedidoRecente(p.getId(), p.getClienteNome(), p.getTipoEntrega(), p.getStatus(),
                        p.getStatus().getDescricao(), p.getValorTotal(), p.getQuantidadeTotalItens(), p.getDataHoraEntrada()))
                .toList();
    }

    // ===================== RF04 — produtos mais vendidos =====================

    public RankingProdutos produtosMaisVendidos(FiltroDashboard filtro, Integer limite, OrdenacaoProdutos ordenacao) {
        OrdenacaoProdutos ordem = ordenacao == null ? OrdenacaoProdutos.QUANTIDADE : ordenacao;
        int max = validarLimite(limite);

        // Agrupa os itens de pedidos não cancelados pelo produto
        Map<String, long[]> quantidades = new LinkedHashMap<>();
        Map<String, BigDecimal> valores = new LinkedHashMap<>();
        Map<String, String> nomes = new LinkedHashMap<>();
        for (Pedido p : buscar(filtro)) {
            if (!VALIDO.test(p)) {
                continue;
            }
            for (ItemPedido item : p.getItens()) {
                String chave = item.getProdutoId() != null ? item.getProdutoId() : item.getProdutoNome();
                nomes.putIfAbsent(chave, item.getProdutoNome());
                quantidades.computeIfAbsent(chave, k -> new long[1])[0] += item.getQuantidade();
                valores.merge(chave, item.getSubtotal(), BigDecimal::add);
            }
        }
        long totalItens = quantidades.values().stream().mapToLong(q -> q[0]).sum();
        BigDecimal totalValor = valores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        Comparator<String> comparador = ordem == OrdenacaoProdutos.FATURAMENTO
                ? Comparator.comparing((String k) -> valores.get(k)).reversed()
                : Comparator.comparingLong((String k) -> quantidades.get(k)[0]).reversed();
        List<String> chaves = quantidades.keySet().stream()
                .sorted(comparador.thenComparing(nomes::get))
                .limit(max)
                .toList();

        List<ProdutoVendido> produtos = new ArrayList<>();
        for (int i = 0; i < chaves.size(); i++) {
            String k = chaves.get(i);
            BigDecimal participacao = ordem == OrdenacaoProdutos.FATURAMENTO
                    ? percentual(valores.get(k), totalValor)
                    : percentual(quantidades.get(k)[0], totalItens);
            produtos.add(new ProdutoVendido(i + 1, k, nomes.get(k), quantidades.get(k)[0],
                    valores.get(k).setScale(2, RoundingMode.HALF_UP), participacao));
        }
        return new RankingProdutos(Periodo.de(filtro), ordem, totalItens, produtos);
    }

    // ===================== RF05 — opções de filtro =====================

    public OpcoesFiltro opcoesFiltro() {
        LocalDate hoje = hoje();
        return new OpcoesFiltro(
                Arrays.stream(StatusPedido.values()).map(s -> new Opcao(s.name(), s.getDescricao())).toList(),
                Arrays.stream(TipoEntrega.values()).map(t -> new Opcao(t.name(), t.getDescricao())).toList(),
                List.of(new Opcao("DIA", "Por dia"), new Opcao("SEMANA", "Por semana"), new Opcao("MES", "Por mês")),
                FiltroDashboard.de(null, null, null, null, hoje));
    }

    // ===================== Consulta ao banco =====================

    private List<Pedido> buscar(FiltroDashboard filtro) {
        return mongoTemplate.find(criarQuery(filtro), Pedido.class);
    }

    private Query criarQuery(FiltroDashboard filtro) {
        Query query = new Query(Criteria.where("dataHoraEntrada")
                .gte(filtro.inicio().atStartOfDay())
                .lt(filtro.fim().plusDays(1).atStartOfDay()));
        if (filtro.tipoEntrega() != null) {
            query.addCriteria(Criteria.where("tipoEntrega").is(filtro.tipoEntrega()));
        }
        if (filtro.status() != null) {
            query.addCriteria(Criteria.where("status").is(filtro.status()));
        }
        return query;
    }

    // ===================== Auxiliares =====================

    static int validarLimite(Integer limite) {
        if (limite == null) {
            return LIMITE_PADRAO;
        }
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new ParametroInvalidoException("O parâmetro limite deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return limite;
    }

    private static BigDecimal somarValor(List<Pedido> pedidos) {
        return pedidos.stream().map(Pedido::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal media(BigDecimal total, long quantidade) {
        return quantidade == 0 ? BigDecimal.ZERO.setScale(2)
                : total.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal percentual(long parte, long total) {
        return percentual(BigDecimal.valueOf(parte), BigDecimal.valueOf(total));
    }

    static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return parte.multiply(CEM).divide(total, 1, RoundingMode.HALF_UP);
    }

    /** Variação percentual em relação à base; nula quando a base é zero (não há como comparar). */
    static BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) {
            return null;
        }
        return atual.subtract(anterior).multiply(CEM).divide(anterior, 1, RoundingMode.HALF_UP);
    }

    private static Long minutosMedios(List<Pedido> pedidos, java.util.function.Function<Pedido, LocalDateTime> fim) {
        OptionalDouble media = pedidos.stream()
                .filter(p -> fim.apply(p) != null && p.getDataHoraEntrada() != null)
                .mapToLong(p -> Duration.between(p.getDataHoraEntrada(), fim.apply(p)).toMinutes())
                .average();
        return media.isPresent() ? Math.round(media.getAsDouble()) : null;
    }

    private static LocalDate inicioDoBalde(LocalDate data, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> data;
            case SEMANA -> data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> data.withDayOfMonth(1);
        };
    }

    private static LocalDate proximoBalde(LocalDate inicio, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> inicio.plusDays(1);
            case SEMANA -> inicio.plusWeeks(1);
            case MES -> inicio.plusMonths(1);
        };
    }

    private static String rotulo(LocalDate inicio, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> inicio.format(DIA_MES);
            case SEMANA -> "Sem. " + inicio.format(DIA_MES);
            case MES -> inicio.format(MES_ANO);
        };
    }
}
