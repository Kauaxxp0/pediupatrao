package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.service.ProdutoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/produtos")
public class ProdutoWebController {

    private final ProdutoService produtoService;

    public ProdutoWebController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public String produtos(Model model) {
        model.addAttribute("produtos", produtoService.listarProdutos());
        model.addAttribute("content", "produtos/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        Produto produto = new Produto();
        produto.setAtivo(true);
        model.addAttribute("produto", produto);
        model.addAttribute("content", "produtos/produto-form :: content");
        return "layout";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable String id, Model model) {
        model.addAttribute("produto", produtoService.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado.")));
        model.addAttribute("content", "produtos/produto-form :: content");
        return "layout";
    }

    @PostMapping("/salvar")
    public String salvar(@ModelAttribute Produto produto, RedirectAttributes redirectAttributes) {
        produtoService.salvar(produto);
        redirectAttributes.addFlashAttribute("sucesso", "Produto salvo com sucesso!");
        return "redirect:/produtos";
    }

    @PostMapping("/excluir/{id}")
    public String excluir(@PathVariable String id, RedirectAttributes redirectAttributes) {
        produtoService.excluir(id);
        redirectAttributes.addFlashAttribute("sucesso", "Produto excluído com sucesso!");
        return "redirect:/produtos";
    }
}
