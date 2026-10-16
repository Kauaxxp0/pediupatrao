package com.umc.pediupatrao.security;

import com.umc.pediupatrao.entity.Perfil;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Dados do usuário autenticado na requisição atual (nome e perfil). */
public record UsuarioAutenticado(String username, String perfil) {

    private static final String PREFIXO_ROLE = "ROLE_";

    public static UsuarioAutenticado atual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return new UsuarioAutenticado("sistema", "SISTEMA");
        }
        String perfil = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(PREFIXO_ROLE))
                .map(a -> a.substring(PREFIXO_ROLE.length()))
                .findFirst()
                .orElse("SEM_PERFIL");
        return new UsuarioAutenticado(auth.getName(), perfil);
    }

    public boolean possui(Perfil p) {
        return p.name().equals(perfil);
    }
}
