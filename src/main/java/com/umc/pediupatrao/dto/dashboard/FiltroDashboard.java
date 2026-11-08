package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Filtros aceitos por todos os endpoints do dashboard (RF05).
 * Sem datas, o período padrão são os últimos 30 dias, incluindo hoje.
 */
public record FiltroDashboard(LocalDate inicio, LocalDate fim, TipoEntrega tipoEntrega, StatusPedido status) {

    public static final int DIAS_PADRAO = 30;
    public static final int INTERVALO_MAXIMO_DIAS = 366;

    /** Aplica os valores padrão e valida o período. */
    public static FiltroDashboard de(LocalDate inicio, LocalDate fim, TipoEntrega tipoEntrega,
            StatusPedido status, LocalDate hoje) {
        LocalDate fimEfetivo = fim != null ? fim : hoje;
        LocalDate inicioEfetivo = inicio != null ? inicio : fimEfetivo.minusDays(DIAS_PADRAO - 1L);
        if (fimEfetivo.isBefore(inicioEfetivo)) {
            throw new ParametroInvalidoException("A data final (fim) não pode ser anterior à data inicial (inicio).");
        }
        if (ChronoUnit.DAYS.between(inicioEfetivo, fimEfetivo) + 1 > INTERVALO_MAXIMO_DIAS) {
            throw new ParametroInvalidoException("O período consultado não pode ultrapassar " + INTERVALO_MAXIMO_DIAS + " dias.");
        }
        return new FiltroDashboard(inicioEfetivo, fimEfetivo, tipoEntrega, status);
    }

    public long dias() {
        return ChronoUnit.DAYS.between(inicio, fim) + 1;
    }

    /** Mesmo número de dias imediatamente antes do período atual (base do comparativo). */
    public FiltroDashboard periodoAnterior() {
        LocalDate fimAnterior = inicio.minusDays(1);
        return new FiltroDashboard(fimAnterior.minusDays(dias() - 1), fimAnterior, tipoEntrega, status);
    }

    public FiltroDashboard semStatus() {
        return new FiltroDashboard(inicio, fim, tipoEntrega, null);
    }
}
