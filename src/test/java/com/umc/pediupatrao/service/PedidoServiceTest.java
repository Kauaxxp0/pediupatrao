package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private AuditoriaService auditoriaService;

    private PedidoService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"), ZoneId.of("America/Sao_Paulo"));
        service = new PedidoService(pedidoRepository, clienteRepository, produtoRepository, auditoriaService, clock);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "atendente01", null, List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE"))));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void criarPedidoUsaPrecoDoCadastroEAgrupaItensRepetidos() {
        Cliente cliente = new Cliente();
        cliente.setId("c1");
        cliente.setNome("Maria");
        Produto pizza = new Produto();
        pizza.setId("p1");
        pizza.setNome("Pizza Calabresa");
        pizza.setPreco(49.90);
        pizza.setAtivo(true);
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente));
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(pizza));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        NovoPedidoRequest req = new NovoPedidoRequest();
        req.setClienteId("c1");
        req.setTipoEntrega(TipoEntrega.ENTREGA);
        req.setItens(List.of(new NovoPedidoRequest.ItemRequest("p1", 1), new NovoPedidoRequest.ItemRequest("p1", 2)));

        Pedido pedido = service.criarPedido(req);

        assertThat(pedido.getItens()).hasSize(1);
        assertThat(pedido.getItens().get(0).getQuantidade()).isEqualTo(3);
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("149.70");
        assertThat(pedido.getRegistradoPor()).isEqualTo("atendente01");
        assertThat(pedido.getRegistradoPorPerfil()).isEqualTo("ATENDENTE");
        assertThat(pedido.getDataHoraEntrada().getHour()).isEqualTo(14); // 17:35 UTC = 14:35 em São Paulo
        verify(auditoriaService).registrar(eq(TipoOperacao.PEDIDO_CRIADO), eq("Pedido"), any(), anyList(), isNull());
    }

    @Test
    void descontoGeraAuditoriaComValoresAnterioresENovos() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "gerente01", null, List.of(new SimpleGrantedAuthority("ROLE_GERENTE"))));
        Pedido pedido = Pedido.registrar("c1", "Maria", TipoEntrega.ENTREGA, null,
                List.of(new com.umc.pediupatrao.entity.ItemPedido("p1", "Pizza", new BigDecimal("100.00"), 1)),
                "atendente01", "ATENDENTE", java.time.LocalDateTime.now());
        when(pedidoRepository.findById("x")).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        service.aplicarDesconto("x", new BigDecimal("15"), "Atraso na entrega");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CampoAlterado>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(eq(TipoOperacao.PEDIDO_DESCONTO_APLICADO), eq("Pedido"), any(),
                captor.capture(), eq("Atraso na entrega"));
        assertThat(captor.getValue())
                .anySatisfy(c -> {
                    assertThat(c.getCampo()).isEqualTo("valorTotal");
                    assertThat(c.getValorAnterior()).isEqualTo("100.00");
                    assertThat(c.getValorNovo()).isEqualTo("85.00");
                });
    }
}
