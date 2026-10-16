package com.umc.pediupatrao.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Relógio único da aplicação: datas de entrada, saída e auditoria no fuso da pizzaria. */
    @Bean
    public Clock clock(@Value("${app.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        return Clock.system(ZoneId.of(fusoHorario));
    }
}
