package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.service.UsuarioService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/usuarios")
public class UsuarioWebController {

    private final UsuarioService usuarioService;

    public UsuarioWebController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @ModelAttribute("perfis")
    public Perfil[] perfis() {
        return Perfil.values();
    }

    @GetMapping
    public String listarUsuarios(Model model) {
        model.addAttribute("usuarios", usuarioService.listarTodos());
        model.addAttribute("content", "usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novoUsuarioForm(Model model) {
        model.addAttribute("usuario", new UsuarioForm());
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @GetMapping("/editar/{id}")
    public String editarUsuarioForm(@PathVariable String id, Model model) {
        UsuarioForm form = usuarioService.buscarPorId(id)
                .map(u -> new UsuarioForm(u.getId(), u.getUsername(), u.getRole()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        model.addAttribute("usuario", form);
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @PostMapping("/salvar")
    public String salvarUsuario(@ModelAttribute UsuarioForm usuario, RedirectAttributes redirectAttributes) {
        usuarioService.criarUsuario(usuario);
        redirectAttributes.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
        return "redirect:/usuarios";
    }

    @PostMapping("/editar/{id}")
    public String atualizarUsuario(@PathVariable String id, @ModelAttribute UsuarioForm usuario,
            RedirectAttributes redirectAttributes) {
        usuarioService.atualizarUsuario(id, usuario);
        redirectAttributes.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
        return "redirect:/usuarios";
    }

    @PostMapping("/deletar/{id}")
    public String deletarUsuario(@PathVariable String id, RedirectAttributes redirectAttributes) {
        usuarioService.deletarUsuario(id);
        redirectAttributes.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        return "redirect:/usuarios";
    }
}
