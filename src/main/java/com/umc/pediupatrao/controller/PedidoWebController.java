package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import java.math.BigDecimal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/pedidos")
public class PedidoWebController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;

    public PedidoWebController(PedidoService pedidoService, ClienteService clienteService,
            ProdutoService produtoService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
    }

    @GetMapping
    public String pedidos(Model model) {
        model.addAttribute("pedidos", pedidoService.listarPedidos());
        model.addAttribute("content", "pedidos :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtoService.listarAtivos());
        model.addAttribute("tiposEntrega", TipoEntrega.values());
        model.addAttribute("content", "pedidos/form :: content");
        return "layout";
    }

    @PostMapping("/salvar")
    public String salvar(@ModelAttribute NovoPedidoRequest pedido, RedirectAttributes redirectAttributes) {
        Pedido salvo = pedidoService.criarPedido(pedido);
        redirectAttributes.addFlashAttribute("sucesso", "Pedido registrado com sucesso!");
        return "redirect:/pedidos/" + salvo.getId();
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable String id, Model model) {
        model.addAttribute("pedido", pedidoService.buscarPorId(id));
        model.addAttribute("content", "pedidos/detalhe :: content");
        return "layout";
    }

    @PostMapping("/{id}/status")
    public String atualizarStatus(@PathVariable String id, @RequestParam StatusPedido status,
            RedirectAttributes redirectAttributes) {
        Pedido pedido = pedidoService.atualizarStatus(id, status);
        redirectAttributes.addFlashAttribute("sucesso", "Status alterado para: " + pedido.getStatus().getDescricao());
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/{id}/desconto")
    public String aplicarDesconto(@PathVariable String id, @RequestParam BigDecimal percentual,
            @RequestParam(required = false) String motivo, RedirectAttributes redirectAttributes) {
        Pedido pedido = pedidoService.aplicarDesconto(id, percentual, motivo);
        redirectAttributes.addFlashAttribute("sucesso", "Desconto de " + pedido.getPercentualDesconto()
                + "% aplicado. Novo total: R$ " + pedido.getValorTotal());
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam String justificativa,
            RedirectAttributes redirectAttributes) {
        pedidoService.cancelar(id, justificativa);
        redirectAttributes.addFlashAttribute("sucesso", "Pedido cancelado.");
        return "redirect:/pedidos/" + id;
    }
}
