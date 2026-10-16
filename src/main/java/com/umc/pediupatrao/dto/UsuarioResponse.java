package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.Usuario;

/** Representação pública do usuário: nunca inclui a senha (nem o hash). */
public record UsuarioResponse(String id, String username, String role) {

    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getUsername(), u.getRole());
    }
}
