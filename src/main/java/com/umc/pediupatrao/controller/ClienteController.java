package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.service.ClienteService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public List<Cliente> listarClientes() {
        return clienteService.listarClientes();
    }

    @GetMapping("/{id}")
    public Cliente buscarCliente(@PathVariable String id) {
        return clienteService.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluirCliente(@PathVariable String id) {
        clienteService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<Cliente> salvarCliente(@RequestBody Cliente cliente) {
        Cliente salvo = clienteService.criar(cliente);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Cliente> atualizarCliente(@PathVariable String id, @RequestBody Cliente cliente) {
        return ResponseEntity.ok(clienteService.atualizar(id, cliente));
    }
}
