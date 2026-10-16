package com.umc.pediupatrao.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.AuditoriaApiController;
import com.umc.pediupatrao.controller.ClienteController;
import com.umc.pediupatrao.controller.PedidoController;
import com.umc.pediupatrao.controller.UsuarioController;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica as regras de autorização das APIs no backend: tentativas com o perfil errado
 * recebem 403 mesmo sem passar pela interface.
 */
@WebMvcTest(controllers = {PedidoController.class, ClienteController.class, UsuarioController.class,
    AuditoriaApiController.class})
@Import(SecurityConfig.class)
class SegurancaApiTest {

    private static final String DESCONTO = "{\"percentual\": 10}";
    private static final String CANCELAMENTO = "{\"justificativa\": \"Cliente desistiu do pedido\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoService pedidoService;
    @MockitoBean
    private ClienteService clienteService;
    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void anonimoRecebe401NaApi() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoPodeCancelarPedido() throws Exception {
        mvc.perform(post("/api/pedidos/1/cancelar").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoPodeAplicarDescontoNemCancelar() throws Exception {
        mvc.perform(post("/api/pedidos/1/desconto").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/1/cancelar").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerentePodeAplicarDescontoECancelar() throws Exception {
        mvc.perform(post("/api/pedidos/1/desconto").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pedidos/1/cancelar").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void requisicaoSemTokenCsrfEhRecusada() throws Exception {
        mvc.perform(post("/api/pedidos/1/cancelar")
                .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("Token CSRF ausente ou inválido."));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoRegistraPedidoNemAvancaStatus() throws Exception {
        mvc.perform(post("/api/pedidos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/1/status").param("status", "EM_PREPARACAO").with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteNaoAdministraUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"hacker\",\"password\":\"123456\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoConsultaAuditoriaNemSeAutoPromove() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isForbidden());
        mvc.perform(put("/api/usuarios/1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"atendente01\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void auditoriaNaoPossuiEndpointsDeAlteracao() throws Exception {
        when(auditoriaService.consultar(any(), anyInt())).thenReturn(Page.empty());
        mvc.perform(get("/api/auditoria")).andExpect(status().isOk());
        mvc.perform(delete("/api/auditoria/1").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/auditoria/1").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteConsultaMasNaoAlteraClientes() throws Exception {
        mvc.perform(get("/api/clientes")).andExpect(status().isOk());
        mvc.perform(put("/api/clientes/1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteAtualizaClienteMasNaoExclui() throws Exception {
        mvc.perform(put("/api/clientes/1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"telefone\":\"11999990000\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/clientes/1").with(csrf())).andExpect(status().isForbidden());
    }
}
