package com.umc.pediupatrao.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.DashboardApiController;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.Periodo;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.VendasPorHora;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Acesso e validação de parâmetros dos endpoints do dashboard. */
@WebMvcTest(DashboardApiController.class)
@Import(SecurityConfig.class)
class DashboardApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @BeforeEach
    void setUp() {
        when(dashboardService.hoje()).thenReturn(LocalDate.of(2026, 11, 8));
        when(dashboardService.vendasPorHora(any())).thenReturn(new VendasPorHora(
                new Periodo(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 11, 8), 30), List.of()));
    }

    @Test
    void semLoginRetorna401() throws Exception {
        mvc.perform(get("/api/dashboard/vendas-por-hora")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoAcessaODashboard() throws Exception {
        mvc.perform(get("/api/dashboard/vendas-por-hora")).andExpect(status().isForbidden());
        mvc.perform(get("/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteAcessaODashboard() throws Exception {
        mvc.perform(get("/api/dashboard/vendas-por-hora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo.dias").value(30));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAcessaODashboard() throws Exception {
        mvc.perform(get("/api/dashboard/vendas-por-hora")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void dataFinalAnteriorAInicialRetorna400() throws Exception {
        mvc.perform(get("/api/dashboard/vendas-por-hora").param("inicio", "2026-11-08").param("fim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A data final (fim) não pode ser anterior à data inicial (inicio)."));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void valorInvalidoDeParametroRetorna400ComONomeDoParametro() throws Exception {
        mvc.perform(get("/api/dashboard/vendas-por-hora").param("status", "ENTREGUE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Valor inválido para o parâmetro 'status': ENTREGUE"));
        mvc.perform(get("/api/dashboard/vendas-por-hora").param("inicio", "08/11/2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void dashboardEhSomenteLeitura() throws Exception {
        mvc.perform(delete("/api/dashboard/resumo")).andExpect(status().isForbidden());
    }
}
