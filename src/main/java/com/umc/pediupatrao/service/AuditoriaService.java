package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.security.UsuarioAutenticado;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);
    private static final String HASH_INICIAL = "0".repeat(64);
    private static final char SEPARADOR = '\u001F';
    /** Campos com dados pessoais exibidos mascarados para quem não é ADMIN. */
    private static final Set<String> CAMPOS_PESSOAIS = Set.of("telefone");
    private static final int TAMANHO_PAGINA = 30;

    private final AuditoriaRepository repository;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public AuditoriaService(AuditoriaRepository repository, MongoTemplate mongoTemplate, Clock clock) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    /**
     * Grava um evento de auditoria para o usuário autenticado. É sincronizado para manter
     * a sequência e a cadeia de hashes consistentes; o índice único em "sequencia"
     * protege contra duplicidade mesmo com mais de uma instância.
     */
    public synchronized RegistroAuditoria registrar(TipoOperacao operacao, String entidade, String entidadeId,
            List<CampoAlterado> alteracoes, String justificativa) {
        UsuarioAutenticado usuario = UsuarioAutenticado.atual();
        RegistroAuditoria ultimo = repository.findTopByOrderBySequenciaDesc().orElse(null);
        long sequencia = ultimo == null ? 1 : ultimo.getSequencia() + 1;
        String hashAnterior = ultimo == null ? HASH_INICIAL : ultimo.getHash();
        // O MongoDB armazena datas com precisão de milissegundos; truncamos para o hash continuar válido após a leitura.
        LocalDateTime agora = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);

        RegistroAuditoria semHash = new RegistroAuditoria(null, sequencia, agora, usuario.username(), usuario.perfil(),
                operacao, entidade, entidadeId, alteracoes, justificativa, hashAnterior, null);
        RegistroAuditoria registro = new RegistroAuditoria(null, sequencia, agora, usuario.username(), usuario.perfil(),
                operacao, entidade, entidadeId, alteracoes, justificativa, hashAnterior, calcularHash(semHash));

        RegistroAuditoria salvo = repository.insert(registro);
        log.info("Auditoria #{}: {} {} id={} por {} ({})", sequencia, operacao, entidade, entidadeId,
                usuario.username(), usuario.perfil());
        return salvo;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public Page<RegistroAuditoria> consultar(FiltroAuditoria filtro, int pagina) {
        Query query = new Query();
        if (filtro.temTexto(filtro.entidade())) {
            query.addCriteria(Criteria.where("entidade").is(filtro.entidade()));
        }
        if (filtro.temTexto(filtro.entidadeId())) {
            query.addCriteria(Criteria.where("entidadeId").is(filtro.entidadeId().trim()));
        }
        if (filtro.temTexto(filtro.usuario())) {
            query.addCriteria(Criteria.where("usuario").is(filtro.usuario().trim()));
        }
        if (filtro.operacao() != null) {
            query.addCriteria(Criteria.where("operacao").is(filtro.operacao()));
        }
        if (filtro.de() != null || filtro.ate() != null) {
            Criteria periodo = Criteria.where("dataHora");
            if (filtro.de() != null) {
                periodo = periodo.gte(filtro.de().atStartOfDay());
            }
            if (filtro.ate() != null) {
                periodo = periodo.lt(filtro.ate().plusDays(1).atStartOfDay());
            }
            query.addCriteria(periodo);
        }

        long total = mongoTemplate.count(query, RegistroAuditoria.class);
        PageRequest pageable = PageRequest.of(Math.max(pagina, 0), TAMANHO_PAGINA,
                Sort.by(Sort.Direction.DESC, "sequencia"));
        List<RegistroAuditoria> registros = mongoTemplate.find(query.with(pageable), RegistroAuditoria.class);

        boolean admin = UsuarioAutenticado.atual().possui(Perfil.ADMIN);
        List<RegistroAuditoria> exibidos = admin ? registros : registros.stream().map(this::mascarar).toList();
        return new PageImpl<>(exibidos, pageable, total);
    }

    /** Recalcula a cadeia de hashes e aponta o primeiro registro adulterado, se houver. */
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResultadoVerificacao verificarIntegridade() {
        List<RegistroAuditoria> todos = repository.findAllByOrderBySequenciaAsc();
        String hashEsperadoAnterior = HASH_INICIAL;
        long sequenciaEsperada = 1;
        for (RegistroAuditoria r : todos) {
            if (r.getSequencia() != sequenciaEsperada) {
                return ResultadoVerificacao.falha(todos.size(), sequenciaEsperada,
                        "Registro #" + sequenciaEsperada + " ausente (possível exclusão).");
            }
            if (!hashEsperadoAnterior.equals(r.getHashAnterior())) {
                return ResultadoVerificacao.falha(todos.size(), r.getSequencia(),
                        "Encadeamento quebrado no registro #" + r.getSequencia() + ".");
            }
            if (!calcularHash(r).equals(r.getHash())) {
                return ResultadoVerificacao.falha(todos.size(), r.getSequencia(),
                        "Conteúdo do registro #" + r.getSequencia() + " foi alterado.");
            }
            hashEsperadoAnterior = r.getHash();
            sequenciaEsperada++;
        }
        return ResultadoVerificacao.ok(todos.size());
    }

    private RegistroAuditoria mascarar(RegistroAuditoria r) {
        boolean temDadoPessoal = r.getAlteracoes().stream().anyMatch(c -> CAMPOS_PESSOAIS.contains(c.getCampo()));
        if (!temDadoPessoal) {
            return r;
        }
        return r.comAlteracoes(r.getAlteracoes().stream()
                .map(c -> CAMPOS_PESSOAIS.contains(c.getCampo())
                        ? new CampoAlterado(c.getCampo(), mascararTelefone(c.getValorAnterior()), mascararTelefone(c.getValorNovo()))
                        : c)
                .toList());
    }

    static String mascararTelefone(String telefone) {
        if (telefone == null) {
            return null;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() <= 4) {
            return "****";
        }
        return "****-" + digitos.substring(digitos.length() - 4);
    }

    String calcularHash(RegistroAuditoria r) {
        StringBuilder sb = new StringBuilder()
                .append(r.getSequencia()).append(SEPARADOR)
                .append(r.getDataHora()).append(SEPARADOR)
                .append(r.getUsuario()).append(SEPARADOR)
                .append(r.getPerfil()).append(SEPARADOR)
                .append(r.getOperacao()).append(SEPARADOR)
                .append(r.getEntidade()).append(SEPARADOR)
                .append(r.getEntidadeId()).append(SEPARADOR);
        for (CampoAlterado c : r.getAlteracoes()) {
            sb.append(c.getCampo()).append('=').append(c.getValorAnterior())
                    .append("->").append(c.getValorNovo()).append(SEPARADOR);
        }
        sb.append(r.getJustificativa()).append(SEPARADOR).append(r.getHashAnterior());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /** Filtros opcionais da consulta de auditoria. */
    public record FiltroAuditoria(String entidade, String entidadeId, String usuario,
            TipoOperacao operacao, LocalDate de, LocalDate ate) {

        boolean temTexto(String s) {
            return s != null && !s.isBlank();
        }
    }

    public record ResultadoVerificacao(boolean integro, int totalRegistros, Long sequenciaComProblema, String mensagem) {

        static ResultadoVerificacao ok(int total) {
            return new ResultadoVerificacao(true, total, null,
                    "Integridade confirmada: " + total + " registro(s) verificados, nenhuma adulteração encontrada.");
        }

        static ResultadoVerificacao falha(int total, long sequencia, String mensagem) {
            return new ResultadoVerificacao(false, total, sequencia, mensagem);
        }
    }
}
