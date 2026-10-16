package com.umc.pediupatrao.entity;

import com.umc.pediupatrao.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Pedido da pizzaria. Os valores (subtotal, desconto e total) são sempre calculados
 * aqui, no servidor, a partir dos itens e do desconto autorizado. Não existem setters
 * públicos para valores, status ou responsáveis: toda mudança passa pelos métodos de negócio.
 */
@Document(collection = "pedidos")
public class Pedido {

    public static final BigDecimal DESCONTO_MAXIMO_PERCENTUAL = new BigDecimal("20");
    private static final BigDecimal CEM = new BigDecimal("100");

    @Id
    private String id;

    /** Controle de concorrência otimista: impede que duas alterações simultâneas se sobrescrevam. */
    @Version
    private Long versao;

    private String clienteId;
    private String clienteNome;
    private TipoEntrega tipoEntrega;
    private String observacao;

    private List<ItemPedido> itens = new ArrayList<>();

    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal percentualDesconto = BigDecimal.ZERO;
    private BigDecimal valorDesconto = BigDecimal.ZERO;
    private BigDecimal valorTotal = BigDecimal.ZERO;
    private String descontoAplicadoPor;
    private LocalDateTime descontoAplicadoEm;

    private StatusPedido status;

    // Entrada
    private String registradoPor;
    private String registradoPorPerfil;
    private LocalDateTime dataHoraEntrada;

    // Saída (SAIU_PARA_ENTREGA ou RETIRADO)
    private String responsavelSaida;
    private LocalDateTime dataHoraSaida;

    // Finalização
    private String finalizadoPor;
    private LocalDateTime dataHoraFinalizacao;

    private Cancelamento cancelamento;

    private List<MudancaStatus> historicoStatus = new ArrayList<>();

    protected Pedido() {
        // usado pelo Spring Data
    }

    /** Cria um pedido no status RECEBIDO, registrando entrada e responsável. */
    public static Pedido registrar(String clienteId, String clienteNome, TipoEntrega tipoEntrega,
            String observacao, List<ItemPedido> itens, String usuario, String perfil, LocalDateTime agora) {
        if (itens == null || itens.isEmpty()) {
            throw new RegraNegocioException("O pedido deve possuir ao menos um item.");
        }
        if (tipoEntrega == null) {
            throw new RegraNegocioException("Informe se o pedido é para entrega ou retirada.");
        }
        Pedido p = new Pedido();
        p.clienteId = clienteId;
        p.clienteNome = clienteNome;
        p.tipoEntrega = tipoEntrega;
        p.observacao = observacao;
        p.itens = new ArrayList<>(itens);
        p.status = StatusPedido.RECEBIDO;
        p.registradoPor = usuario;
        p.registradoPorPerfil = perfil;
        p.dataHoraEntrada = agora;
        p.historicoStatus.add(new MudancaStatus(null, StatusPedido.RECEBIDO, usuario, perfil, agora));
        p.recalcularValores();
        return p;
    }

    /** Avança no fluxo normal. Registra saída e finalização quando aplicável. */
    public void avancarStatus(StatusPedido destino, String usuario, String perfil, LocalDateTime agora) {
        if (status == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode ter o status alterado.");
        }
        if (destino == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Use a operação de cancelamento (exclusiva do gerente, com justificativa).");
        }
        if (!status.podeAvancarPara(destino, tipoEntrega)) {
            throw new RegraNegocioException("Transição inválida: " + status.getDescricao() + " → "
                    + (destino == null ? "?" : destino.getDescricao())
                    + ". Próximo(s) permitido(s): " + descreverProximos() + ".");
        }
        StatusPedido anterior = status;
        status = destino;
        if (destino.isSaida()) {
            responsavelSaida = usuario;
            dataHoraSaida = agora;
        }
        if (destino == StatusPedido.FINALIZADO) {
            finalizadoPor = usuario;
            dataHoraFinalizacao = agora;
        }
        historicoStatus.add(new MudancaStatus(anterior, destino, usuario, perfil, agora));
    }

