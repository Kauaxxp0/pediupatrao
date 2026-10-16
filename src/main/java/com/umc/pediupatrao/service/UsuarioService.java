package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.UsuarioAutenticado;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Administração de usuários e perfis: exclusiva do ADMIN (verificado na classe inteira).
 * Senhas nunca são registradas na auditoria nem em logs.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {

    static final String ENTIDADE = "Usuario";
    private static final Pattern USERNAME_VALIDO = Pattern.compile("^[a-zA-Z0-9._-]{3,30}$");
    private static final int SENHA_MINIMA = 6;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll(Sort.by("username"));
    }

    public Usuario criarUsuario(UsuarioForm form) {
        String username = validarUsername(form.getUsername());
        if (usuarioRepository.findByUsername(username).isPresent()) {
            throw new RegraNegocioException("Já existe um usuário com o username \"" + username + "\".");
        }
        Perfil perfil = validarPerfil(form.getRole());
        validarSenha(form.getPassword());

        Usuario novo = new Usuario();
        novo.setUsername(username);
        novo.setRole(perfil.name());
        novo.setPassword(passwordEncoder.encode(form.getPassword()));
        Usuario salvo = usuarioRepository.save(novo);

        RegistroAlteracoes alteracoes = new RegistroAlteracoes()
                .incluir("username", null, salvo.getUsername())
                .incluir("perfil", null, salvo.getRole());
        auditoriaService.registrar(TipoOperacao.USUARIO_CRIADO, ENTIDADE, salvo.getId(), alteracoes.lista(), null);
        return salvo;
    }

    public Usuario atualizarUsuario(String id, UsuarioForm form) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        String username = validarUsername(form.getUsername());
        usuarioRepository.findByUsername(username)
                .filter(outro -> !outro.getId().equals(id))
                .ifPresent(outro -> {
                    throw new RegraNegocioException("Já existe um usuário com o username \"" + username + "\".");
                });
        Perfil novoPerfil = validarPerfil(form.getRole());
        boolean ehOProprioUsuario = existente.getUsername().equals(UsuarioAutenticado.atual().username());

        // Proteção contra alteração indevida de privilégios
        if (ehOProprioUsuario && existente.getPerfil() != novoPerfil) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
        }
        if (ehOProprioUsuario && !existente.getUsername().equals(username)) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio username.");
        }
        if (existente.getPerfil() == Perfil.ADMIN && novoPerfil != Perfil.ADMIN && ehUltimoAdmin()) {
            throw new RegraNegocioException("Não é possível remover o perfil do último administrador.");
        }

        RegistroAlteracoes alteracoes = new RegistroAlteracoes()
                .comparar("username", existente.getUsername(), username)
                .comparar("perfil", existente.getRole(), novoPerfil.name());

        existente.setUsername(username);
        existente.setRole(novoPerfil.name());
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            validarSenha(form.getPassword());
            existente.setPassword(passwordEncoder.encode(form.getPassword()));
            alteracoes.incluir("senha", null, "[redefinida]"); // o valor da senha nunca é registrado
        }
        Usuario salvo = usuarioRepository.save(existente);
        if (!alteracoes.isVazio()) {
            auditoriaService.registrar(TipoOperacao.USUARIO_ALTERADO, ENTIDADE, salvo.getId(), alteracoes.lista(), null);
        }
        return salvo;
    }

    public void deletarUsuario(String id) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        if (existente.getUsername().equals(UsuarioAutenticado.atual().username())) {
            throw new RegraNegocioException("Você não pode excluir o seu próprio usuário.");
        }
        if (existente.getPerfil() == Perfil.ADMIN && ehUltimoAdmin()) {
            throw new RegraNegocioException("Não é possível excluir o último administrador.");
        }
        usuarioRepository.deleteById(id);
        RegistroAlteracoes alteracoes = new RegistroAlteracoes()
                .incluir("username", existente.getUsername(), null)
                .incluir("perfil", existente.getRole(), null);
        auditoriaService.registrar(TipoOperacao.USUARIO_EXCLUIDO, ENTIDADE, id, alteracoes.lista(), null);
    }

    private boolean ehUltimoAdmin() {
        return usuarioRepository.findAll().stream().filter(u -> u.getPerfil() == Perfil.ADMIN).count() <= 1;
    }

    private String validarUsername(String username) {
        if (username == null || !USERNAME_VALIDO.matcher(username.trim()).matches()) {
            throw new RegraNegocioException("Username inválido: use de 3 a 30 caracteres (letras, números, ponto, hífen ou _).");
        }
        return username.trim();
    }

    private Perfil validarPerfil(String role) {
        return Perfil.de(role).orElseThrow(() ->
                new RegraNegocioException("Perfil inválido. Valores aceitos: ADMIN, GERENTE ou ATENDENTE."));
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.length() < SENHA_MINIMA) {
            throw new RegraNegocioException("A senha deve ter pelo menos " + SENHA_MINIMA + " caracteres.");
        }
    }
}
