package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.dashboard.DashboardDtos.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.OpcoesFiltro;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.OrdenacaoProdutos;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.PedidoRecente;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.RankingProdutos;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.Resumo;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.SerieVendas;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.SituacaoPedidos;
import com.umc.pediupatrao.dto.dashboard.DashboardDtos.VendasPorHora;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.service.DashboardService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API do dashboard administrativo (somente leitura, ADMIN e GERENTE).
 * Todos os endpoints aceitam os filtros: inicio, fim (yyyy-MM-dd), tipoEntrega e status.
 * Documentação completa em docs/DASHBOARD.md.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

    private final DashboardService dashboardService;

    public DashboardApiController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    public Resumo resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(required = false) StatusPedido status) {
        return dashboardService.resumo(filtro(inicio, fim, tipoEntrega, status));
    }

    @GetMapping("/vendas")
    public SerieVendas vendas(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) Agrupamento agrupamento) {
        return dashboardService.vendas(filtro(inicio, fim, tipoEntrega, status), agrupamento);
    }

    @GetMapping("/vendas-por-hora")
    public VendasPorHora vendasPorHora(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(required = false) StatusPedido status) {
        return dashboardService.vendasPorHora(filtro(inicio, fim, tipoEntrega, status));
    }

    @GetMapping("/situacao-pedidos")
    public SituacaoPedidos situacaoPedidos(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoEntrega tipoEntrega) {
        return dashboardService.situacao(filtro(inicio, fim, tipoEntrega, null));
    }

    @GetMapping("/pedidos-recentes")
    public List<PedidoRecente> pedidosRecentes(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.pedidosRecentes(filtro(inicio, fim, tipoEntrega, status), limite);
    }

    @GetMapping("/produtos-mais-vendidos")
    public RankingProdutos produtosMaisVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false) OrdenacaoProdutos ordenarPor) {
        return dashboardService.produtosMaisVendidos(filtro(inicio, fim, tipoEntrega, status), limite, ordenarPor);
    }

    @GetMapping("/filtros")
    public OpcoesFiltro filtros() {
        return dashboardService.opcoesFiltro();
    }

    private FiltroDashboard filtro(LocalDate inicio, LocalDate fim, TipoEntrega tipoEntrega, StatusPedido status) {
        return FiltroDashboard.de(inicio, fim, tipoEntrega, status, dashboardService.hoje());
    }
}
