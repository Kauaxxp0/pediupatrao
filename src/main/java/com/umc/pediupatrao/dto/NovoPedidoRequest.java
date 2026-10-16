package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.TipoEntrega;
import java.util.ArrayList;
import java.util.List;

/**
 * Dados aceitos para registrar um pedido (formulário e API). Não há campos de preço,
 * total, status ou responsável: esses valores são definidos exclusivamente pelo servidor.
 */
public class NovoPedidoRequest {

    private String clienteId;
    private TipoEntrega tipoEntrega;
    private String observacao;
    private List<ItemRequest> itens = new ArrayList<>();

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public TipoEntrega getTipoEntrega() {
        return tipoEntrega;
    }

    public void setTipoEntrega(TipoEntrega tipoEntrega) {
        this.tipoEntrega = tipoEntrega;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public List<ItemRequest> getItens() {
        return itens;
    }

    public void setItens(List<ItemRequest> itens) {
        this.itens = itens;
    }

    public static class ItemRequest {

        private String produtoId;
        private Integer quantidade;

        public ItemRequest() {
        }

        public ItemRequest(String produtoId, Integer quantidade) {
            this.produtoId = produtoId;
            this.quantidade = quantidade;
        }

        public String getProdutoId() {
            return produtoId;
        }

        public void setProdutoId(String produtoId) {
            this.produtoId = produtoId;
        }

        public Integer getQuantidade() {
            return quantidade;
        }

        public void setQuantidade(Integer quantidade) {
            this.quantidade = quantidade;
        }
    }
}
