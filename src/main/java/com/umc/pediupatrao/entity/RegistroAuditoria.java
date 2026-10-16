package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Evento de auditoria. A classe é imutável (sem setters) e o repositório não expõe
 * atualização nem exclusão. Cada registro guarda o hash do anterior (cadeia de hashes),
 * o que permite detectar qualquer alteração ou remoção feita diretamente no banco.
 */
@Document(collection = "auditoria")
public class RegistroAuditoria {

    @Id
    private final String id;

    @Indexed(unique = true)
    private final long sequencia;

    @Indexed
    private final LocalDateTime dataHora;

    private final String usuario;
    private final String perfil;
    private final TipoOperacao operacao;

    @Indexed
    private final String entidade;

    @Indexed
    private final String entidadeId;

    private final List<CampoAlterado> alteracoes;
    private final String justificativa;
    private final String hashAnterior;
    private final String hash;

    @PersistenceCreator
    public RegistroAuditoria(String id, long sequencia, LocalDateTime dataHora, String usuario, String perfil,
            TipoOperacao operacao, String entidade, String entidadeId, List<CampoAlterado> alteracoes,
            String justificativa, String hashAnterior, String hash) {
        this.id = id;
        this.sequencia = sequencia;
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
        this.operacao = operacao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.alteracoes = alteracoes == null ? List.of() : List.copyOf(alteracoes);
        this.justificativa = justificativa;
        this.hashAnterior = hashAnterior;
        this.hash = hash;
    }

    /** Cópia para exibição, com as alterações substituídas (ex.: dados pessoais mascarados). */
    public RegistroAuditoria comAlteracoes(List<CampoAlterado> novasAlteracoes) {
        return new RegistroAuditoria(id, sequencia, dataHora, usuario, perfil, operacao, entidade, entidadeId,
                novasAlteracoes, justificativa, hashAnterior, hash);
    }

    public String getId() {
        return id;
    }

    public long getSequencia() {
        return sequencia;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getPerfil() {
        return perfil;
    }

    public TipoOperacao getOperacao() {
        return operacao;
    }

    public String getEntidade() {
        return entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public List<CampoAlterado> getAlteracoes() {
        return Collections.unmodifiableList(alteracoes);
    }

    public String getJustificativa() {
        return justificativa;
    }

    public String getHashAnterior() {
        return hashAnterior;
    }

    public String getHash() {
        return hash;
    }
}
