package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Item do pedido. Nome e preço são copiados do produto no momento do registro,
 * preservando o histórico mesmo que o produto seja alterado depois.
 */
public class ItemPedido {

    private String produtoId;
    private String produtoNome;
    private BigDecimal precoUnitario;
    private int quantidade;
    private BigDecimal subtotal;

    public ItemPedido() {
    }

    public ItemPedido(String produtoId, String produtoNome, BigDecimal precoUnitario, int quantidade) {
        this.produtoId = produtoId;
        this.produtoNome = produtoNome;
        this.precoUnitario = precoUnitario.setScale(2, RoundingMode.HALF_UP);
        this.quantidade = quantidade;
        this.subtotal = this.precoUnitario.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_UP);
    }

    public String getProdutoId() {
        return produtoId;
    }

    public String getProdutoNome() {
        return produtoNome;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    @Override
    public String toString() {
        return quantidade + "x " + produtoNome + " (R$ " + precoUnitario + ")";
    }
}
