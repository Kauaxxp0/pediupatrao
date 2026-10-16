package com.umc.pediupatrao.entity;

import java.util.Optional;

/**
 * Perfis de acesso do sistema. O nome do enum é gravado no campo "role" do usuário
 * e convertido para a authority "ROLE_<NOME>" pelo Spring Security.
 */
public enum Perfil {

    ADMIN("Administrador"),
    GERENTE("Gerente"),
    ATENDENTE("Atendente");

    private final String descricao;

    Perfil(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Converte o texto gravado no banco; valores desconhecidos (ex.: o antigo "USER") retornam vazio. */
    public static Optional<Perfil> de(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        for (Perfil p : values()) {
            if (p.name().equalsIgnoreCase(valor.trim())) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
