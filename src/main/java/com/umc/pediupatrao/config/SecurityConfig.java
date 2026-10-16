package com.umc.pediupatrao.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Autorização por perfil aplicada no backend em duas camadas:
 * 1) regras de URL abaixo (páginas e APIs);
 * 2) @PreAuthorize nos serviços (@EnableMethodSecurity), que valem para qualquer rota.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final RequestMatcher API = new AntPathRequestMatcher("/api/**");

    private static final String ADMIN = "ADMIN";
    private static final String GERENTE = "GERENTE";
    private static final String ATENDENTE = "ATENDENTE";

    /**
     * O Spring Security usa automaticamente o UsuarioDetailsService (único UserDetailsService)
     * e este PasswordEncoder (BCrypt) para autenticar o login por formulário.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF habilitado: formulários Thymeleaf recebem o token automaticamente
                // e as chamadas fetch enviam o cabeçalho X-CSRF-TOKEN (ver layout.html).
                .csrf(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/images/**").permitAll()

                        // Usuários e perfis: somente ADMIN
                        .requestMatchers("/usuarios/**", "/api/usuarios/**").hasRole(ADMIN)

                        // Auditoria: ADMIN e GERENTE, somente leitura (outros métodos caem no denyAll da API)
                        .requestMatchers(HttpMethod.GET, "/auditoria", "/api/auditoria", "/api/auditoria/verificar")
                        .hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers("/auditoria/**").denyAll()

                        // Pedidos: desconto e cancelamento exclusivos do GERENTE (nem o ADMIN pode)
                        .requestMatchers("/pedidos/*/desconto", "/pedidos/*/cancelar",
                                "/api/pedidos/*/desconto", "/api/pedidos/*/cancelar").hasRole(GERENTE)
                        .requestMatchers("/pedidos/novo", "/pedidos/salvar", "/pedidos/*/status").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.POST, "/api/pedidos").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.PUT, "/api/pedidos/*/status").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.GET, "/pedidos", "/pedidos/*", "/api/pedidos", "/api/pedidos/*")
                        .hasAnyRole(ADMIN, GERENTE, ATENDENTE)

                        // Clientes: ATENDENTE cadastra/altera; GERENTE consulta (e exclui sem pedidos)
                        .requestMatchers("/clientes/excluir/*").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/*").hasRole(GERENTE)
                        .requestMatchers("/clientes/novo", "/clientes/salvar", "/clientes/editar/**").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.POST, "/api/clientes").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.PUT, "/api/clientes/*").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.GET, "/clientes", "/api/clientes", "/api/clientes/*").hasAnyRole(GERENTE, ATENDENTE)

                        // Produtos: todos consultam; ADMIN e GERENTE mantêm o cardápio
                        .requestMatchers(HttpMethod.GET, "/produtos", "/api/produtos", "/api/produtos/*").authenticated()
                        .requestMatchers("/produtos/**", "/api/produtos/**").hasAnyRole(ADMIN, GERENTE)

                        // Qualquer outra API não mapeada acima é negada por padrão
                        .requestMatchers("/api/**").denyAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout") // com CSRF ativo, o logout exige POST com token
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        // APIs respondem 401 em vez de redirecionar para a tela de login
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), API)
                        .accessDeniedHandler(accessDeniedHandler())
                );

        return http.build();
    }

    /** Registra a tentativa negada no log e responde 403 (JSON para APIs, página de erro para telas). */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.access.AccessDeniedException ex) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String usuario = auth == null ? "anônimo" : auth.getName();
            String perfis = auth == null ? "-" : auth.getAuthorities().toString();
            boolean csrf = ex instanceof CsrfException;
            log.warn("Acesso negado{}: usuario={} perfis={} {} {}", csrf ? " (CSRF)" : "",
                    usuario, perfis, request.getMethod(), request.getRequestURI());

            String mensagem = csrf
                    ? "Token CSRF ausente ou inválido."
                    : "Acesso negado: seu perfil não tem permissão para esta operação.";
            if (API.matches(request)) {
                escreverJson(response, mensagem);
            } else if (csrf && "/login".equals(request.getServletPath())) {
                // Página de login aberta há muito tempo (sessão expirada): pede para tentar de novo
                response.sendRedirect(request.getContextPath() + "/login?expirado");
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, mensagem);
            }
        };
    }

    private static void escreverJson(HttpServletResponse response, String mensagem) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":403,\"erro\":\"" + mensagem + "\"}");
    }
}
