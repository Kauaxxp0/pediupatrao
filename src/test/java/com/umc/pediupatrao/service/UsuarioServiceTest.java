package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
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
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository repository;
    @Mock
    private AuditoriaService auditoriaService;

    private UsuarioService service;

    @BeforeEach
    @SuppressWarnings("deprecation")
    void setUp() {
        service = new UsuarioService(repository, NoOpPasswordEncoder.getInstance(), auditoriaService);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private Usuario usuario(String id, String username, String role) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setRole(role);
        u.setPassword("hash");
        return u;
    }

    @Test
    void adminNaoPodeAlterarOProprioPerfil() {
        when(repository.findById("1")).thenReturn(Optional.of(usuario("1", "admin", "ADMIN")));
        when(repository.findByUsername("admin")).thenReturn(Optional.of(usuario("1", "admin", "ADMIN")));

        UsuarioForm form = new UsuarioForm("1", "admin", "ATENDENTE");
        assertThatThrownBy(() -> service.atualizarUsuario("1", form))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("próprio perfil");
        verify(repository, never()).save(any());
    }

    @Test
    void perfilInexistenteEhRecusado() {
        when(repository.findByUsername(anyString())).thenReturn(Optional.empty());
        UsuarioForm form = new UsuarioForm(null, "novo.user", "SUPERADMIN");
        form.setPassword("segredo123");

        assertThatThrownBy(() -> service.criarUsuario(form))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Perfil inválido");
    }

    @Test
    void naoPermiteExcluirOUltimoAdmin() {
        Usuario outroAdmin = usuario("2", "admin2", "ADMIN");
        when(repository.findById("2")).thenReturn(Optional.of(outroAdmin));
        when(repository.findAll()).thenReturn(List.of(outroAdmin));

        assertThatThrownBy(() -> service.deletarUsuario("2"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("último administrador");
    }

    @Test
    void redefinicaoDeSenhaNaoRegistraOValorNaAuditoria() {
        Usuario atendente = usuario("3", "atendente01", "ATENDENTE");
        when(repository.findById("3")).thenReturn(Optional.of(atendente));
        when(repository.findByUsername("atendente01")).thenReturn(Optional.of(atendente));
        when(repository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioForm form = new UsuarioForm("3", "atendente01", "GERENTE");
        form.setPassword("NovaSenhaSecreta");
        service.atualizarUsuario("3", form);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CampoAlterado>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(any(), any(), any(), captor.capture(), any());
        assertThat(captor.getValue()).noneSatisfy(c -> assertThat(c.getValorNovo()).contains("NovaSenhaSecreta"));
        assertThat(captor.getValue()).anySatisfy(c -> assertThat(c.getValorNovo()).isEqualTo("GERENTE"));
    }
}
