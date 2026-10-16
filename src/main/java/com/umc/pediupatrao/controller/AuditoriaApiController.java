package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.AuditoriaService.FiltroAuditoria;
import com.umc.pediupatrao.service.AuditoriaService.ResultadoVerificacao;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta da auditoria (somente GET): não existem endpoints de alteração ou exclusão. */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaApiController {

    private final AuditoriaService auditoriaService;

    public AuditoriaApiController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<RegistroAuditoria> consultar(
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) TipoOperacao operacao,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(defaultValue = "0") int pagina) {
        FiltroAuditoria filtro = new FiltroAuditoria(entidade, entidadeId, usuario, operacao, de, ate);
        return auditoriaService.consultar(filtro, pagina).getContent();
    }

    @GetMapping("/verificar")
    public ResultadoVerificacao verificar() {
        return auditoriaService.verificarIntegridade();
    }
}
