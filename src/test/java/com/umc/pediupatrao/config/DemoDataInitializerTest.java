package com.umc.pediupatrao.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DemoDataInitializerTest {

    private static Produto produto(String id, String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }

    @Test
    void geraHistoricoCoerenteComAsRegrasDoPedido() {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Ana");
        LocalDateTime agora = LocalDateTime.of(2026, 11, 8, 20, 30);

        List<Pedido> pedidos = DemoDataInitializer.gerar(
                List.of(produto("p1", "Pizza Calabresa", "Pizza", 49.9), produto("p2", "Esfiha", "Esfiha", 6.5),
                        produto("p3", "Refrigerante", "Bebida", 14.0)),
                List.of(c), agora, 45, new Random(2026));

        assertThat(pedidos).hasSizeGreaterThan(200);
        assertThat(pedidos).allSatisfy(p -> {
            assertThat(p.getDataHoraEntrada()).isBeforeOrEqualTo(agora);
            assertThat(p.getValorTotal().signum()).isPositive();
            assertThat(p.getObservacao()).startsWith(DemoDataInitializer.MARCADOR);
        });
        assertThat(pedidos).anyMatch(p -> p.getStatus() == StatusPedido.CANCELADO);
        assertThat(pedidos).anyMatch(p -> p.getStatus() == StatusPedido.FINALIZADO);
        assertThat(pedidos).anyMatch(p -> p.getPercentualDesconto().signum() > 0);
        // Pedidos finalizados passaram pela saída, com data e responsável
        assertThat(pedidos).filteredOn(p -> p.getStatus() == StatusPedido.FINALIZADO)
                .allSatisfy(p -> assertThat(p.getDataHoraSaida()).isNotNull());
    }
}
