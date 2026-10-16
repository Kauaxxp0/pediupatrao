package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.service.ClienteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/clientes")
public class ClienteWebController {

    private final ClienteService clienteService;

    public ClienteWebController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public String clientes(Model model) {
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("content", "clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novoClienteForm(Model model) {
        model.addAttribute("cliente", new Cliente());
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/salvar")
    public String salvarCliente(@ModelAttribute Cliente cliente, RedirectAttributes redirectAttributes) {
        clienteService.criar(cliente);
        redirectAttributes.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
        return "redirect:/clientes";
    }

    @GetMapping("/editar/{id}")
    public String editarClienteForm(@PathVariable String id, Model model) {
        Cliente cliente = clienteService.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        model.addAttribute("cliente", cliente);
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/editar/{id}")
    public String atualizarCliente(@PathVariable String id, @ModelAttribute Cliente cliente,
            RedirectAttributes redirectAttributes) {
        clienteService.atualizar(id, cliente);
        redirectAttributes.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
        return "redirect:/clientes";
    }

    @PostMapping("/excluir/{id}")
    public String excluirCliente(@PathVariable String id, RedirectAttributes redirectAttributes) {
        clienteService.excluir(id);
        redirectAttributes.addFlashAttribute("sucesso", "Cliente excluído com sucesso!");
        return "redirect:/clientes";
    }
}
