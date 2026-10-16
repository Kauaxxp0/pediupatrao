package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Regras de negócio dos pedidos. A autorização é verificada aqui (@PreAuthorize),
 * além das regras de URL do SecurityConfig, para que nenhuma rota contorne o perfil exigido.
 */
@Service
public class PedidoService {

    static final String ENTIDADE = "Pedido";
    private static final int QUANTIDADE_MAXIMA_POR_ITEM = 50;

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
            ProdutoRepository produtoRepository, AuditoriaService auditoriaService, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll(Sort.by(Sort.Direction.DESC, "dataHoraEntrada"));
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Pedido buscarPorId(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado: " + id));
    }

    /** RF02: registra o pedido calculando preços e total a partir do cadastro de produtos. */
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido criarPedido(NovoPedidoRequest request) {
        if (request == null || request.getClienteId() == null || request.getClienteId().isBlank()) {
            throw new RegraNegocioException("Selecione o cliente do pedido.");
        }
        Cliente cliente = clienteRepository.findById(request.getClienteId())
                .orElseThrow(() -> new RegraNegocioException("Cliente informado não existe."));

        List<ItemPedido> itens = montarItens(request.getItens());
        UsuarioAutenticado usuario = UsuarioAutenticado.atual();
        String observacao = request.getObservacao() == null ? null : request.getObservacao().trim();

        Pedido pedido = Pedido.registrar(cliente.getId(), cliente.getNome(), request.getTipoEntrega(),
                observacao, itens, usuario.username(), usuario.perfil(), agora());
        Pedido salvo = pedidoRepository.save(pedido);

        RegistroAlteracoes alteracoes = new RegistroAlteracoes()
                .incluir("cliente", null, salvo.getClienteNome() + " (" + salvo.getClienteId() + ")")
                .incluir("tipoEntrega", null, salvo.getTipoEntrega())
                .incluir("itens", null, salvo.getItens().toString())
                .incluir("subtotal", null, salvo.getSubtotal())
                .incluir("valorTotal", null, salvo.getValorTotal())
                .incluir("status", null, salvo.getStatus())
                .incluir("registradoPor", null, salvo.getRegistradoPor())
                .incluir("dataHoraEntrada", null, salvo.getDataHoraEntrada());
        auditoriaService.registrar(TipoOperacao.PEDIDO_CRIADO, ENTIDADE, salvo.getId(), alteracoes.lista(), null);
        return salvo;
    }

    /** RF03: transições do fluxo normal; a saída registra data/hora e responsável. */
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido atualizarStatus(String id, StatusPedido novoStatus) {
        Pedido pedido = buscarPorId(id);
        StatusPedido anterior = pedido.getStatus();
        UsuarioAutenticado usuario = UsuarioAutenticado.atual();

        pedido.avancarStatus(novoStatus, usuario.username(), usuario.perfil(), agora());
        Pedido salvo = pedidoRepository.save(pedido);

        RegistroAlteracoes alteracoes = new RegistroAlteracoes().incluir("status", anterior, salvo.getStatus());
        if (salvo.getStatus().isSaida()) {
            alteracoes.incluir("responsavelSaida", null, salvo.getResponsavelSaida())
                    .incluir("dataHoraSaida", null, salvo.getDataHoraSaida());
        }
        if (salvo.getStatus() == StatusPedido.FINALIZADO) {
            alteracoes.incluir("dataHoraFinalizacao", null, salvo.getDataHoraFinalizacao());
        }
        auditoriaService.registrar(TipoOperacao.PEDIDO_STATUS_ALTERADO, ENTIDADE, salvo.getId(), alteracoes.lista(), null);
        return salvo;
    }

    /** Mantém compatibilidade com a API antiga, que recebia o status como texto. */
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido atualizarStatus(String id, String novoStatus) {
        return atualizarStatus(id, converterStatus(novoStatus));
    }

