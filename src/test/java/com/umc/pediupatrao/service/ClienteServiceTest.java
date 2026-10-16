package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private AuditoriaService auditoriaService;

    private ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(clienteRepository, pedidoRepository, auditoriaService);
    }

    private Cliente cliente(String logradouro, String telefone) {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Maria");
        c.setTelefone(telefone);
        c.setLogradouro(logradouro);
        return c;
    }

    @Test
    void alteracaoDeEnderecoETelefoneEhAuditadaCampoACampo() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("Rua das Flores", "(11) 99999-0000")));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        service.atualizar("c1", cliente("Av. Brasil", "(11) 98888-1111"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CampoAlterado>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(eq(TipoOperacao.CLIENTE_ALTERADO), eq("Cliente"), eq("c1"), captor.capture(), isNull());
        assertThat(captor.getValue()).extracting(CampoAlterado::getCampo).containsExactly("telefone", "logradouro");
        assertThat(captor.getValue().get(1).getValorAnterior()).isEqualTo("Rua das Flores");
        assertThat(captor.getValue().get(1).getValorNovo()).isEqualTo("Av. Brasil");
    }

    @Test
    void semMudancasNaoGeraAuditoria() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("Rua A", "(11) 99999-0000")));

        service.atualizar("c1", cliente("Rua A", "(11) 99999-0000"));

        verify(auditoriaService, never()).registrar(any(), any(), any(), any(), any());
    }

    @Test
    void clienteComPedidosNaoPodeSerExcluido() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("Rua A", "(11) 99999-0000")));
        when(pedidoRepository.findByClienteId("c1")).thenReturn(List.of(Mockito.mock(Pedido.class)));

        assertThatThrownBy(() -> service.excluir("c1")).isInstanceOf(RegraNegocioException.class);
        verify(clienteRepository, never()).deleteById(any());
    }
}
