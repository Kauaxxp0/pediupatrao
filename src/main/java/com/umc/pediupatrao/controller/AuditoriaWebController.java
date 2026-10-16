package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.AuditoriaService.FiltroAuditoria;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/auditoria")
public class AuditoriaWebController {

    private final AuditoriaService auditoriaService;

    public AuditoriaWebController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public String consultar(
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) TipoOperacao operacao,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "false") boolean verificar,
            Model model) {
        FiltroAuditoria filtro = new FiltroAuditoria(entidade, entidadeId, usuario, operacao, de, ate);
        model.addAttribute("filtro", filtro);
        model.addAttribute("pagina", auditoriaService.consultar(filtro, pagina));
        model.addAttribute("operacoes", TipoOperacao.values());
        if (verificar) {
            model.addAttribute("verificacao", auditoriaService.verificarIntegridade());
        }
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }
}