    /** RF04: desconto percentual (até 20%), exclusivo do GERENTE, antes da saída. */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual, String motivo) {
        Pedido pedido = buscarPorId(id);
        BigDecimal percentualAnterior = pedido.getPercentualDesconto();
        BigDecimal descontoAnterior = pedido.getValorDesconto();
        BigDecimal totalAnterior = pedido.getValorTotal();
        UsuarioAutenticado usuario = UsuarioAutenticado.atual();

        pedido.aplicarDesconto(percentual, usuario.username(), agora());
        Pedido salvo = pedidoRepository.save(pedido);

        RegistroAlteracoes alteracoes = new RegistroAlteracoes()
                .incluir("percentualDesconto", percentualAnterior, salvo.getPercentualDesconto())
                .incluir("valorDesconto", descontoAnterior, salvo.getValorDesconto())
                .incluir("valorTotal", totalAnterior, salvo.getValorTotal());
        String justificativa = motivo == null || motivo.isBlank() ? null : motivo.trim();
        auditoriaService.registrar(TipoOperacao.PEDIDO_DESCONTO_APLICADO, ENTIDADE, salvo.getId(),
                alteracoes.lista(), justificativa);
        return salvo;
    }

    /** RF04: cancelamento exclusivo do GERENTE, com justificativa obrigatória. */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        Pedido pedido = buscarPorId(id);
        StatusPedido anterior = pedido.getStatus();
        UsuarioAutenticado usuario = UsuarioAutenticado.atual();

        pedido.cancelar(justificativa, usuario.username(), usuario.perfil(), agora());
        Pedido salvo = pedidoRepository.save(pedido);

        RegistroAlteracoes alteracoes = new RegistroAlteracoes()
                .incluir("status", anterior, salvo.getStatus())
                .incluir("canceladoPor", null, salvo.getCancelamento().getUsuario())
                .incluir("dataHoraCancelamento", null, salvo.getCancelamento().getDataHora());
        auditoriaService.registrar(TipoOperacao.PEDIDO_CANCELADO, ENTIDADE, salvo.getId(),
                alteracoes.lista(), salvo.getCancelamento().getJustificativa());
        return salvo;
    }

    private List<ItemPedido> montarItens(List<NovoPedidoRequest.ItemRequest> itensRequest) {
        if (itensRequest == null) {
            throw new RegraNegocioException("O pedido deve possuir ao menos um item.");
        }
        // Agrupa linhas repetidas do mesmo produto, mantendo a ordem de inclusão.
        Map<String, Integer> quantidades = new LinkedHashMap<>();
        for (NovoPedidoRequest.ItemRequest item : itensRequest) {
            if (item == null || item.getProdutoId() == null || item.getProdutoId().isBlank()) {
                continue; // linha vazia do formulário
            }
            int quantidade = item.getQuantidade() == null ? 0 : item.getQuantidade();
            if (quantidade < 1 || quantidade > QUANTIDADE_MAXIMA_POR_ITEM) {
                throw new RegraNegocioException("A quantidade de cada item deve estar entre 1 e "
                        + QUANTIDADE_MAXIMA_POR_ITEM + ".");
            }
            quantidades.merge(item.getProdutoId(), quantidade, Integer::sum);
        }
        if (quantidades.isEmpty()) {
            throw new RegraNegocioException("O pedido deve possuir ao menos um item.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        quantidades.forEach((produtoId, quantidade) -> {
            Produto produto = produtoRepository.findById(produtoId)
                    .orElseThrow(() -> new RegraNegocioException("Produto não encontrado: " + produtoId));
            if (Boolean.FALSE.equals(produto.getAtivo())) {
                throw new RegraNegocioException("O produto \"" + produto.getNome() + "\" está inativo.");
            }
            if (produto.getPreco() == null || produto.getPreco() <= 0) {
                throw new RegraNegocioException("O produto \"" + produto.getNome() + "\" está sem preço válido.");
            }
            if (quantidade > QUANTIDADE_MAXIMA_POR_ITEM) {
                throw new RegraNegocioException("A quantidade de \"" + produto.getNome() + "\" excede o limite de "
                        + QUANTIDADE_MAXIMA_POR_ITEM + ".");
            }
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), BigDecimal.valueOf(produto.getPreco()), quantidade));
        });
        return itens;
    }

    private StatusPedido converterStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new RegraNegocioException("Informe o novo status.");
        }
        try {
            return StatusPedido.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException("Status inválido: " + status);
        }
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock);
    }
}
