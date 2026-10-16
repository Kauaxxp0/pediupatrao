package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private AuditoriaRepository repository;
    @Mock
    private MongoTemplate mongoTemplate;

    private AuditoriaService service;
    private final List<RegistroAuditoria> banco = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new AuditoriaService(repository, mongoTemplate, Clock.systemDefaultZone());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "atendente01", null, List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE"))));
        // Simula a coleção: insert acrescenta e findTop devolve o último
        lenient().when(repository.insert(any(RegistroAuditoria.class))).thenAnswer(inv -> {
            RegistroAuditoria r = inv.getArgument(0);
            banco.add(r);
            return r;
        });
        lenient().when(repository.findTopByOrderBySequenciaDesc())
                .thenAnswer(inv -> banco.isEmpty() ? Optional.empty() : Optional.of(banco.get(banco.size() - 1)));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registraUsuarioPerfilCamposESequencia() {
        RegistroAuditoria r = service.registrar(TipoOperacao.CLIENTE_ALTERADO, "Cliente", "c1",
                List.of(new CampoAlterado("logradouro", "Rua das Flores", "Av. Brasil")), null);

        assertThat(r.getUsuario()).isEqualTo("atendente01");
        assertThat(r.getPerfil()).isEqualTo("ATENDENTE");
        assertThat(r.getSequencia()).isEqualTo(1);
        assertThat(r.getDataHora()).isNotNull();
        assertThat(r.getAlteracoes()).singleElement()
                .satisfies(c -> assertThat(c.getValorNovo()).isEqualTo("Av. Brasil"));
        assertThat(r.getHash()).hasSize(64);
    }

    @Test
    void verificacaoDetectaRegistroAdulteradoNoBanco() {
        service.registrar(TipoOperacao.CLIENTE_CRIADO, "Cliente", "c1", List.of(new CampoAlterado("nome", null, "Ana")), null);
        service.registrar(TipoOperacao.CLIENTE_ALTERADO, "Cliente", "c1", List.of(new CampoAlterado("nome", "Ana", "Bia")), null);
        when(repository.findAllByOrderBySequenciaAsc()).thenAnswer(inv -> List.copyOf(banco));

        assertThat(service.verificarIntegridade().integro()).isTrue();

        // Alguém altera o valor diretamente no MongoDB
        RegistroAuditoria original = banco.get(1);
        banco.set(1, original.comAlteracoes(List.of(new CampoAlterado("nome", "Ana", "Carla"))));

        AuditoriaService.ResultadoVerificacao resultado = service.verificarIntegridade();
        assertThat(resultado.integro()).isFalse();
        assertThat(resultado.sequenciaComProblema()).isEqualTo(2L);
    }

    @Test
    void verificacaoDetectaExclusaoDeRegistro() {
        service.registrar(TipoOperacao.CLIENTE_CRIADO, "Cliente", "c1", List.of(), null);
        service.registrar(TipoOperacao.CLIENTE_ALTERADO, "Cliente", "c1", List.of(), null);
        service.registrar(TipoOperacao.CLIENTE_ALTERADO, "Cliente", "c1", List.of(), null);
        banco.remove(1);
        when(repository.findAllByOrderBySequenciaAsc()).thenAnswer(inv -> List.copyOf(banco));

        assertThat(service.verificarIntegridade().integro()).isFalse();
    }

    @Test
    void mascaraTelefoneMantendoApenasFinal() {
        assertThat(AuditoriaService.mascararTelefone("(11) 98765-4321")).isEqualTo("****-4321");
        assertThat(AuditoriaService.mascararTelefone(null)).isNull();
    }
}