    /** Aplica (ou substitui) um desconto percentual de até 20% antes da saída do pedido. */
    public void aplicarDesconto(BigDecimal percentual, String usuario, LocalDateTime agora) {
        if (status == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode receber desconto.");
        }
        if (!status.permiteDescontoOuCancelamento()) {
            throw new RegraNegocioException("Desconto só pode ser aplicado antes da saída para entrega ou retirada.");
        }
        if (percentual == null || percentual.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("O percentual de desconto deve ser maior que zero.");
        }
        if (percentual.compareTo(DESCONTO_MAXIMO_PERCENTUAL) > 0) {
            throw new RegraNegocioException("O desconto é limitado a " + DESCONTO_MAXIMO_PERCENTUAL + "% do valor do pedido.");
        }
        if (percentual.scale() > 2) {
            throw new RegraNegocioException("Informe o percentual com no máximo duas casas decimais.");
        }
        percentualDesconto = percentual.setScale(2, RoundingMode.HALF_UP);
        descontoAplicadoPor = usuario;
        descontoAplicadoEm = agora;
        recalcularValores();
    }

    /** Cancela o pedido, preservando todos os dados históricos. */
    public void cancelar(String justificativa, String usuario, String perfil, LocalDateTime agora) {
        if (status == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("O pedido já está cancelado.");
        }
        if (!status.permiteDescontoOuCancelamento()) {
            throw new RegraNegocioException("Não é possível cancelar: o pedido já saiu para entrega/retirada ou foi finalizado.");
        }
        if (justificativa == null || justificativa.trim().length() < 10) {
            throw new RegraNegocioException("A justificativa do cancelamento é obrigatória (mínimo de 10 caracteres).");
        }
        StatusPedido anterior = status;
        cancelamento = new Cancelamento(justificativa.trim(), usuario, agora, anterior);
        status = StatusPedido.CANCELADO;
        historicoStatus.add(new MudancaStatus(anterior, StatusPedido.CANCELADO, usuario, perfil, agora));
    }

    private void recalcularValores() {
        subtotal = itens.stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        valorDesconto = subtotal.multiply(percentualDesconto)
                .divide(CEM, 2, RoundingMode.HALF_UP);
        valorTotal = subtotal.subtract(valorDesconto).setScale(2, RoundingMode.HALF_UP);
    }

    private String descreverProximos() {
        Set<StatusPedido> proximos = getProximosStatus();
        if (proximos.isEmpty()) {
            return "nenhum (pedido encerrado)";
        }
        return String.join(", ", proximos.stream().map(StatusPedido::getDescricao).toList());
    }

    public Set<StatusPedido> getProximosStatus() {
        return status == null ? Set.of() : status.proximos(tipoEntrega);
    }

    public boolean isPermiteDescontoOuCancelamento() {
        return status != null && status.permiteDescontoOuCancelamento();
    }

    public int getQuantidadeTotalItens() {
        return itens.stream().mapToInt(ItemPedido::getQuantidade).sum();
    }

    // ===== Getters (somente leitura) =====

    public String getId() {
        return id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public String getClienteNome() {
        return clienteNome;
    }

    public TipoEntrega getTipoEntrega() {
        return tipoEntrega;
    }

    public String getObservacao() {
        return observacao;
    }

    public List<ItemPedido> getItens() {
        return Collections.unmodifiableList(itens);
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getPercentualDesconto() {
        return percentualDesconto;
    }

    public BigDecimal getValorDesconto() {
        return valorDesconto;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public String getDescontoAplicadoPor() {
        return descontoAplicadoPor;
    }

    public LocalDateTime getDescontoAplicadoEm() {
        return descontoAplicadoEm;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public String getRegistradoPor() {
        return registradoPor;
    }

    public String getRegistradoPorPerfil() {
        return registradoPorPerfil;
    }

    public LocalDateTime getDataHoraEntrada() {
        return dataHoraEntrada;
    }

    public String getResponsavelSaida() {
        return responsavelSaida;
    }

    public LocalDateTime getDataHoraSaida() {
        return dataHoraSaida;
    }

    public String getFinalizadoPor() {
        return finalizadoPor;
    }

    public LocalDateTime getDataHoraFinalizacao() {
        return dataHoraFinalizacao;
    }

    public Cancelamento getCancelamento() {
        return cancelamento;
    }

    public List<MudancaStatus> getHistoricoStatus() {
        return Collections.unmodifiableList(historicoStatus);
    }
}
