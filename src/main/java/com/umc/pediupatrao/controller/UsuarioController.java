package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.dto.UsuarioResponse;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** API de usuários: exclusiva do ADMIN (SecurityConfig + @PreAuthorize no UsuarioService). */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<UsuarioResponse> listarTodos() {
        return usuarioService.listarTodos().stream().map(UsuarioResponse::de).toList();
    }

    @GetMapping("/{id}")
    public UsuarioResponse buscarPorId(@PathVariable String id) {
        return usuarioService.buscarPorId(id).map(UsuarioResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> criarUsuario(@RequestBody UsuarioForm usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.de(usuarioService.criarUsuario(usuario)));
    }

    @PutMapping("/{id}")
    public UsuarioResponse atualizarUsuario(@PathVariable String id, @RequestBody UsuarioForm usuario) {
        return UsuarioResponse.de(usuarioService.atualizarUsuario(id, usuario));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarUsuario(@PathVariable String id) {
        usuarioService.deletarUsuario(id);
        return ResponseEntity.noContent().build();
    }
}
