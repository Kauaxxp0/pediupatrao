package com.umc.pediupatrao.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.PedidoService;
import java.math.BigDecimal;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Mesmo que alguém crie uma rota nova sem regra no SecurityConfig, o serviço continua
 * protegido pelo @PreAuthorize: desconto e cancelamento só funcionam para GERENTE.
 */
@SpringJUnitConfig(SegurancaServicoTest.Config.class)
class SegurancaServicoTest {

    @Configuration
    @EnableMethodSecurity
    @Import(PedidoService.class)
    static class Config {

        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }
    }

    @Autowired
    private PedidoService pedidoService;

    @MockitoBean
    private PedidoRepository pedidoRepository;
    @MockitoBean
    private ClienteRepository clienteRepository;
    @MockitoBean
    private ProdutoRepository produtoRepository;
    @MockitoBean
    private AuditoriaService auditoriaService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminNaoCancelaPeloServico() {
        assertThatThrownBy(() -> pedidoService.cancelar("1", "Justificativa suficiente"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoAplicaDescontoPeloServico() {
        assertThatThrownBy(() -> pedidoService.aplicarDesconto("1", BigDecimal.TEN, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminNaoRegistraPedidoPeloServico() {
        assertThatThrownBy(() -> pedidoService.criarPedido(null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
