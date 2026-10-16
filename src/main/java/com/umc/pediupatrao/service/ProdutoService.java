package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;

    public ProdutoService(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    public List<Produto> listarProdutos() {
        return produtoRepository.findAll(Sort.by("tipo", "nome"));
    }

    public List<Produto> listarAtivos() {
        return listarProdutos().stream().filter(p -> !Boolean.FALSE.equals(p.getAtivo())).toList();
    }

    public Optional<Produto> buscarPorId(String id) {
        return produtoRepository.findById(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public void excluir(String id) {
        if (!produtoRepository.existsById(id)) {
            throw new RecursoNaoEncontradoException("Produto não encontrado.");
        }
        produtoRepository.deleteById(id); // pedidos guardam cópia de nome e preço, o histórico é preservado
    }

    // Método para salvar um novo produto ou atualizar um produto existente
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public Produto salvar(Produto produto) {
        if (produto.getNome() == null || produto.getNome().isBlank()) {
            throw new RegraNegocioException("O nome do produto é obrigatório.");
        }
        if (produto.getPreco() == null || produto.getPreco() <= 0) {
            throw new RegraNegocioException("O preço do produto deve ser maior que zero.");
        }
        if (produto.getAtivo() == null) {
            produto.setAtivo(Boolean.FALSE);
        }
        if (produto.getId() == null || produto.getId().isBlank()) {
            produto.setId(null);
            return produtoRepository.save(produto);
        }
        if (!produtoRepository.existsById(produto.getId())) {
            throw new RecursoNaoEncontradoException("Produto não encontrado para atualização.");
        }
        return produtoRepository.save(produto);
    }
}
