package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ClienteService {

    static final String ENTIDADE = "Cliente";

    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, PedidoRepository pedidoRepository,
            AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll(Sort.by("nome"));
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente criar(Cliente dados) {
        validar(dados);
        Cliente novo = new Cliente();
        copiarCampos(dados, novo);
        Cliente salvo = clienteRepository.save(novo);

        RegistroAlteracoes alteracoes = compararCampos(new Cliente(), salvo);
        auditoriaService.registrar(TipoOperacao.CLIENTE_CRIADO, ENTIDADE, salvo.getId(), alteracoes.lista(), null);
        return salvo;
    }

    /** Atualiza o cliente e audita cada campo alterado (endereço e telefone inclusive). */
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente atualizar(String id, Cliente dados) {
        validar(dados);
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado para atualização."));

        Cliente anterior = new Cliente();
        copiarCampos(existente, anterior);
        copiarCampos(dados, existente);

        RegistroAlteracoes alteracoes = compararCampos(anterior, existente);
        if (alteracoes.isVazio()) {
            return existente; // nada mudou: não gera gravação nem evento de auditoria
        }
        Cliente salvo = clienteRepository.save(existente);
        auditoriaService.registrar(TipoOperacao.CLIENTE_ALTERADO, ENTIDADE, salvo.getId(), alteracoes.lista(), null);
        return salvo;
    }

    /** Exclusão restrita ao GERENTE e bloqueada para clientes com pedidos (preserva o histórico). */
    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        if (!pedidoRepository.findByClienteId(id).isEmpty()) {
            throw new RegraNegocioException("Não é possível excluir um cliente que possui pedidos registrados.");
        }
        clienteRepository.deleteById(id);
        RegistroAlteracoes alteracoes = compararCampos(existente, new Cliente());
        auditoriaService.registrar(TipoOperacao.CLIENTE_EXCLUIDO, ENTIDADE, id, alteracoes.lista(), null);
    }

    private void validar(Cliente c) {
        if (c == null || c.getNome() == null || c.getNome().isBlank()) {
            throw new RegraNegocioException("O nome do cliente é obrigatório.");
        }
        if (c.getTelefone() == null || c.getTelefone().replaceAll("\\D", "").length() < 10) {
            throw new RegraNegocioException("Informe um telefone válido com DDD.");
        }
        if (c.getEstado() != null && !c.getEstado().isBlank() && c.getEstado().trim().length() != 2) {
            throw new RegraNegocioException("O estado deve ter a sigla com 2 letras.");
        }
    }

    /** Copia somente os campos editáveis (evita que o id ou outros campos sejam sobrescritos). */
    private static void copiarCampos(Cliente origem, Cliente destino) {
        destino.setNome(limpar(origem.getNome()));
        destino.setTelefone(limpar(origem.getTelefone()));
        destino.setCep(limpar(origem.getCep()));
        destino.setLogradouro(limpar(origem.getLogradouro()));
        destino.setNumero(limpar(origem.getNumero()));
        destino.setComplemento(limpar(origem.getComplemento()));
        destino.setBairro(limpar(origem.getBairro()));
        destino.setCidade(limpar(origem.getCidade()));
        destino.setEstado(origem.getEstado() == null ? null : limpar(origem.getEstado().toUpperCase()));
        if (origem.getEndereco() != null) {
            destino.setEndereco(limpar(origem.getEndereco()));
        }
    }

    private static RegistroAlteracoes compararCampos(Cliente antes, Cliente depois) {
        return new RegistroAlteracoes()
                .comparar("nome", antes.getNome(), depois.getNome())
                .comparar("telefone", antes.getTelefone(), depois.getTelefone())
                .comparar("cep", antes.getCep(), depois.getCep())
                .comparar("logradouro", antes.getLogradouro(), depois.getLogradouro())
                .comparar("numero", antes.getNumero(), depois.getNumero())
                .comparar("complemento", antes.getComplemento(), depois.getComplemento())
                .comparar("bairro", antes.getBairro(), depois.getBairro())
                .comparar("cidade", antes.getCidade(), depois.getCidade())
                .comparar("estado", antes.getEstado(), depois.getEstado())
                .comparar("endereco", antes.getEndereco(), depois.getEndereco());
    }

    private static String limpar(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
