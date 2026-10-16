package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.PedidoService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    /** Aceita apenas cliente, tipo de entrega e itens (produto + quantidade); o total é calculado no servidor. */
    @PostMapping
    public ResponseEntity<Pedido> criarPedido(@RequestBody NovoPedidoRequest pedido) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.criarPedido(pedido));
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @GetMapping("/{id}")
    public Pedido buscarPedido(@PathVariable String id) {
        return pedidoService.buscarPorId(id);
    }

    @PutMapping("/{id}/status")
    public Pedido atualizarStatus(@PathVariable String id, @RequestParam String status) {
        return pedidoService.atualizarStatus(id, status);
    }

    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable String id, @RequestBody DescontoRequest request) {
        return pedidoService.aplicarDesconto(id, request.percentual(), request.motivo());
    }

    @PostMapping("/{id}/cancelar")
    public Pedido cancelar(@PathVariable String id, @RequestBody CancelamentoRequest request) {
        return pedidoService.cancelar(id, request.justificativa());
    }

    public record DescontoRequest(BigDecimal percentual, String motivo) {
    }

    public record CancelamentoRequest(String justificativa) {
    }
}
