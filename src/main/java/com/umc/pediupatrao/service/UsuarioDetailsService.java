package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioDetailsService.class);

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Atribui a role correspondente ao perfil gravado no usuário (ROLE_ADMIN, ROLE_GERENTE
     * ou ROLE_ATENDENTE). Usuários sem perfil válido ficam desabilitados e não conseguem entrar.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return usuarioRepository.findByUsername(username)
                .map(usuario -> {
                    Perfil perfil = Perfil.de(usuario.getRole()).orElse(null);
                    if (perfil == null) {
                        log.warn("Usuário '{}' sem perfil válido; acesso bloqueado.", usuario.getUsername());
                    }
                    return User.builder()
                            .username(usuario.getUsername())
                            .password(usuario.getPassword())
                            .roles(perfil == null ? new String[0] : new String[]{perfil.name()})
                            .disabled(perfil == null)
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
